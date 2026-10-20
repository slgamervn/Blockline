package com.blockline.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSearchTest {
    @Test
    fun searchesCurrentReadOnlyPageBeforeWrapping() {
        val source = "first match\ncurrent page match\nnext page match"
        assertEquals(source.indexOf("current page"), findWrappedMatch(source, "CURRENT PAGE", source.indexOf("current page")))
    }

    @Test
    fun wrapsToBeginningWhenNoMatchExistsAfterCurrentPosition() {
        val source = "first target\nsecond line\nthird line"
        assertEquals(6, findWrappedMatch(source, "target", source.length))
    }

    @Test
    fun returnsMinusOneWhenQueryIsMissing() {
        assertEquals(-1, findWrappedMatch("some text", "missing", 4))
    }

    @Test
    fun clampsNegativeSearchStartAndMatchesCaseInsensitively() {
        assertEquals(0, findWrappedMatch("Blockline", "block", -10))
    }
}
