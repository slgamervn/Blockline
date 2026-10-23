package com.blockline.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {
    private val palette = Palette(
        key = Color(0xFF9CDCFE),
        string = Color(0xFFCE9178),
        number = Color(0xFFB5CEA8),
        keyword = Color(0xFF569CD6),
        comment = Color(0xFF6A9955),
        boolean = Color(0xFF569CD6),
        function = Color(0xFFDCDCAA),
        property = Color(0xFF9CDCFE),
        identifier = Color(0xFFD4D4D4)
    )

    @Test
    fun highlightsJavaScriptAndMinecraftFormattingColorsTogether() {
        val source = """const label = "§aGreen §cRed";"""
        val colored = highlight(source, Lang.JS, palette)

        assertHasColor(colored, source.indexOf("const"), Color(0xFF569CD6))
        assertHasColor(colored, source.indexOf("Green"), Color(0xFF55FF55))
        assertHasColor(colored, source.indexOf("Red"), Color(0xFFFF5555))
    }

    @Test
    fun understandsEscapedUnicodeSectionSign() {
        val source = """{"text":"\u00a7aGreen"}"""
        val colored = highlight(source, Lang.JSON, palette)

        assertHasColor(colored, source.indexOf("Green"), Color(0xFF55FF55))
    }

    @Test
    fun restoresBaseStringColorAndFontAfterResetCode() {
        val source = """{"text":"§lBold§rNormal"}"""
        val colored = highlight(source, Lang.JSON, palette)
        val boldIndex = source.indexOf("Bold")
        val normalIndex = source.indexOf("Normal")

        assertTrue(colored.spanStyles.any {
            it.start <= boldIndex && it.end > boldIndex && it.item.fontWeight == FontWeight.Bold
        })
        assertHasColor(colored, boldIndex, palette.string)
        assertHasColor(colored, normalIndex, palette.string)
    }

    @Test
    fun highlightsMinecraftFormattingInsideMcfunctionStrings() {
        val source = "say \"§bWelcome\""
        val colored = highlight(source, Lang.MCFUNC, palette)

        assertHasColor(colored, source.indexOf("Welcome"), Color(0xFF55FFFF))
    }

    @Test
    fun treatsHtmlAsJavaScriptCapableSourceForCanvasApps() {
        assertEquals(Lang.JS, langFor("html"))
        assertEquals(Lang.JS, langFor("htm"))
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
