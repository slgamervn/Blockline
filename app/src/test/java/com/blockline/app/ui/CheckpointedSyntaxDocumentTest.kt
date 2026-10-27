package com.blockline.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointedSyntaxDocumentTest {
    private val palette = Palette(
        key = Color(0xFF9CDCFE),
        string = Color(0xFFCE9178),
        number = Color(0xFFB5CEA8),
        keyword = Color(0xFF569CD6),
        comment = Color(0xFF6A9955),
        boolean = Color(0xFFFF9E64),
        function = Color(0xFFDCDCAA),
        property = Color(0xFF9CDCFE),
        identifier = Color(0xFFD4D4D4)
    )

    @Test
    fun highlightsJsonKeysValuesAndTypesAcrossFiftyThousandLines() {
        val source = (1..50_000).joinToString("\n") {
            """{"id":$it,"enabled":true,"name":"Tiếng Việt 世界"}"""
        }
        val index = CheckpointedSyntaxDocument.build(source, Lang.JSON)
        val start = source.lastIndexOf("\"enabled\"")
        val window = index.highlightWindow(start, (start + 48).coerceAtMost(source.length), palette)

        assertEquals(source.substring(start, (start + 48).coerceAtMost(source.length)), window.text)
        assertHasColor(window, window.text.indexOf("enabled"), palette.key)
        assertHasColor(window, window.text.indexOf("true"), palette.boolean)
        assertTrue("Index should stay compact relative to the source", index.checkpointCount < source.length / 512)
    }

    @Test
    fun handlesFiveMiBMinifiedJsonWithoutMaterializingWholeAnnotatedDocument() {
        val value = "a".repeat(5 * 1024 * 1024)
        val source = """{"payload":"$value","enabled":true,"count":42}"""
        val index = CheckpointedSyntaxDocument.build(source, Lang.JSON)
        val valueOffset = source.indexOf(value) + value.length / 2
        val window = index.highlightWindow(valueOffset - 16, valueOffset + 64, palette)

        assertTrue(window.text.startsWith("a"))
        assertHasColor(window, 16, palette.string)
        assertTrue(index.checkpointCount < 5_000)
        val keyOffset = source.indexOf("\"enabled\"")
        val keyWindow = index.highlightWindow(keyOffset, keyOffset + 30, palette)
        assertHasColor(keyWindow, keyWindow.text.indexOf("enabled"), palette.key)
        assertHasColor(keyWindow, keyWindow.text.indexOf("true"), palette.boolean)
    }

    @Test
    fun escapedQuotesBackslashesUnicodeVietnameseAndBracesRemainInsideJsonString() {
        val source = buildString {
            append("""{"payload":" """)
            append("x".repeat(8_000))
            append(""" \"escaped quote\" \\ { "nested": true } Tiếng Việt 🌱","ok":true}""")
        }
        val index = CheckpointedSyntaxDocument.build(source, Lang.JSON)
        val valueOffset = source.indexOf("Tiếng Việt")
        val window = index.highlightWindow(valueOffset - 12, valueOffset + 24, palette)

        assertHasColor(window, window.text.indexOf("Tiếng Việt"), palette.string)
        val boolOffset = source.lastIndexOf("true")
        val boolWindow = index.highlightWindow(boolOffset, boolOffset + 4, palette)
        assertHasColor(boolWindow, 0, palette.boolean)
        assertSame(source, index.source)
    }

    @Test
    fun highlightsLongJavaScriptStringsAndDoesNotReuseAnOldSnapshot() {
        val source = "const payload = '" + "x".repeat(80_000) +
            """ \'quoted\' \\ {braces} Tiếng Việt 🌱 '; const ready = true; function go() {}"""
        val index = CheckpointedSyntaxDocument.build(source, Lang.JS)
        val valueOffset = source.indexOf("Tiếng Việt")
        val valueWindow = index.highlightWindow(valueOffset - 5, valueOffset + 25, palette)
        assertHasColor(valueWindow, valueWindow.text.indexOf("Tiếng Việt"), palette.string)

        val keywordOffset = source.indexOf("const")
        val keywordWindow = index.highlightWindow(keywordOffset, keywordOffset + 5, palette)
        assertHasColor(keywordWindow, 0, palette.keyword)

        val changed = String(source.toCharArray())
        assertFalse("A result built for an older String snapshot must not match a newer revision", index.source === changed)
    }

    @Test
    fun handlesLongMcfunctionStringsTargetsAndComments() {
        val suffix = """ {braces} Việt Nam 🌱" # generated comment
execute as @e run say "done"""
        val source = "say \"" + "x".repeat(75_000) + suffix + "\""
        val index = CheckpointedSyntaxDocument.build(source, Lang.MCFUNC)
        val valueOffset = source.indexOf("Việt Nam")
        val valueWindow = index.highlightWindow(valueOffset - 3, valueOffset + 20, palette)
        assertHasColor(valueWindow, valueWindow.text.indexOf("Việt Nam"), palette.string)

        val commentOffset = source.indexOf("# generated")
        val commentWindow = index.highlightWindow(commentOffset, commentOffset + 20, palette)
        assertHasColor(commentWindow, 0, palette.comment)

        val targetOffset = source.indexOf("@e")
        val targetWindow = index.highlightWindow(targetOffset, targetOffset + 2, palette)
        assertHasColor(targetWindow, 0, palette.key)
    }

    private fun assertHasColor(
        text: androidx.compose.ui.text.AnnotatedString,
        index: Int,
        color: Color
    ) {
        assertTrue(
            "Expected $color at index $index. Spans were: ${text.spanStyles}",
            text.spanStyles.any { it.start <= index && it.end > index && it.item.color == color }
        )
    }
}
