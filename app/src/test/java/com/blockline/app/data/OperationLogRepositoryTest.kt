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
