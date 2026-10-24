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
    fun detectsHtmlXmlAndCssAsDedicatedLanguageModes() {
        assertEquals(Lang.HTML, langFor("html"))
        assertEquals(Lang.HTML, langFor("htm"))
        assertEquals(Lang.HTML, langFor("xml"))
        assertEquals(Lang.CSS, langFor("css"))
        assertEquals(Lang.JS, langFor("ts"))
    }

    @Test
    fun highlightsHtmlCommentsTagsAttributesAndQuotedValues() {
        val source = """<!-- note --><div class="hero">Hello</div>"""
        val colored = highlight(source, Lang.HTML, palette)
        assertHasColor(colored, source.indexOf("<!--"), palette.comment)
        assertHasColor(colored, source.indexOf("<div"), palette.keyword)
        assertHasColor(colored, source.indexOf("class"), palette.key)
        assertHasColor(colored, source.indexOf("hero"), palette.string)
    }

    @Test
    fun highlightsCssCommentsSelectorsPropertiesAndValues() {
        val source = """/* note */ .hero { color: #ff00aa; margin: 4px; }"""
        val colored = highlight(source, Lang.CSS, palette)
        assertHasColor(colored, source.indexOf("/*"), palette.comment)
        assertHasColor(colored, source.indexOf(".hero"), palette.function)
        assertHasColor(colored, source.indexOf("color"), palette.key)
        assertHasColor(colored, source.indexOf("#ff00aa"), palette.function)
        assertHasColor(colored, source.indexOf("4px"), palette.number)
    }


    @Test
    fun rendersMinecraftPreviewWithoutShowingFormattingCodes() {
        val preview = minecraftPreviewText("§aGreen §lBold §cRed§r Normal", Color(0xFFD4D4D4))
        assertEquals("Green Bold Red Normal", preview.text)
        assertHasColor(preview, preview.text.indexOf("Green"), Color(0xFF55FF55))
        assertHasColor(preview, preview.text.indexOf("Red"), Color(0xFFFF5555))
        assertHasColor(preview, preview.text.indexOf("Normal"), Color(0xFFD4D4D4))
        assertTrue(preview.spanStyles.any {
            it.start <= preview.text.indexOf("Bold") && it.end > preview.text.indexOf("Bold") &&
                it.item.fontWeight == FontWeight.Bold
        })
    }

    @Test
    fun previewKeepsUnicodeAndVietnameseText() {
        val preview = minecraftPreviewText("§bXin chào thế giới 🌱", Color.White)
        assertEquals("Xin chào thế giới 🌱", preview.text)
        assertHasColor(preview, 0, Color(0xFF55FFFF))
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
