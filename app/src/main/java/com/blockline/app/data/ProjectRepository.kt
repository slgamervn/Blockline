package com.blockline.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Project(o.getString("id"), o.getString("name"), o.getLong("createdAt"), o.getLong("lastOpened"))
            }.sortedByDescending { it.lastOpened }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<Project>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("createdAt", it.createdAt).put("lastOpened", it.lastOpened)
            )
        }
        indexFile.writeText(arr.toString())
    }

    fun touch(id: String) {
        val now = System.currentTimeMillis()
        save(list().map { if (it.id == id) it.copy(lastOpened = now) else it })
    }

    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        save(list().map { if (it.id == id) it.copy(name = n) else it })
    }

    fun duplicate(id: String): Project {
        val src = list().first { it.id == id }
        val newId = UUID.randomUUID().toString()
        projectDir(id).copyRecursively(projectDir(newId), overwrite = true)
        val now = System.currentTimeMillis()
        val p = Project(newId, src.name + " (copy)", now, now)
        save(list() + p)
        return p
    }

    fun delete(id: String) {
        projectDir(id).deleteRecursively()
        backupDir(id).deleteRecursively()
        prefs.edit().remove("fav_$id").remove("recent_$id").apply()
        save(list().filter { it.id != id })
    }

    fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i)
            }
        }
        return uri.lastPathSegment ?: "project"
    }

    fun importPack(uri: Uri, displayName: String): Project {
        val tmp = File(ctx.cacheDir, "import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open file")
            input.use { ZipUtil.unzip(it, tmp) }
            expandNested(tmp, 0)
            val packs = findPackRoots(tmp)
            if (packs.isEmpty()) throw IllegalStateException("No manifest.json found")
            val id = UUID.randomUUID().toString()
            val dest = projectDir(id)
            var bp = 0
            var rp = 0
            for (p in packs) {
                val name = if (isResourcePack(p)) {
                    if (rp++ == 0) "RP" else "RP$rp"
                } else {
                    if (bp++ == 0) "BP" else "BP$bp"
                }
                p.copyRecursively(File(dest, name), overwrite = true)
            }
            val now = System.currentTimeMillis()
            val base = displayName.substringBeforeLast('.').ifBlank { "Project" }
            val project = Project(id, base, now, now)
            save(list() + project)
            return project
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun expandNested(dir: File, depth: Int) {
        if (depth > 3) return
        val archives = dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("mcpack", "mcaddon") }.toList()
        for (a in archives) {
            val target = File(a.parentFile, a.nameWithoutExtension + "_x")
            a.inputStream().use { ZipUtil.unzip(it, target) }
            a.delete()
            expandNested(target, depth + 1)
        }
    }

    private fun findPackRoots(dir: File): List<File> {
        if (File(dir, "manifest.json").isFile) return listOf(dir)
        return dir.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" }
            ?.flatMap { findPackRoots(it) } ?: emptyList()
    }

    private fun isResourcePack(packDir: File): Boolean = try {
        val text = File(packDir, "manifest.json").readText()
        text.contains(""""resources""") || text.contains(""""resource""")
    } catch (e: Exception) { false }

    private fun backupDir(id: String) = File(backupRoot, id)
}
