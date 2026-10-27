package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorIndentationPolicyTest {
    @Test
    fun insertsConfiguredIndentAtCursor() {
        val result = EditorIndentationPolicy.insertTab("{}", 1, 1, 2)
        assertEquals("{  }", result.text)
        assertEquals(3, result.cursor)
    }

    @Test
    fun replacesSelectionAndKeepsCursorAfterIndent() {
        val result = EditorIndentationPolicy.insertTab("beforeREMOVEafter", 6, 12, 8)
        assertEquals("before        after", result.text)
        assertEquals(14, result.cursor)
    }

    @Test
    fun clampsInvalidSelectionsAndUsesSafeDefaultWidth() {
        val result = EditorIndentationPolicy.insertTab("abc", -10, 100, 3)
        assertEquals("    ", result.text)
        assertEquals(4, result.cursor)
    }

    @Test
    fun supportsOnlyTheExposedIndentWidths() {
        assertEquals("  ", EditorIndentationPolicy.spaces(2))
        assertEquals("    ", EditorIndentationPolicy.spaces(4))
        assertEquals("        ", EditorIndentationPolicy.spaces(8))
        assertEquals("    ", EditorIndentationPolicy.spaces(0))
    }
}
