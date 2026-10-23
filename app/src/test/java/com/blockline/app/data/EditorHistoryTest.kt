package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorHistoryTest {
    @Test
    fun groupsEditsWithinTheTypingWindow() {
        val history = EditorHistory()
        history.recordEdit("main.js", "", 1_000L)
        history.recordEdit("main.js", "a", 1_500L)

        assertEquals("", history.undo("main.js", "ab"))
        assertNull(history.undo("main.js", ""))
    }

    @Test
    fun createsNewUndoStepAfterTypingPauses() {
        val history = EditorHistory()
        history.recordEdit("main.js", "", 1_000L)
        history.recordEdit("main.js", "a", 1_701L)

        assertEquals("a", history.undo("main.js", "ab"))
        assertEquals("", history.undo("main.js", "a"))
    }

    @Test
    fun newEditClearsRedoHistory() {
        val history = EditorHistory()
        history.recordEdit("main.js", "", 1_000L)
        history.recordEdit("main.js", "a", 1_701L)
        assertEquals("a", history.undo("main.js", "ab"))

        history.recordEdit("main.js", "a", 2_500L)

        assertNull(history.redo("main.js", "ax"))
    }

    @Test
    fun capsHistoryAtEightySnapshots() {
        val history = EditorHistory(maxDepth = 80)
        for (index in 0..80) {
            history.recordEdit("main.js", "state-$index", (index + 1) * 1_000L)
        }

        var current = "current"
        val undone = mutableListOf<String>()
        repeat(80) {
            current = history.undo("main.js", current) ?: error("Expected undo snapshot $it")
            undone += current
        }

        assertEquals(80, undone.size)
        assertEquals("state-80", undone.first())
        assertEquals("state-1", undone.last())
        assertNull(history.undo("main.js", current))
    }

    @Test
    fun boundsStoredHistoryByCharacterBudget() {
        val history = EditorHistory(maxCharsPerStack = 10L)
        history.recordEdit("large.json", "aaaaaa", 1_000L)
        history.recordEdit("large.json", "bbbbbb", 1_701L)
        history.recordEdit("large.json", "cccccc", 2_402L)

        assertEquals("cccccc", history.undo("large.json", "current"))
        assertNull(history.undo("large.json", "cccccc"))
    }

    @Test
    fun skipsSnapshotLargerThanHistoryBudget() {
        val history = EditorHistory(maxCharsPerStack = 5L)
        history.recordEdit("large.json", "too-large", 1_000L)

        assertNull(history.undo("large.json", "current"))
    }

    @Test
    fun remapsHistoryAndCanResetOneFile() {
        val history = EditorHistory()
        history.recordEdit("old/main.js", "before", 1_000L)
        history.recordEdit("old/main.js", "middle", 1_701L)

        history.remap("old/main.js", "new/main.js")

        assertEquals("middle", history.undo("new/main.js", "after"))
        assertEquals("before", history.undo("new/main.js", "middle"))
        assertNull(history.undo("old/main.js", "after"))

        history.recordEdit("new/main.js", "before", 2_500L)
        history.reset("new/main.js")
        assertNull(history.undo("new/main.js", "after"))
    }

    @Test
    fun clearRemovesEveryFileHistory() {
        val history = EditorHistory()
        history.recordEdit("a.json", "", 1_000L)
        history.recordEdit("b.json", "", 1_000L)

        history.clear()

        assertNull(history.undo("a.json", "a"))
        assertNull(history.undo("b.json", "b"))
    }
}
