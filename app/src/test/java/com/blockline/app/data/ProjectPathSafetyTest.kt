package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectPathSafetyTest {
    @Test
    fun acceptsProjectDirectoryInsideRoot() {
        val root = Files.createTempDirectory("blockline-project-root-").toFile()
        try {
            assertEquals(File(root.canonicalFile, "valid-project").canonicalFile, resolveProjectDirectory(root, "valid-project"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversalAndRootAliases() {
        val root = Files.createTempDirectory("blockline-project-root-").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { resolveProjectDirectory(root, "../outside") }
            assertThrows(IllegalArgumentException::class.java) { resolveProjectDirectory(root, ".") }
            assertThrows(IllegalArgumentException::class.java) { resolveProjectDirectory(root, root.canonicalPath) }
        } finally {
            root.deleteRecursively()
        }
    }
}
