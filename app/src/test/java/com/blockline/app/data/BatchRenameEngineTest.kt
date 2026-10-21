package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenameEngineTest {
    @Test
    fun previewReplacesOnlyFileNamesAndPreservesDirectories() {
        val result = BatchRenameEngine.preview(
            filePaths = listOf("BP/entities/custom_entity.json", "BP/items/custom_item.json", "RP/textures/other.png"),
            existingPaths = setOf("BP/entities/custom_entity.json", "BP/items/custom_item.json", "RP/textures/other.png"),
            query = "custom_",
            replacement = "demo_"
        )
        assertEquals(
            listOf("BP/entities/demo_entity.json", "BP/items/demo_item.json"),
            result.candidates.map { it.newPath }
        )
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun detectsExistingAndDuplicateTargets() {
        val result = BatchRenameEngine.preview(
            filePaths = listOf("BP/a.json", "BP/aa.json"),
            existingPaths = setOf("BP/a.json", "BP/aa.json", "BP/b.json"),
            query = "a",
            replacement = "b"
        )
        assertTrue(result.conflicts.isNotEmpty())
    }

    @Test
    fun rejectsEmptyQueriesAndUnsafeNames() {
        assertThrows(IllegalArgumentException::class.java) {
            BatchRenameEngine.preview(listOf("a.json"), setOf("a.json"), "", "b")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BatchRenameEngine.preview(listOf("a.json"), setOf("a.json"), "a", "../b")
        }
    }

    @Test
    fun detectsMultipleFilesCollapsingToTheSameTarget() {
        val result = BatchRenameEngine.preview(
            filePaths = listOf("BP/a.json", "BP/aa.json"),
            existingPaths = setOf("BP/a.json", "BP/aa.json"),
            query = "a",
            replacement = ""
        )
        assertEquals(1, result.conflicts.count { it.duplicateTarget })
        assertTrue(result.conflicts.none { it.targetExists })
    }

    @Test
    fun returnsEmptyPreviewWhenNoNamesMatch() {
        val result = BatchRenameEngine.preview(
            filePaths = listOf("BP/a.json"),
            existingPaths = setOf("BP/a.json"),
            query = "missing",
            replacement = "new"
        )
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }
}
