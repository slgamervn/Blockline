package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFileWriter {
    fun write(target: File, value: String, suffix: String = ".saving") {
        require(suffix.isNotBlank()) { "A temporary-file suffix is required." }
        val parent = target.parentFile ?: error("Target file has no parent directory.")
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the target directory." }

        val temp = Files.createTempFile(parent.toPath(), "blockline-${target.name}.", suffix).toFile()
        try {
            temp.writeText(value, Charsets.UTF_8)
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temp.delete()
        }
    }
}
