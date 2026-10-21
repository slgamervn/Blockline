package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.util.UUID

data class OperationLogEntry(val timestamp: Long, val action: String, val detail: String)

class OperationLogRepository(private val root: File) {
    @Synchronized
    fun list(projectId: String): List<OperationLogEntry> = read(logFile(projectId))

    @Synchronized
    fun append(projectId: String, action: String, detail: String) {
        check(root.mkdirs() || root.isDirectory) { "Could not create operation log storage." }
        val target = logFile(projectId)
        val entries = (read(target) + OperationLogEntry(
            timestamp = System.currentTimeMillis(),
            action = action.take(120),
            detail = detail.take(400)
        )).takeLast(MAX_ENTRIES)
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("timestamp", entry.timestamp).put("action", entry.action).put("detail", entry.detail))
        }
        writeAtomically(target, array.toString())
    }

    @Synchronized
    fun clear(projectId: String) {
        val target = logFile(projectId)
        if (target.exists()) check(target.delete()) { "Could not clear operation log." }
    }

    private fun logFile(projectId: String): File {
        require(Regex("^[A-Za-z0-9_-]{1,80}$").matches(projectId)) { "Invalid project identifier." }
        return File(root, "$projectId.json")
    }

    private fun read(file: File): List<OperationLogEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val timestamp = item.optLong("timestamp", 0L)
                val action = item.optString("action", "")
                if (timestamp <= 0L || action.isBlank()) null
                else OperationLogEntry(timestamp, action, item.optString("detail", ""))
            }.takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    private fun writeAtomically(target: File, content: String) {
        val temporary = File(root, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private companion object { const val MAX_ENTRIES = 100 }
}
