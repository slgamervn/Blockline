package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicodeEscapeToolsTest {
    @Test
    fun decodesUnicodeEscapesInJsonStringsAndRawText() {
        val source = "{\"name\":\"\\u0041\"}"
        val decoded = UnicodeEscapeTools.decode(source)
        assertTrue(decoded.contains("\"A\""))
        assertEquals("Text A", UnicodeEscapeTools.decode("Text " + "\\" + "u0041"))
    }

    @Test
    fun encodesAndDecodesJsonWithoutChangingItsMeaning() {
        val source = "{\"name\":\"sơn (ui)\",\"binding\":\"#slot\"}"
        val encoded = UnicodeEscapeTools.encode(source)
        assertTrue(encoded.contains("\\u0073"))
        assertTrue(encoded.contains("(\\u0075\\u0069)"))
        assertNull(ProjectValidator.inspectJsonSyntax(encoded))
        val decoded = UnicodeEscapeTools.decode(encoded)
        assertTrue(decoded.contains("sơn (ui)"))
        assertTrue(decoded.contains("#slot"))
    }

    @Test
    fun safelyAutoFixesLooseKeysSingleQuotesAndTrailingCommas() {
        val fixed = UnicodeEscapeTools.autoFixJson("{name:'Sơn', value:1,}")
        assertNull(ProjectValidator.inspectJsonSyntax(fixed))
        assertTrue(fixed.contains("\"name\""))
        assertTrue(fixed.contains("\"Sơn\""))
    }

    @Test
    fun minifiesWithoutRemovingWhitespaceInsideStrings() {
        val result = UnicodeEscapeTools.minifyJson("{\"text\": \"keep  spaces\", \"n\": 2}")
        assertEquals("{\"text\":\"keep  spaces\",\"n\":2}", result)
    }

    @Test
    fun formatRejectsMalformedJsonWithSpecificLocation() {
        try {
            UnicodeEscapeTools.formatJson("{\"x\": }")
            throw AssertionError("Malformed JSON was formatted as valid")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("line 1"))
        }
    }
}

