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

    /** Extracts a zip stream into destDir. Rejects entries that escape destDir (zip-slip). */
    fun unzip(input: InputStream, destDir: File) {
        destDir.mkdirs()
        val destCanon = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                val name = e.name.replace('\\', '/')
                val out = File(destDir, name)
                val canon = out.canonicalPath
                if (canon != destCanon && !canon.startsWith(destCanon + File.separator)) {
                    throw SecurityException("Unsafe zip entry: $name")
                }
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }

    /** Zips each (directory, prefix) pair into one archive. */
    fun zip(roots: List<Pair<File, String>>, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { zos ->
            for ((dir, prefix) in roots) {
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry(prefix + rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}
