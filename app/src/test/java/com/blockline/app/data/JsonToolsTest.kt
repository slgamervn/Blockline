package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
    fun stripTrailingCommasPreservesLengthAndIgnoresStrings() {
        val source = """{"value": 1,  "text": ",} ]", "nested": [1, 2,]}"""
        val stripped = JsonTools.stripTrailingCommas(source)

        assertEquals(source.length, stripped.length)
        assertTrue(stripped.contains("\"text\": \",} ]\""))
        assertTrue(stripped.contains("[1, 2 ]"))
    }

    @Test
    fun syntaxInspectorAcceptsBedrockTrailingCommasAndReportsMalformedJsonLocation() {
        assertNull(ProjectValidator.inspectJsonSyntax("""{"value": 1,}"""))
        val issue = ProjectValidator.inspectJsonSyntax("{\n  \"value\": }")
        assertNotNull(issue)
        assertEquals(2, issue?.line)
        assertTrue((issue?.column ?: 0) > 0)
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
