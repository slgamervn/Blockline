package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchReplaceEngineTest {
    @Test
    fun countsNonOverlappingLiteralMatches() {
        assertEquals(2, BatchReplaceEngine.countOccurrences("aaaa", "aa"))
        assertEquals(3, BatchReplaceEngine.countOccurrences("abc abc abc", "abc"))
        assertEquals(0, BatchReplaceEngine.countOccurrences("abc", "xyz"))
    }

    @Test
    fun replacementTreatsRegexSymbolsAsLiteralText() {
        assertEquals("value = done", BatchReplaceEngine.replaceLiteral("value = a.$", "a.$", "done"))
    }

    @Test
    fun replacementCanRemoveTextAndRejectsEmptyQuery() {
        assertEquals("ab", BatchReplaceEngine.replaceLiteral("aXXb", "XX", ""))
        assertThrows(IllegalArgumentException::class.java) {
            BatchReplaceEngine.replaceLiteral("source", "", "replacement")
        }
    }

    @Test
    fun sampleLineUsesFirstMatchAndLimitsLength() {
        assertEquals("target on this line", BatchReplaceEngine.sampleLine("first\ntarget on this line\nsecond target", "target"))
        assertEquals(180, BatchReplaceEngine.sampleLine("x".repeat(240), "x").length)
    }
    @Test
    fun fingerprintsDetectAnyContentChangeEvenWhenMatchCountsStayEqual() {
        val before = "name: alpha\\nname: beta"
        val after = "name: gamma\\nname: beta"
        assertEquals(2, BatchReplaceEngine.countOccurrences(before, "name"))
        assertEquals(2, BatchReplaceEngine.countOccurrences(after, "name"))
        org.junit.Assert.assertNotEquals(
            BatchReplaceEngine.fingerprint(before),
            BatchReplaceEngine.fingerprint(after)
        )
        assertEquals(
            BatchReplaceEngine.fingerprint(before),
            BatchReplaceEngine.fingerprint(before)
        )
    }

}
