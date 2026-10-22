package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
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
        val rawBytes = Files.readAllBytes(file.toPath())
        val array = try {
            JSONArray(String(rawBytes, Charsets.UTF_8))
        } catch (_: Exception) {
            preserveCorruptLog(file, rawBytes)
            return emptyList()
        }
        val entries = mutableListOf<OperationLogEntry>()
        var hasInvalidEntry = false
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                hasInvalidEntry = true
                continue
            }
            val timestamp = item.optLong("timestamp", 0L)
            val action = item.optString("action", "")
            if (timestamp <= 0L || action.isBlank()) {
                hasInvalidEntry = true
                continue
            }
            entries += OperationLogEntry(timestamp, action, item.optString("detail", ""))
        }
        if (hasInvalidEntry) preserveCorruptLog(file, rawBytes)
        return entries.takeLast(MAX_ENTRIES)
    }

    private fun preserveCorruptLog(file: File, rawBytes: ByteArray) {
        val parent = file.parentFile ?: error("Operation log has no parent directory.")
        val digest = MessageDigest.getInstance("SHA-256").digest(rawBytes)
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val snapshot = File(parent, "${file.nameWithoutExtension}.corrupt.$digest.json")
        if (snapshot.exists()) return
        try {
            Files.write(snapshot.toPath(), rawBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (error: FileAlreadyExistsException) {
            if (!snapshot.isFile) throw error
        }
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
