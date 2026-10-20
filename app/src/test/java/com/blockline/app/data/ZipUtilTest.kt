package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtilTest {
    @Test
    fun extractsRegularArchive() {
        val root = createTempDir(prefix = "blockline-zip-test")
        try {
            val archive = archive("BP/manifest.json" to "{}")
            ZipUtil.unzip(ByteArrayInputStream(archive), root)
            assertEquals("{}", File(root, "BP/manifest.json").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun addonArchiveContainsNestedMcpackFiles() {
        val root = createTempDir(prefix = "blockline-addon-test")
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText("""{"pack":"behavior"}""")
            File(pack, "scripts").mkdirs()
            File(pack, "scripts/main.js").writeText("console.warn('test')")

            val addonBytes = ByteArrayOutputStream()
            ZipUtil.zipAddon(listOf(pack), addonBytes, root)

            val nestedPackBytes = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(addonBytes.toByteArray())).use { addon ->
                val entry = addon.nextEntry
                assertEquals("BP.mcpack", entry.name)
                addon.copyTo(nestedPackBytes)
                addon.closeEntry()
                assertEquals(null, addon.nextEntry)
            }

            val nestedEntries = mutableSetOf<String>()
            ZipInputStream(ByteArrayInputStream(nestedPackBytes.toByteArray())).use { packZip ->
                var entry = packZip.nextEntry
                while (entry != null) {
                    nestedEntries += entry.name
                    entry = packZip.nextEntry
                }
            }
            assertTrue(nestedEntries.contains("manifest.json"))
            assertTrue(nestedEntries.contains("scripts/main.js"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsZipSlipPath() {
        val root = createTempDir(prefix = "blockline-zip-test")
        try {
            assertThrows(SecurityException::class.java) {
                ZipUtil.unzip(ByteArrayInputStream(archive("../outside.txt" to "unsafe")), root)
            }
            assertFalse(File(root.parentFile, "outside.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDuplicateEntries() {
        val root = createTempDir(prefix = "blockline-zip-test")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ZipUtil.unzip(ByteArrayInputStream(archive("same.txt" to "one", "./same.txt" to "two")), root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
