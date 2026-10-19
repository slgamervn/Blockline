package com.blockline.app.data

import java.io.File

object FileOps {

    fun safeName(n: String): String? {
        val t = n.trim()
        if (t.isEmpty() || t.contains('/') || t.contains('\\') || t == "." || t == "..") return null
        return t
    }

    fun create(parent: File, name: String, dir: Boolean): Boolean {
        val n = safeName(name) ?: return false
        val f = File(parent, n)
        if (f.exists()) return false
        return if (dir) f.mkdirs() else {
            f.parentFile?.mkdirs()
            f.createNewFile()
        }
    }

    fun rename(f: File, name: String): File? {
        val n = safeName(name) ?: return null
        val target = File(f.parentFile, n)
        if (target.exists()) return null
        return if (f.renameTo(target)) target else null
    }

    fun move(f: File, destDir: File): File? {
        val dc = destDir.canonicalPath
        val fc = f.canonicalPath
        if (dc == fc || dc.startsWith(fc + File.separator)) return null
        val target = File(destDir, f.name)
        if (target.exists()) return null
        return if (f.renameTo(target)) target else null
    }

    fun allDirs(root: File): List<File> =
        root.walkTopDown().filter { it.isDirectory && it != root }.sortedBy { it.path }.toList()
}
