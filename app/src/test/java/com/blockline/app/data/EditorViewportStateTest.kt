package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorViewportStateTest {
    @Test
    fun preservesScrollCursorAndSelectionAcrossTabRestoration() {
        val saved = EditorViewportState(
            scrollX = 27,
            scrollY = 4_280,
            selectionStart = 412,
            selectionEnd = 395
        )
        val restored = saved.clamped(textLength = 1_000)

        assertEquals(27, restored.scrollX)
        assertEquals(4_280, restored.scrollY)
        assertEquals(412, restored.selectionStart)
        assertEquals(395, restored.selectionEnd)
    }

    @Test
    fun clampsSelectionAfterDocumentWasShortenedWithoutResettingViewport() {
        val saved = EditorViewportState(
            scrollX = 40,
            scrollY = 7_200,
            selectionStart = 99,
            selectionEnd = 83
        ).clamped(textLength = 60)

        assertEquals(40, saved.scrollX)
        assertEquals(7_200, saved.scrollY)
        assertEquals(60, saved.selectionStart)
        assertEquals(60, saved.selectionEnd)
    }
}
