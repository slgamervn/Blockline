package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectMetadataRecoveryTest {
    @Test
    fun recoversProjectNameFromPackManifest() {
        val root = Files.createTempDirectory("blockline-metadata-recovery-").toFile()
        try {
            val project = File(root, "11111111-2222-3333-4444-555555555555")
            val manifest = File(project, "BP/manifest.json")
            manifest.parentFile?.mkdirs()
            manifest.writeText("""{"header":{"name":"Recovered Behavior Pack"}}""")

            val recovered = ProjectMetadataRecovery.recover(root)

            assertEquals(1, recovered.size)
            assertEquals(project.name, recovered.single().id)
            assertEquals("Recovered Behavior Pack", recovered.single().name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun fallsBackToFolderNameWhenNoManifestCanBeRead() {
        val root = Files.createTempDirectory("blockline-metadata-fallback-").toFile()
        try {
            val project = File(root, "12345678-abcd")
            project.mkdirs()
            File(project, "RP").mkdirs()
            File(project, "RP/manifest.json").writeText("{broken")

            val recovered = ProjectMetadataRecovery.recover(root)

            assertEquals(1, recovered.size)
            assertEquals("Recovered 12345678", recovered.single().name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresDirectoriesThatCannotBeUsedAsProjectIds() {
        val root = Files.createTempDirectory("blockline-metadata-unsafe-").toFile()
        try {
            val safe = File(root, "safe-project").apply { mkdirs() }
            File(root, "unsafe\\project").mkdirs()

            val recovered = ProjectMetadataRecovery.recover(root)

            assertEquals(1, recovered.size)
            assertEquals(safe.name, recovered.single().id)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresTemporaryRestoreAndRollbackDirectories() {
        val root = Files.createTempDirectory("blockline-metadata-temp-").toFile()
        try {
            File(root, ".restore_project").mkdirs()
            File(root, ".rollback_project").mkdirs()
            val realProject = File(root, "real-project").apply { mkdirs() }

            val recovered = ProjectMetadataRecovery.recover(root)

            assertEquals(1, recovered.size)
            assertTrue(recovered.single().id == realProject.name)
        } finally {
            root.deleteRecursively()
        }
    }
}
