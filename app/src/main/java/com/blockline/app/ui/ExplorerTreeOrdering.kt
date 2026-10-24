package com.blockline.app.ui

import java.io.File

internal data class IndexedExplorerEntry(
    val file: File,
    val path: String,
    val ancestors: List<String>,
    val depth: Int,
    internal val segments: List<String>,
    internal val segmentPaths: List<String>
)

internal object ExplorerTreeOrdering {
    fun index(
        files: List<File>,
        pathOf: (File) -> String,
        ensureActive: () -> Unit = {}
    ): List<IndexedExplorerEntry> {
        val entries = ArrayList<IndexedExplorerEntry>(files.size)
        files.forEachIndexed { index, file ->
            if (index % 128 == 0) ensureActive()
            val path = pathOf(file)
            val segments = path.split('/')
            val segmentPaths = ArrayList<String>(segments.size)
            var prefix = ""
            segments.forEach { segment ->
                prefix = if (prefix.isEmpty()) segment else "$prefix/$segment"
                segmentPaths += prefix
            }
            val ancestors = segmentPaths.dropLast(1)
            entries += IndexedExplorerEntry(
                file = file,
                path = path,
                ancestors = ancestors,
                depth = ancestors.size,
                segments = segments,
                segmentPaths = segmentPaths
            )
        }

        val directoryByPath = entries.associate { it.path to it.file.isDirectory }
        val nameComparator = Comparator<String> { left, right ->
            val insensitive = left.compareTo(right, ignoreCase = true)
            if (insensitive != 0) insensitive else left.compareTo(right)
        }
        var comparisons = 0
        return entries.sortedWith { left, right ->
            comparisons++
            if (comparisons and 4095 == 0) ensureActive()
            val limit = minOf(left.segments.size, right.segments.size)
            var result = 0
            var segmentIndex = 0
            while (segmentIndex < limit && result == 0) {
                val leftSegment = left.segments[segmentIndex]
                val rightSegment = right.segments[segmentIndex]
                if (leftSegment != rightSegment) {
                    val leftDirectory = directoryByPath[left.segmentPaths[segmentIndex]] == true
                    val rightDirectory = directoryByPath[right.segmentPaths[segmentIndex]] == true
                    result = when {
                        leftDirectory && !rightDirectory -> -1
                        !leftDirectory && rightDirectory -> 1
                        else -> nameComparator.compare(leftSegment, rightSegment)
                    }
                }
                segmentIndex++
            }
            if (result != 0) result else left.segments.size.compareTo(right.segments.size)
        }
    }
}
