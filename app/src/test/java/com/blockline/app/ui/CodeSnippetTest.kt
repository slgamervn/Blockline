package com.blockline.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSnippetTest {
    @Test
    fun snippetIdsAreUniqueAndContentIsNotEmpty() {
        assertEquals(builtInSnippets.size, builtInSnippets.map { it.id }.toSet().size)
        assertTrue(builtInSnippets.isNotEmpty())
        assertTrue(builtInSnippets.all { it.id.isNotBlank() && it.code.isNotBlank() })
    }

    @Test
    fun snippetsHaveNoPlaceholderMarkers() {
        assertFalse(builtInSnippets.any { snippet ->
            snippet.code.contains("TODO", ignoreCase = true) ||
                snippet.code.contains("YOUR_CODE_HERE", ignoreCase = true)
        })
    }

    @Test
    fun snippetsIncludeFunctionAndScriptApiExamples() {
        assertTrue(builtInSnippets.any { it.id == "mcfunction.broadcast" && it.code.startsWith("tellraw @a") })
        assertTrue(builtInSnippets.any { it.id == "javascript.player-spawn" && it.code.contains("@minecraft/server") })
    }
}
