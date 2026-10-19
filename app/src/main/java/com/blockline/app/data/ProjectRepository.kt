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

    fun projectDir(id: String) = File(root, id)
    fun list(): List<Project> = try {
        if (!indexFile.exists()) emptyList() else {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Project(o.getString("id"), o.getString("name"), o.getLong("createdAt"), o.getLong("lastOpened"))
            }.filter { projectDir(it.id).isDirectory }.sortedByDescending { it.lastOpened }
        }
    } catch (_: Exception) { emptyList() }

    private fun save(list: List<Project>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt).put("lastOpened", it.lastOpened)) }
        val tmp = File(indexFile.parentFile, "projects.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(indexFile)) { indexFile.writeText(arr.toString()); tmp.delete() }
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
        check(projectDir(id).copyRecursively(projectDir(newId), overwrite = false)) { "Could not duplicate project" }
        val now = System.currentTimeMillis()
        return Project(newId, src.name + " (copy)", now, now).also { save(list() + it) }
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
            target.parentFile?.mkdirs()
            var content = ctx.assets.open("sample-pack/$relative").bufferedReader().use { it.readText() }
            replacements.forEach { (token, value) -> content = content.replace(token, value) }
            target.writeText(content)
        }
        val now = System.currentTimeMillis()
        return Project(id, "Blockline Sample", now, now).also { save(list() + it) }
    }

    fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) return c.getString(i) }
        }
        return uri.lastPathSegment ?: "project"
    }

    fun importPack(uri: Uri, displayName: String): Project {
        val tmp = File(ctx.cacheDir, "import_${UUID.randomUUID()}").apply { check(mkdirs() || isDirectory) }
        var dest: File? = null
        try {
            val input = ctx.contentResolver.openInputStream(uri)
                ?: error("Cannot open the selected file. Choose the pack archive again.")
            input.use { ZipUtil.unzip(it, tmp) }
            expandNested(tmp, 0)
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

    private fun expandNested(dir: File, depth: Int) {
        if (depth >= 3) return
        dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mcpack", "mcaddon") }.toList().forEach { archive ->
            val target = File(archive.parentFile, archive.nameWithoutExtension + "_unpacked")
            archive.inputStream().use { ZipUtil.unzip(it, target) }
            archive.delete()
            expandNested(target, depth + 1)
        }
    }

    private fun findPackRoots(dir: File): List<File> {
        if (File(dir, "manifest.json").isFile || File(dir, "pack_manifest.json").isFile) return listOf(dir)
        return dir.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" }?.flatMap { findPackRoots(it) } ?: emptyList()
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
        return base.walkTopDown().filter { it != base }.sortedBy { it.relativeTo(base).path.lowercase() }.toList()
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
        val target = File(backupDir(id), System.currentTimeMillis().toString())
        check(src.copyRecursively(target, overwrite = false)) { "Backup failed" }
        return target
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
        ZipUtil.zip(chosen.map { it to if (mode == ExportMode.BOTH) "${it.name}/" else "" }, output)
    }

    private fun backupDir(id: String) = File(backupRoot, id)
}
