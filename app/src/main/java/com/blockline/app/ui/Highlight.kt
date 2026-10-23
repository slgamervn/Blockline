package com.blockline.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.blockline.app.R

enum class Lang { JSON, JS, MCFUNC, LANG }

class Palette(
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val comment: Color,
    val errorBackground: Color = Color(0x55F44336),
    val boolean: Color = Color(0xFFFF9E64),
    val function: Color = Color(0xFF80CBC4),
    val property: Color = key,
    val identifier: Color = Color(0xFFE6EDF3)
)

private val minecraftColorCodeRegex = Regex("§([0-9a-fA-Fk-orK-OR])")

data class MinecraftColorToken(val code: Char, val labelRes: Int, val color: Color)

data class MinecraftFormatToken(val code: Char, val labelRes: Int)

val minecraftColorPalette = listOf(
    MinecraftColorToken('0', R.string.mc_color_black, Color(0xFF000000)),
    MinecraftColorToken('1', R.string.mc_color_dark_blue, Color(0xFF0000AA)),
    MinecraftColorToken('2', R.string.mc_color_dark_green, Color(0xFF00AA00)),
    MinecraftColorToken('3', R.string.mc_color_dark_aqua, Color(0xFF00AAAA)),
    MinecraftColorToken('4', R.string.mc_color_dark_red, Color(0xFFAA0000)),
    MinecraftColorToken('5', R.string.mc_color_dark_purple, Color(0xFFAA00AA)),
    MinecraftColorToken('6', R.string.mc_color_gold, Color(0xFFFFAA00)),
    MinecraftColorToken('7', R.string.mc_color_gray, Color(0xFFAAAAAA)),
    MinecraftColorToken('8', R.string.mc_color_dark_gray, Color(0xFF555555)),
    MinecraftColorToken('9', R.string.mc_color_blue, Color(0xFF5555FF)),
    MinecraftColorToken('a', R.string.mc_color_green, Color(0xFF55FF55)),
    MinecraftColorToken('b', R.string.mc_color_aqua, Color(0xFF55FFFF)),
    MinecraftColorToken('c', R.string.mc_color_red, Color(0xFFFF5555)),
    MinecraftColorToken('d', R.string.mc_color_light_purple, Color(0xFFFF55FF)),
    MinecraftColorToken('e', R.string.mc_color_yellow, Color(0xFFFFFF55)),
    MinecraftColorToken('f', R.string.mc_color_white, Color(0xFFFFFFFF))
)

val minecraftFormatPalette = listOf(
    MinecraftFormatToken('k', R.string.mc_style_obfuscated),
    MinecraftFormatToken('l', R.string.mc_style_bold),
    MinecraftFormatToken('m', R.string.mc_style_strikethrough),
    MinecraftFormatToken('n', R.string.mc_style_underline),
    MinecraftFormatToken('o', R.string.mc_style_italic),
    MinecraftFormatToken('r', R.string.mc_style_reset)
)

private val minecraftColors = minecraftColorPalette.associate { it.code to it.color }

private fun AnnotatedString.Builder.addMinecraftFormattingSpans(
    value: String,
    absoluteStart: Int,
    baseColor: Color
) {
    val codes = minecraftColorCodeRegex.findAll(value).toList()
    if (codes.isEmpty()) return
    var color = baseColor
    var bold = false
    var italic = false
    var strike = false
    var underline = false
    var segmentStart = 0

    fun addCurrentStyle(start: Int, end: Int) {
        if (start >= end) return
        addStyle(
            SpanStyle(
                color = color,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when {
                    strike && underline -> TextDecoration.combine(
                        listOf(TextDecoration.LineThrough, TextDecoration.Underline)
                    )
                    strike -> TextDecoration.LineThrough
                    underline -> TextDecoration.Underline
                    else -> TextDecoration.None
                }
            ),
            absoluteStart + start,
            absoluteStart + end
        )
    }

    for (match in codes) {
        addCurrentStyle(segmentStart, match.range.first)
        val code = match.groupValues[1].lowercase().first()
        when {
            minecraftColors.containsKey(code) -> {
                color = minecraftColors.getValue(code)
                bold = false
                italic = false
                strike = false
                underline = false
            }
            code == 'r' -> {
                color = baseColor
                bold = false
                italic = false
                strike = false
                underline = false
            }
            code == 'l' -> bold = true
            code == 'o' -> italic = true
            code == 'm' -> strike = true
            code == 'n' -> underline = true
            // §k changes glyph rendering in Minecraft itself; preserve its code in the editor.
        }
        segmentStart = match.range.first
    }
    addCurrentStyle(segmentStart, value.length)
}

private val jsonRx = Regex(
    """//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\\n])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b"""
)
private val jsRx = Regex(
    """//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`|\b\d+(?:\.\d+)?\b|\b[A-Za-z_$][\w$]*\b"""
)
private val jsKeywords = setOf(
    "const", "let", "var", "function", "return", "if", "else", "for", "while",
    "import", "export", "from", "new", "class", "async", "await", "true", "false",
    "null", "undefined", "of", "in", "switch", "case", "break", "continue", "try",
    "catch", "throw", "default", "this", "super", "extends", "implements", "interface",
    "type", "typeof", "instanceof", "void", "delete", "do", "while", "yield", "with",
    "get", "set", "static", "constructor"
)
private val jsBooleanLiterals = setOf("true", "false", "null", "undefined")
private val jsonBooleanLiterals = setOf("true", "false", "null")
private val mcRx = Regex(
    """#[^\n]*|@[aeprs]\b|-?\d+(?:\.\d+)?|\b(?:execute|run|as|at|if|unless|positioned|rotated|align|facing|anchored|in|give|tp|teleport|scoreboard|tag|summon|kill|function|say|tellraw|title|effect|playsound|setblock|fill|clone)\b"""
)

private val langRx = Regex(
    """(?m)^[ \t]*##[^\r\n]*|^[ \t]*[^=\r\n]+(?==)|(?<==)[^\r\n]+"""
)

fun langFor(ext: String): Lang? = when (ext.lowercase()) {
    "json", "mcmeta", "json5" -> Lang.JSON
    "js", "mjs", "ts" -> Lang.JS
    "mcfunction" -> Lang.MCFUNC
    "lang" -> Lang.LANG
    else -> null
}

private fun isKey(s: String, from: Int): Boolean {
    var i = from
    while (i < s.length && s[i].isWhitespace()) i++
    return i < s.length && s[i] == ':'
}

fun highlight(src: String, lang: Lang, p: Palette, errorOffset: Int? = null): AnnotatedString = buildAnnotatedString {
    append(src)
    val rx = when (lang) {
        Lang.JSON -> jsonRx
        Lang.JS -> jsRx
        Lang.MCFUNC -> mcRx
        Lang.LANG -> langRx
    }
    for (m in rx.findAll(src)) {
        val t = m.value
        if (lang == Lang.LANG) {
            val lineStart = src.lastIndexOf('\n', m.range.first - 1) + 1
            val prefix = src.substring(lineStart, m.range.first)
            when {
                t.trimStart().startsWith("##") -> addStyle(SpanStyle(color = p.comment), m.range.first, m.range.last + 1)
                '=' !in prefix -> addStyle(SpanStyle(color = p.key), m.range.first, m.range.last + 1)
                else -> {
                    val inlineComment = t.indexOf("\t##")
                    if (inlineComment >= 0) {
                        if (inlineComment > 0) addStyle(SpanStyle(color = p.string), m.range.first, m.range.first + inlineComment)
                        addStyle(SpanStyle(color = p.comment), m.range.first + inlineComment, m.range.last + 1)
                    } else {
                        addStyle(SpanStyle(color = p.string), m.range.first, m.range.last + 1)
                    }
                }
            }
            continue
        }
        var nextIndex = (m.range.last + 1).coerceAtMost(src.length)
        while (nextIndex < src.length && src[nextIndex].isWhitespace()) nextIndex++
        val nextNonWhitespace = src.getOrNull(nextIndex)
        val color = when {
            t.startsWith("//") || t.startsWith("/*") || (lang == Lang.MCFUNC && t.startsWith("#")) -> p.comment
            lang == Lang.JSON && t in jsonBooleanLiterals -> p.boolean
            lang == Lang.JS && t in jsBooleanLiterals -> p.boolean
            t[0] == '"' || t[0] == '\'' || t[0] == 96.toChar() ->
                if (lang == Lang.JSON && isKey(src, m.range.last + 1)) p.key
                else if (lang == Lang.JS && nextNonWhitespace == ':') p.property
                else p.string
            t[0] == '@' -> p.key
            t[0].isDigit() || t[0] == '-' -> p.number
            lang == Lang.JS && t in jsKeywords -> p.keyword
            lang == Lang.JS && nextNonWhitespace == '(' -> p.function
            lang == Lang.JS && nextNonWhitespace == ':' -> p.property
            lang == Lang.JS -> p.identifier
            else -> p.keyword
        }
        addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
        val isQuotedString = t.length >= 2 && (t.first() == '"' || t.first() == '\'' || t.first() == '`')
        val isJsonKey = lang == Lang.JSON && t.startsWith('"') && isKey(src, m.range.last + 1)
        if (isQuotedString && !isJsonKey) {
            addMinecraftFormattingSpans(
                t.substring(1, t.length - 1),
                m.range.first + 1,
                p.string
            )
        }
    }
    if ((lang == Lang.JSON || lang == Lang.LANG) && errorOffset != null && src.isNotEmpty()) {
        val position = errorOffset.coerceIn(0, src.lastIndex)
        val lineStart = src.lastIndexOf('\n', position - 1) + 1
        val lineBreak = src.indexOf('\n', position)
        val lineEnd = if (lineBreak >= 0) lineBreak else src.length
        if (lineStart < lineEnd) addStyle(SpanStyle(background = p.errorBackground), lineStart, lineEnd)
    }
}

class HighlightTransformation(
    private val lang: Lang,
    private val p: Palette,
    private val errorOffset: Int? = null
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text, lang, p, errorOffset), OffsetMapping.Identity)
}

class PrecomputedHighlightTransformation(
    private val sourceText: String,
    private val highlightedText: AnnotatedString
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            if (text.text == sourceText) highlightedText else text,
            OffsetMapping.Identity
        )
}


fun highlightCurrentLine(
    source: String,
    cursorPosition: Int,
    syntax: AnnotatedString,
    background: Color
): AnnotatedString {
    if (source.isEmpty() || syntax.text != source) return syntax
    val cursor = cursorPosition.coerceIn(0, source.length)
    val lineStart = if (cursor == 0) 0 else source.lastIndexOf('\n', cursor - 1) + 1
    val foundLineEnd = source.indexOf('\n', cursor)
    var lineEnd = if (foundLineEnd >= 0) foundLineEnd else source.length
    if (lineEnd == lineStart && lineEnd < source.length) lineEnd++
    if (lineStart >= lineEnd) return syntax
    return buildAnnotatedString {
        append(syntax)
        addStyle(SpanStyle(background = background), lineStart, lineEnd)
    }
}
