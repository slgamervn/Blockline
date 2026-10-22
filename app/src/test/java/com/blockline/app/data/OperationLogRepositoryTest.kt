package com.blockline.app.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationLogRepositoryTest {
    @Test
    fun appendsAndKeepsOnlyTheNewestHundredEntries() {
        val root = Files.createTempDirectory("blockline-operation-log").toFile()
        try {
            val repository = OperationLogRepository(root)
            repeat(105) { index -> repository.append("project-1", "Action", "detail-$index") }
            val entries = repository.list("project-1")
            assertEquals(100, entries.size)
            assertEquals("detail-5", entries.first().detail)
            assertEquals("detail-104", entries.last().detail)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun clearRemovesOnlyTheSelectedProjectLog() {
        val root = Files.createTempDirectory("blockline-operation-log").toFile()
        try {
            val repository = OperationLogRepository(root)
            repository.append("project-a", "Rename", "a.json → b.json")
            repository.append("project-b", "Export", "BOTH")
            repository.clear("project-a")
            assertTrue(repository.list("project-a").isEmpty())
            assertEquals(1, repository.list("project-b").size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservesMalformedLogBeforeAppendRepairsTheActiveFile() {
        val root = Files.createTempDirectory("blockline-corrupt-operation-log").toFile()
        try {
            val activeLog = java.io.File(root, "project-a.json")
            activeLog.writeText("{broken", Charsets.UTF_8)
            val repository = OperationLogRepository(root)

            assertTrue(repository.list("project-a").isEmpty())
            assertEquals("{broken", activeLog.readText(Charsets.UTF_8))
            val snapshots = root.listFiles().orEmpty().filter {
                it.isFile && it.name.startsWith("project-a.corrupt.") && it.extension == "json"
            }
            assertEquals(1, snapshots.size)
            assertEquals("{broken", snapshots.single().readText(Charsets.UTF_8))

            repository.append("project-a", "Recovered", "new entry")

            assertEquals(1, repository.list("project-a").size)
            assertEquals("new entry", repository.list("project-a").single().detail)
            assertEquals("{broken", snapshots.single().readText(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservesLogWithInvalidRecordsBeforeFilteringThem() {
        val root = Files.createTempDirectory("blockline-invalid-operation-log-record").toFile()
        try {
            val activeLog = java.io.File(root, "project-b.json")
            val original = """[{"timestamp":0,"action":"","detail":"invalid record"}]"""
            activeLog.writeText(original, Charsets.UTF_8)
            val repository = OperationLogRepository(root)

            assertTrue(repository.list("project-b").isEmpty())
            val snapshot = root.listFiles().orEmpty().single {
                it.isFile && it.name.startsWith("project-b.corrupt.") && it.extension == "json"
            }
            assertEquals(original, snapshot.readText(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsafeProjectIdentifiers() {
        val root = Files.createTempDirectory("blockline-operation-log").toFile()
        try {
            val repository = OperationLogRepository(root)
            assertThrows(IllegalArgumentException::class.java) {
                repository.append("../outside", "Action", "detail")
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
