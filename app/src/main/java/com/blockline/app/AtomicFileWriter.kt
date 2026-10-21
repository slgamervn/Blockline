package com.blockline.app

import java.io.File

internal object AtomicFileWriter {
    fun write(target: File, value: String, suffix: String) {
        val parent = target.parentFile ?: error("Target file has no parent directory.")
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the target directory." }
        val temporary = File(parent, target.name + suffix)
        temporary.writeText(value, Charsets.UTF_8)
        try {
            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }
}
