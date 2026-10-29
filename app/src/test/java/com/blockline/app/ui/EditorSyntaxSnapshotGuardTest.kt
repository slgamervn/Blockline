package com.blockline.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSyntaxSnapshotGuardTest {
    @Test
    fun acceptsOnlyTheExactSnapshotForTheSameOpenDocument() {
        val source = String("{\"blocks\":[1,2,3]}".toCharArray())
        assertTrue(isCurrentSyntaxSnapshot(source, source, "RP/blocks.json", "RP/blocks.json"))
    }

    @Test
    fun rejectsLateResultAfterSameTextWasRecreatedAsANewRevision() {
        val previous = String("{\"blocks\":[1,2,3]}".toCharArray())
        val current = String(previous.toCharArray())
        assertFalse(previous === current)
        assertFalse(isCurrentSyntaxSnapshot(previous, current, "RP/blocks.json", "RP/blocks.json"))
    }

    @Test
    fun rejectsLateResultAfterSwitchingTabs() {
        val source = String("{\"blocks\":[1,2,3]}".toCharArray())
        assertFalse(isCurrentSyntaxSnapshot(source, source, "RP/blocks.json", "BP/entities/zombie.json"))
    }
}
