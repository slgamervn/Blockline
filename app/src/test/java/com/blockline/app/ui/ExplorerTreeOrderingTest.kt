package com.blockline.app.ui

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerTreeOrderingTest {
    @Test
    fun sortsDirectoriesBeforeFilesAndNamesWithoutCaseSensitivity() {
        val root = Files.createTempDirectory("blockline-explorer-tree").toFile()
        try {
            listOf(
                "zeta/child.json",
                "Beta/child.json",
                "alpha.txt",
                "delta/child.json",
                "Alpha.json"
            ).forEach { relative ->
                val file = File(root, relative)
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val entries = root.walkTopDown()
                .filter { it != root }
                .toList()
            val sorted = ExplorerTreeOrdering.index(
                entries,
                pathOf = { file -> file.relativeTo(root).path.replace(File.separatorChar, '/') }
            )

            assertEquals(
                listOf(
                    "Beta",
                    "Beta/child.json",
                    "delta",
                    "delta/child.json",
                    "zeta",
                    "zeta/child.json",
                    "Alpha.json",
                    "alpha.txt"
                ),
                sorted.map { it.path }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recordsEveryParentPathForNestedEntries() {
        val root = Files.createTempDirectory("blockline-explorer-ancestors").toFile()
        try {
            val file = File(root, "BP/entities/mob.json")
            file.parentFile?.mkdirs()
            file.createNewFile()

            val entries = root.walkTopDown()
                .filter { it != root }
                .toList()
            val nestedFile = ExplorerTreeOrdering.index(
                entries,
                pathOf = { file -> file.relativeTo(root).path.replace(File.separatorChar, '/') }
            ).single { it.path == "BP/entities/mob.json" }

            assertEquals(listOf("BP", "BP/entities"), nestedFile.ancestors)
            assertEquals(2, nestedFile.depth)
        } finally {
            root.deleteRecursively()
        }
    }
}
