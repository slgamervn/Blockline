package com.blockline.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID

class ProjectRepository(private val ctx: Context) {
    private val root = File(ctx.filesDir, "projects").apply { mkdirs() }
    private val backupRoot = File(ctx.filesDir, "backups").apply { mkdirs() }
    private val indexFile = File(ctx.filesDir, "projects.json")
    val prefs = ctx.getSharedPreferences("blockline", Context.MODE_PRIVATE)

    fun projectDir(id: String): File = resolveProjectDirectory(root, id)
    fun list(): List<Project> {
        if (!indexFile.isFile) return recoverProjectIndex()

        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Project(o.getString("id"), o.getString("name"), o.getLong("createdAt"), o.getLong("lastOpened"))
            }.filter { projectDir(it.id).isDirectory }.sortedByDescending { it.lastOpened }
        } catch (_: Exception) {
            recoverProjectIndex(preserveDamagedIndex = true)
        }
    }

    private fun recoverProjectIndex(preserveDamagedIndex: Boolean = false): List<Project> {
        if (preserveDamagedIndex && indexFile.isFile) {
            val damagedCopy = File(indexFile.parentFile, "projects.corrupt.${System.currentTimeMillis()}.json")
            runCatching { indexFile.copyTo(damagedCopy, overwrite = false) }
        }
        val recovered = ProjectMetadataRecovery.recover(root)
        runCatching { save(recovered) }
        return recovered
    }

    private fun save(list: List<Project>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt).put("lastOpened", it.lastOpened)) }
        AtomicFileWriter.write(indexFile, arr.toString(), suffix = ".tmp")
    }

    fun touch(id: String) {
        val now = System.currentTimeMillis()
        save(list().map { if (it.id == id) it.copy(lastOpened = now) else it })
    }

    fun rename(id: String, name: String) {
        val n = name.trim()
        require(n.isNotEmpty()) { "Project name cannot be empty" }
        save(list().map { if (it.id == id) it.copy(name = n) else it })
    }

    fun duplicate(id: String): Project {
        val src = list().firstOrNull { it.id == id } ?: error("Project not found")
        val newId = UUID.randomUUID().toString()
        val destination = projectDir(newId)
        try {
            check(projectDir(id).copyRecursively(destination, overwrite = false)) { "Could not duplicate project" }
            val now = System.currentTimeMillis()
            val duplicate = Project(newId, src.name + " (copy)", now, now)
            save(list() + duplicate)
            return duplicate
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }

    fun delete(id: String) {
        projectDir(id).deleteRecursively()
        backupDir(id).deleteRecursively()
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("filefav:$id:") || it == "recentFile:$id" || it == "recentFiles:$id" }.forEach { editor.remove(it) }
        editor.apply()
        save(list().filter { it.id != id })
    }

    fun createSample(): Project {
        val id = UUID.randomUUID().toString()
        val dest = projectDir(id)
        try {
            val replacements = mapOf(
                "__BP_HEADER_UUID__" to UUID.randomUUID().toString(),
                "__BP_DATA_UUID__" to UUID.randomUUID().toString(),
                "__BP_SCRIPT_UUID__" to UUID.randomUUID().toString(),
                "__RP_HEADER_UUID__" to UUID.randomUUID().toString(),
                "__RP_MODULE_UUID__" to UUID.randomUUID().toString()
            )
            val sampleFiles = listOf(
                "BP/manifest.json",
                "BP/scripts/main.js",
                "BP/functions/hello.mcfunction",
                "RP/manifest.json",
                "RP/texts/languages.json",
                "RP/texts/en_US.lang",
                "RP/texts/vi_VN.lang"
            )
            sampleFiles.forEach { relative ->
                val target = File(dest, relative)
                check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) {
                    "Could not create sample project directories."
                }
                var content = ctx.assets.open("sample-pack/$relative").bufferedReader().use { it.readText() }
                replacements.forEach { (token, value) -> content = content.replace(token, value) }
                target.writeText(content)
            }
            check(sampleFiles.all { File(dest, it).isFile }) { "Sample project is incomplete." }
            val now = System.currentTimeMillis()
            val project = Project(id, "Blockline Sample", now, now)
            save(list() + project)
            return project
        } catch (error: Exception) {
            dest.deleteRecursively()
            throw error
        }
    }

    fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) return c.getString(i) }
        }
        return uri.lastPathSegment ?: "project"
    }

    fun importTextDocument(projectId: String, uri: Uri): String {
        val projectRoot = projectDir(projectId)
        require(projectRoot.isDirectory) { "Project not found." }
        val display = displayName(uri).substringAfterLast('/').trim()
        val extension = display.substringAfterLast('.', "").lowercase()
        require(extension in setOf("txt", "html", "htm", "css", "js", "json", "xml", "md", "log", "yml", "yaml", "properties", "mcfunction")) {
            "Unsupported text file type. Choose TXT, HTML, CSS, JS, JSON, XML, Markdown, LOG, YAML or MCFunction."
        }
        require(display.isNotBlank() && display != "." && display != "..") { "Invalid file name." }
        val safeName = display.map { ch -> if (ch.isLetterOrDigit() || ch in "._- ") ch else '_' }.joinToString("").trim()
        val target = resolve(projectId, safeName)
        require(!target.exists()) { "A file with this name already exists in the project." }
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 5 * 1024 * 1024) { "Text file exceeds the 5 MB import limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Could not read the selected file.")
        val text = bytes.toString(Charsets.UTF_8)
        require('\u0000' !in text) { "The selected file is not plain text." }
        check(target.parentFile?.mkdirs() != false) { "Could not create the destination folder." }
        AtomicFileWriter.write(target, text, suffix = ".importing")
        return relative(projectId, target)
    }

    fun importPack(uri: Uri, displayName: String): Project {
        val tmp = File(ctx.cacheDir, "import_${UUID.randomUUID()}").apply { check(mkdirs() || isDirectory) }
        var dest: File? = null
        try {
            val budget = ZipExtractionBudget()
            val input = ctx.contentResolver.openInputStream(uri)
                ?: error("Cannot open the selected file. Choose the pack archive again.")
            input.use { ZipUtil.unzip(it, tmp, budget) }
            expandNested(tmp, 0, budget)
            val packs = findPackRoots(tmp)
            if (packs.isEmpty()) {
                error("No supported pack manifest was found. Select a valid .mcpack, .mcaddon, or ZIP containing manifest.json or pack_manifest.json.")
            }
            val id = UUID.randomUUID().toString()
            dest = projectDir(id)
            check(dest.mkdirs() || dest.isDirectory) { "Could not create the project folder." }
            var bp = 0
            var rp = 0
            for (pack in packs) {
                val name = if (isResourcePack(pack)) {
                    rp++
                    if (rp == 1) "RP" else "RP$rp"
                } else {
                    bp++
                    if (bp == 1) "BP" else "BP$bp"
                }
                val target = File(dest, name)
                check(pack.copyRecursively(target, overwrite = false)) { "Could not copy pack files into the project." }
                check(target.isDirectory && target.listFiles()?.isNotEmpty() == true) { "The selected pack appears to be empty." }
            }
            check(dest.listFiles()?.isNotEmpty() == true) { "No pack files were imported." }
            val now = System.currentTimeMillis()
            val safeName = displayName.substringBeforeLast('.').trim().ifBlank { "Project" }
            val project = Project(id, safeName, now, now)
            save(list() + project)
            return project
        } catch (e: Exception) {
            dest?.deleteRecursively()
            throw IllegalArgumentException(
                when (e) {
                    is SecurityException -> "The archive contains an unsafe file path and was rejected."
                    is java.util.zip.ZipException -> "The selected file is not a valid or supported ZIP-based pack archive."
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "Import failed. Check that the selected file is a valid .mcpack, .mcaddon, or ZIP archive."
                },
                e
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun expandNested(dir: File, depth: Int, budget: ZipExtractionBudget) {
        if (depth >= 3) return
        val archives = dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase(java.util.Locale.ROOT) in setOf("mcpack", "mcaddon") }
            .take(65)
            .toList()
        require(archives.size <= 64) { "The add-on contains too many nested pack archives (limit: 64)." }
        archives.forEach { archive ->
            budget.nestedArchiveCount++
            require(budget.nestedArchiveCount <= 64) {
                "The add-on contains too many nested pack archives (limit: 64)."
            }
            val target = File(archive.parentFile, archive.nameWithoutExtension + "_unpacked")
            archive.inputStream().use { ZipUtil.unzip(it, target, budget) }
            check(archive.delete()) { "Could not remove a temporary nested pack archive." }
            expandNested(target, depth + 1, budget)
        }
    }

    private fun findPackRoots(dir: File): List<File> {
        val roots = mutableListOf<File>()
        val pending = java.util.ArrayDeque<File>()
        pending.addLast(dir)
        var visited = 0
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            visited++
            require(visited <= 10_000) { "The add-on contains too many folders to scan safely." }
            if (File(current, "manifest.json").isFile || File(current, "pack_manifest.json").isFile) {
                roots += current
                continue
            }
            current.listFiles()
                ?.filter { it.isDirectory && it.name != "__MACOSX" }
                ?.forEach(pending::addLast)
        }
        return roots
    }

    private fun isResourcePack(packDir: File): Boolean = try {
        val manifestFile = listOf(File(packDir, "manifest.json"), File(packDir, "pack_manifest.json")).firstOrNull { it.isFile }
            ?: return false
        val manifest = JSONObject(JsonTools.stripComments(manifestFile.readText()))
        val modules = manifest.optJSONArray("modules") ?: JSONArray()
        (0 until modules.length()).any { modules.optJSONObject(it)?.optString("type") == "resources" }
    } catch (_: Exception) { false }

    fun allEntries(id: String): List<File> {
        val base = projectDir(id)
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown()
            .filter { it != base }
            .map { file -> file to file.relativeTo(base).path.lowercase(java.util.Locale.ROOT) }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
    }
    fun allFiles(id: String): List<File> = allEntries(id).filter { it.isFile }
    fun relative(id: String, file: File) = file.relativeTo(projectDir(id)).path.replace(File.separatorChar, '/')
    fun resolve(id: String, relative: String): File {
        val base = projectDir(id).canonicalFile
        val file = File(base, relative).canonicalFile
        require(file.path.startsWith(base.path + File.separator)) { "Invalid file path" }
        return file
    }

    fun isFavorite(id: String, path: String) = prefs.getBoolean("filefav:$id:$path", false)
    fun toggleFavorite(id: String, path: String) { prefs.edit().putBoolean("filefav:$id:$path", !isFavorite(id, path)).apply() }
    fun addRecentFile(id: String, path: String) {
        val updated = (listOf(path) + recentFiles(id).filterNot { it == path }).take(30)
        val encoded = JSONArray().apply { updated.forEach { put(it) } }.toString()
        prefs.edit().putString("recentFile:$id", path).putString("recentFiles:$id", encoded).apply()
    }

    fun recentFiles(id: String): List<String> {
        val raw = prefs.getString("recentFiles:$id", null)
        if (raw.isNullOrBlank()) return listOfNotNull(prefs.getString("recentFile:$id", null))
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }.distinct().take(30)
        }.getOrElse { listOfNotNull(prefs.getString("recentFile:$id", null)) }
    }

    fun remapPathMetadata(id: String, oldPath: String, newPath: String) {
        val prefix = "filefav:$id:"
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(prefix)) {
                val savedPath = key.removePrefix(prefix)
                if (savedPath == oldPath || savedPath.startsWith("$oldPath/")) {
                    editor.remove(key)
                    val replacement = newPath + savedPath.removePrefix(oldPath)
                    if (value == true) editor.putBoolean("$prefix$replacement", true)
                }
            }
        }
        val recentKey = "recentFile:$id"
        val recent = prefs.getString(recentKey, null)
        if (recent == oldPath || recent?.startsWith("$oldPath/") == true) {
            editor.putString(recentKey, newPath + recent!!.removePrefix(oldPath))
        }
        val recentList = recentFiles(id).map { saved ->
            if (saved == oldPath || saved.startsWith("$oldPath/")) newPath + saved.removePrefix(oldPath) else saved
        }.distinct().take(30)
        editor.putString("recentFiles:$id", JSONArray().apply { recentList.forEach { put(it) } }.toString())
        editor.apply()
    }

    fun removePathMetadata(id: String, path: String) {
        val prefix = "filefav:$id:"
        val editor = prefs.edit()
        prefs.all.keys.filter { key ->
            if (!key.startsWith(prefix)) false
            else {
                val savedPath = key.removePrefix(prefix)
                savedPath == path || savedPath.startsWith("$path/")
            }
        }.forEach { key -> editor.remove(key) }
        val recentKey = "recentFile:$id"
        val recent = prefs.getString(recentKey, null)
        val recentList = recentFiles(id).filterNot { it == path || it.startsWith("$path/") }
        if (recent == path || recent?.startsWith("$path/") == true) {
            if (recentList.isEmpty()) editor.remove(recentKey) else editor.putString(recentKey, recentList.first())
        }
        editor.putString("recentFiles:$id", JSONArray().apply { recentList.forEach { put(it) } }.toString())
        editor.apply()
    }
    fun recentFile(id: String): String? = prefs.getString("recentFile:$id", null)

    fun backup(id: String): File {
        val src = projectDir(id)
        require(src.isDirectory) { "Project not found" }
        val directory = backupDir(id)
        check(directory.mkdirs() || directory.isDirectory) { "Could not create backup storage." }
        val target = File(directory, System.currentTimeMillis().toString())
        try {
            check(src.copyRecursively(target, overwrite = false)) { "Backup failed" }
            directory.listFiles()?.filter { it.isDirectory }
                ?.sortedByDescending { it.name.toLongOrNull() ?: 0L }
                ?.drop(5)
                ?.forEach { it.deleteRecursively() }
            return target
        } catch (error: Exception) {
            target.deleteRecursively()
            throw error
        }
    }

    fun restoreLatest(id: String): Boolean {
        val latest = backupDir(id).listFiles()?.filter { it.isDirectory }
            ?.maxByOrNull { it.name.toLongOrNull() ?: 0L } ?: return false
        val live = projectDir(id)
        val staging = File(root, ".restore_${id}_${System.currentTimeMillis()}")
        val rollback = File(root, ".rollback_${id}_${System.currentTimeMillis()}")
        return try {
            check(latest.copyRecursively(staging, overwrite = false)) { "Could not prepare backup restore" }
            val hadLive = live.exists()
            if (hadLive) check(live.renameTo(rollback)) { "Could not preserve current project before restore" }
            if (!staging.renameTo(live)) {
                if (hadLive) rollback.renameTo(live)
                false
            } else {
                rollback.deleteRecursively()
                true
            }
        } catch (_: Exception) {
            if (!live.exists() && rollback.exists()) rollback.renameTo(live)
            false
        } finally {
            staging.deleteRecursively()
            if (live.exists()) rollback.deleteRecursively()
        }
    }

    fun export(id: String, mode: ExportMode, output: OutputStream) {
        val packs = projectDir(id).listFiles()?.filter { it.isDirectory && (File(it, "manifest.json").isFile || File(it, "pack_manifest.json").isFile) }.orEmpty()
        val chosen = when (mode) {
            ExportMode.BP -> packs.filterNot { isResourcePack(it) }.take(1)
            ExportMode.RP -> packs.filter { isResourcePack(it) }.take(1)
            ExportMode.BOTH -> packs
        }
        if (chosen.isEmpty()) throw NothingToExport()
        if (mode == ExportMode.BOTH) {
            ZipUtil.zipAddon(chosen, output, ctx.cacheDir)
        } else {
            ZipUtil.zip(chosen.map { it to "" }, output)
        }
    }

    private fun backupDir(id: String): File = resolveProjectDirectory(backupRoot, id)
}
