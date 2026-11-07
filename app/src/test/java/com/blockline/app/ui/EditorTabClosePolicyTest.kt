package com.blockline.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTabClosePolicyTest {
    @Test
    fun flushesWhenClosingTheActiveEditableDocument() {
        assertTrue(
            shouldFlushEditorBeforeTabClose(
                closingPath = "RP/blocks.json",
                activePath = "RP/blocks.json",
                readOnly = false,
                isOpeningFile = false
            )
        )
    }

    @Test
    fun doesNotFlushWhenClosingAnotherTab() {
        assertFalse(
            shouldFlushEditorBeforeTabClose(
                closingPath = "RP/entities/zombie.json",
                activePath = "RP/blocks.json",
                readOnly = false,
                isOpeningFile = false
            )
        )
    }

    @Test
    fun doesNotFlushReadOnlyOrStillLoadingDocuments() {
        assertFalse(
            shouldFlushEditorBeforeTabClose(
                closingPath = "RP/blocks.json",
                activePath = "RP/blocks.json",
                readOnly = true,
                isOpeningFile = false
            )
        )
        assertFalse(
            shouldFlushEditorBeforeTabClose(
                closingPath = "RP/blocks.json",
                activePath = "RP/blocks.json",
                readOnly = false,
                isOpeningFile = true
            )
        )
    }
}
