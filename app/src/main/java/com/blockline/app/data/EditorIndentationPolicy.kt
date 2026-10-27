package com.blockline.app.data

data class TabInsertionResult(
    val text: String,
    val cursor: Int
)

/** Small, deterministic helper shared by the editor and JVM tests. */
object EditorIndentationPolicy {
    private val supportedWidths = setOf(2, 4, 8)

    fun spaces(width: Int): String = " ".repeat(width.takeIf { it in supportedWidths } ?: 4)

    fun insertTab(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        width: Int
    ): TabInsertionResult {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, source.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, source.length)
        val indentation = spaces(width)
        return TabInsertionResult(
            text = source.replaceRange(start, end, indentation),
            cursor = start + indentation.length
        )
    }
}
