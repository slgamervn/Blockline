package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicFileWriterTest {
    @Test
    fun atomicallyReplacesExistingUtf8ContentAndCleansTemporaryFile() {
        val directory = Files.createTempDirectory("blockline-writer").toFile()
        try {
            val target = File(directory, "manifest.json")
            target.writeText("old", Charsets.UTF_8)

            AtomicFileWriter.write(target, "{\"name\":\"Đồng Tháp 🌱\"}", ".saving")

            assertEquals("{\"name\":\"Đồng Tháp 🌱\"}", target.readText(Charsets.UTF_8))
            assertEquals(listOf("manifest.json"), directory.list()?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun createsMissingParentDirectories() {
        val directory = Files.createTempDirectory("blockline-writer").toFile()
        try {
            val target = File(directory, "nested/pack/manifest.json")

            AtomicFileWriter.write(target, "content")

            assertTrue(target.isFile)
            assertEquals("content", target.readText(Charsets.UTF_8))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failsWhenParentPathIsAFileWithoutDamagingThatFile() {
        val directory = Files.createTempDirectory("blockline-writer").toFile()
        try {
            val blocker = File(directory, "not-a-directory")
            blocker.writeText("preserve me", Charsets.UTF_8)

            assertThrows(IllegalStateException::class.java) {
                AtomicFileWriter.write(File(blocker, "manifest.json"), "new content")
            }

            assertEquals("preserve me", blocker.readText(Charsets.UTF_8))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsBlankTemporaryFileSuffix() {
        val directory = Files.createTempDirectory("blockline-writer").toFile()
        try {
            val target = File(directory, "manifest.json")
            target.writeText("original", Charsets.UTF_8)

            assertThrows(IllegalArgumentException::class.java) {
                AtomicFileWriter.write(target, "new content", " ")
            }

            assertEquals("original", target.readText(Charsets.UTF_8))
            assertFalse(File(directory, "manifest.json. ").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
