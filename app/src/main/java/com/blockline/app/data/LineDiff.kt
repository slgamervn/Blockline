package com.blockline.app.data

enum class DiffLineKind { UNCHANGED, ADDED, REMOVED }

data class DiffLine(
    val kind: DiffLineKind,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String
)

data class LineDiffResult(
    val lines: List<DiffLine>,
    val additions: Int,
    val removals: Int,
    val limited: Boolean
)

object LineDiffEngine {
    private const val MAX_TEXT_CHARS = 600_000
    private const val MAX_MATRIX_CELLS = 1_000_000L

    fun compare(before: String, after: String): LineDiffResult {
        if (before.length > MAX_TEXT_CHARS || after.length > MAX_TEXT_CHARS) {
            return LineDiffResult(emptyList(), 0, 0, true)
        }
        val oldLines = before.split('\n')
        val newLines = after.split('\n')
        if ((oldLines.size.toLong() + 1L) * (newLines.size.toLong() + 1L) > MAX_MATRIX_CELLS) {
            return LineDiffResult(emptyList(), 0, 0, true)
        }

        val columns = newLines.size + 1
        val matrix = IntArray((oldLines.size + 1) * columns)
        for (oldIndex in oldLines.lastIndex downTo 0) {
            for (newIndex in newLines.lastIndex downTo 0) {
                val cell = oldIndex * columns + newIndex
                matrix[cell] = if (oldLines[oldIndex] == newLines[newIndex]) {
                    1 + matrix[(oldIndex + 1) * columns + newIndex + 1]
                } else {
                    maxOf(
                        matrix[(oldIndex + 1) * columns + newIndex],
                        matrix[oldIndex * columns + newIndex + 1]
                    )
                }
            }
        }

        val output = ArrayList<DiffLine>()
        var oldIndex = 0
        var newIndex = 0
        var additions = 0
        var removals = 0
        while (oldIndex < oldLines.size || newIndex < newLines.size) {
            if (oldIndex < oldLines.size && newIndex < newLines.size && oldLines[oldIndex] == newLines[newIndex]) {
                output += DiffLine(DiffLineKind.UNCHANGED, oldIndex + 1, newIndex + 1, oldLines[oldIndex])
                oldIndex++
                newIndex++
            } else if (
                oldIndex < oldLines.size &&
                (newIndex >= newLines.size ||
                    matrix[(oldIndex + 1) * columns + newIndex] >= matrix[oldIndex * columns + newIndex + 1])
            ) {
                output += DiffLine(DiffLineKind.REMOVED, oldIndex + 1, null, oldLines[oldIndex])
                removals++
                oldIndex++
            } else {
                output += DiffLine(DiffLineKind.ADDED, null, newIndex + 1, newLines[newIndex])
                additions++
                newIndex++
            }
        }
        return LineDiffResult(output, additions, removals, false)
    }
}
