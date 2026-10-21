package com.blockline.app.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class BatchReplaceFilePreview(
    val path: String,
    val occurrences: Int,
    val sample: String,
    val fingerprint: String
)

enum class BatchReplaceFailure {
    BACKUP_FAILED,
    PREVIEW_STALE,
    WRITE_ROLLED_BACK,
    WRITE_ROLLBACK_FAILED
}

data class BatchReplaceOutcome(
    val changedFiles: Int = 0,
    val occurrences: Int = 0,
    val changedContents: Map<String, String> = emptyMap(),
    val failure: BatchReplaceFailure? = null,
    val details: String? = null
)

data class BatchReplaceScanResult(
    val files: List<BatchReplaceFilePreview>,
    val truncated: Boolean
)

object BatchReplaceEngine {
    fun countOccurrences(source: String, query: String): Int {
        if (query.isEmpty() || source.isEmpty()) return 0
        var count = 0
        var index = 0
        while (index <= source.length - query.length) {
            val found = source.indexOf(query, index)
            if (found < 0) break
            count++
            index = found + query.length
        }
        return count
    }

    fun replaceLiteral(source: String, query: String, replacement: String): String {
        require(query.isNotEmpty()) { "Search text cannot be empty." }
        return source.replace(query, replacement)
    }

    fun sampleLine(source: String, query: String): String =
        source.lineSequence().firstOrNull { it.contains(query) }?.trim()?.take(180).orEmpty()

    fun fingerprint(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun writeAtomically(target: File, value: String) {
        val parent = target.parentFile ?: error("Target file has no parent directory.")
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the target directory." }
        val temporary = File(parent, "${target.name}.batch-replace-${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(value, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
