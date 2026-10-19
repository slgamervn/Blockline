package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonToolsTest {
    @Test
    fun stripCommentsPreservesSourceLengthAndLineBreaks() {
        val source = "{\n  /* comment */\n  \"value\": 1 // trailing\n}"
        val stripped = JsonTools.stripComments(source)

        assertEquals(source.length, stripped.length)
        assertEquals(source.count { it == '\n' }, stripped.count { it == '\n' })
        assertEquals(source.count { it == '\r' }, stripped.count { it == '\r' })
    }

    @Test
    fun stripCommentsDoesNotChangeCommentMarkersInsideStrings() {
        val source = """{"url":"https://example.com/a/*b*/","text":"// not a comment"}"""
        assertEquals(source, JsonTools.stripComments(source))
    }

    @Test
    fun stripCommentsPreservesUnterminatedBlockCommentForValidation() {
        val source = "{\n  \"value\": 1 /* unfinished\n"
        val stripped = JsonTools.stripComments(source)

        assertEquals(source.length, stripped.length)
        assertTrue(stripped.endsWith("\n"))
        assertTrue(stripped.substringAfter("1").all { it == ' ' || it == '\n' || it == '\r' })
    }
}
