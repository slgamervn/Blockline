package com.blockline.app.data

/**
 * Viewport and selection state saved per open document. Pixel offsets are restored only when
 * the same path is reattached; callers clamp selection offsets against the current text length.
 */
data class EditorViewportState(
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0
) {
    fun clamped(textLength: Int): EditorViewportState {
        val start = selectionStart.coerceIn(0, textLength)
        val end = selectionEnd.coerceIn(0, textLength)
        return copy(
            scrollX = scrollX.coerceAtLeast(0),
            scrollY = scrollY.coerceAtLeast(0),
            selectionStart = minOf(start, end),
            selectionEnd = maxOf(start, end)
        )
    }
}
