package com.blockline.app.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    fun unzip(input: InputStream, destDir: File) {
        check(destDir.mkdirs() || destDir.isDirectory) { "Could not create extraction directory." }
        val destCanon = destDir.canonicalPath
        val seen = HashSet<String>()
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw IllegalArgumentException("Archive contains too many entries (limit: $MAX_ENTRIES).")
                }

                val name = entry.name.replace('\\', '/')
                if (name.isBlank() || name.indexOf('\u0000') >= 0 || name.startsWith("/") ||
                    Regex("^[A-Za-z]:").containsMatchIn(name)
                ) {
                    throw SecurityException("Unsafe archive entry path.")
                }

                val out = File(destDir, name)
                val canon = out.canonicalPath
                if (canon != destCanon && !canon.startsWith(destCanon + File.separator)) {
                    throw SecurityException("Unsafe archive entry path: $name")
                }

                val normalized = canon.trimEnd(File.separatorChar)
                if (!seen.add(normalized)) {
                    throw IllegalArgumentException("Archive contains duplicate entry paths: $name")
                }

                if (entry.isDirectory) {
                    check(out.mkdirs() || out.isDirectory) { "Could not create archive directory: $name" }
                } else {
                    check(out.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                        "Could not create parent directory for archive entry: $name"
                    }
                    var entryBytes = 0L
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zis.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            if (entryBytes > MAX_ENTRY_BYTES) {
                                throw IllegalArgumentException("Archive entry exceeds the 64 MiB extraction limit: $name")
                            }
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw IllegalArgumentException("Archive exceeds the 256 MiB total extraction limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun zipAddon(packs: List<File>, out: OutputStream, temporaryDirectory: File) {
        require(packs.isNotEmpty()) { "At least one pack is required for an add-on archive." }
        check(temporaryDirectory.mkdirs() || temporaryDirectory.isDirectory) {
            "Could not create temporary export storage."
        }
        val temporaryArchives = mutableListOf<File>()
        try {
            ZipOutputStream(BufferedOutputStream(out)).use { addonZip ->
                packs.forEach { pack ->
                    require(pack.isDirectory) { "Pack folder does not exist: ${pack.name}" }
                    val temporary = File.createTempFile("blockline-export-", ".mcpack", temporaryDirectory)
                    temporaryArchives += temporary
                    FileOutputStream(temporary).use { packOutput ->
                        zip(listOf(pack to ""), packOutput)
                    }
                    addonZip.putNextEntry(ZipEntry("${pack.name}.mcpack"))
                    temporary.inputStream().buffered().use { it.copyTo(addonZip) }
                    addonZip.closeEntry()
                }
            }
        } finally {
            temporaryArchives.forEach { it.delete() }
        }
    }

    fun zip(roots: List<Pair<File, String>>, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zos ->
            for ((dir, prefix) in roots) {
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry(prefix + relative))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}
