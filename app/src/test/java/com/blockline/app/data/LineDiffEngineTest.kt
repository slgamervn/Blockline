package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffEngineTest {
    @Test
    fun identifiesAddedAndRemovedLinesInOrder() {
        val result = LineDiffEngine.compare("alpha\nold\nomega", "alpha\nnew\nomega")
        assertEquals(1, result.additions)
        assertEquals(1, result.removals)
        assertEquals(
            listOf(DiffLineKind.UNCHANGED, DiffLineKind.REMOVED, DiffLineKind.ADDED, DiffLineKind.UNCHANGED),
            result.lines.map { it.kind }
        )
        assertEquals("new", result.lines[2].text)
    }

    @Test
    fun handlesEmptyAndIdenticalFiles() {
        assertTrue(LineDiffEngine.compare("", "one").lines.any { it.kind == DiffLineKind.ADDED })
        val result = LineDiffEngine.compare("same\ntext", "same\ntext")
        assertEquals(0, result.additions)
        assertEquals(0, result.removals)
        assertTrue(result.lines.all { it.kind == DiffLineKind.UNCHANGED })
    }

    @Test
    fun refusesUnboundedQuadraticComparisons() {
        val source = (1..1100).joinToString("\n") { "line" + it }
        assertTrue(LineDiffEngine.compare(source, source).limited)
    }

    @Test
    fun unchangedLinesKeepBothLineNumbers() {
        val line = LineDiffEngine.compare("a\nb", "x\na\nb").lines.first()
        assertEquals(DiffLineKind.ADDED, line.kind)
        assertEquals(1, line.newLineNumber)
        assertTrue(line.oldLineNumber == null)
    }
}
