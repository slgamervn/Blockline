package com.blockline.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * A low-allocation lexical index for large source files. It stores only one checkpoint per
 * [checkpointStride] UTF-16 code units; the whole document is scanned off the UI thread once
 * per debounced source revision, while scrolling only tokenizes the visible window.
 *
 * Checkpoints inside long strings/comments carry the final token range and kind. That lets a
 * viewport inside a minified multi-megabyte line render correctly without re-scanning the
 * whole prefix (or rendering an AnnotatedString for the whole file).
 */
internal class CheckpointedSyntaxDocument private constructor(
    val source: String,
    val language: Lang,
    private val checkpoints: List<Checkpoint>,
    private val checkpointStride: Int
) {
    private enum class Kind {
        STRING, JSON_KEY, NUMBER, BOOLEAN, KEYWORD, COMMENT, FUNCTION, PROPERTY, IDENTIFIER, TARGET
    }

    private data class Checkpoint(
        val offset: Int,
        val tokenStart: Int = -1,
        val tokenEnd: Int = -1,
        val kind: Kind? = null
    )

    private data class Token(val end: Int, val kind: Kind)

    fun highlightWindow(startOffset: Int, endOffset: Int, palette: Palette): AnnotatedString {
        val start = startOffset.coerceIn(0, source.length)
        val end = endOffset.coerceIn(start, source.length)
        if (start >= end) return AnnotatedString("")
        val spans = ArrayList<Triple<Int, Int, Kind>>(128)
        var cursor = checkpointAtOrBefore(start).offset

        // If the viewport begins within a long token, use its precomputed end and classification.
        // No scan from the token's beginning is needed, even for a multi-megabyte JSON string.
        val initialCheckpoint = checkpointAtOrBefore(start)
        if (initialCheckpoint.kind != null && initialCheckpoint.tokenEnd > initialCheckpoint.offset) {
            addIntersection(
                spans,
                initialCheckpoint.tokenStart,
                initialCheckpoint.tokenEnd,
                initialCheckpoint.kind,
                start,
                end
            )
            cursor = initialCheckpoint.tokenEnd
        }

        while (cursor < end) {
            val token = scanToken(source, cursor, language)
            if (token == null) {
                cursor++
                continue
            }

            // A token longer than the checkpoint stride has a checkpoint inside it. Reuse its
            // final classification/end rather than walking the long token again on every scroll.
            val known = checkpointInsideToken(cursor)
            val tokenEnd = if (known?.tokenStart == cursor) known.tokenEnd else token.end
            val kind = if (known?.tokenStart == cursor) known.kind ?: token.kind else token.kind
            addIntersection(spans, cursor, tokenEnd, kind, start, end)
            cursor = tokenEnd.coerceAtLeast(cursor + 1)
        }

        return buildAnnotatedString {
            append(source.substring(start, end))
            spans.forEach { (tokenStart, tokenEnd, kind) ->
                val color = when (kind) {
                    Kind.STRING -> palette.string
                    Kind.JSON_KEY -> palette.key
                    Kind.NUMBER -> palette.number
                    Kind.BOOLEAN -> palette.boolean
                    Kind.KEYWORD -> palette.keyword
                    Kind.COMMENT -> palette.comment
                    Kind.FUNCTION -> palette.function
                    Kind.PROPERTY -> palette.property
                    Kind.IDENTIFIER -> palette.identifier
                    Kind.TARGET -> palette.key
                }
                addStyle(
                    SpanStyle(color = color),
                    (tokenStart - start).coerceAtLeast(0),
                    (tokenEnd - start).coerceAtMost(end - start)
                )
            }
        }
    }

    private fun addIntersection(
        spans: MutableList<Triple<Int, Int, Kind>>,
        tokenStart: Int,
        tokenEnd: Int,
        kind: Kind?,
        windowStart: Int,
        windowEnd: Int
    ) {
        if (kind == null) return
        val start = maxOf(tokenStart, windowStart)
        val end = minOf(tokenEnd, windowEnd)
        if (start < end) spans += Triple(start, end, kind)
    }

    private fun checkpointAtOrBefore(offset: Int): Checkpoint {
        var low = 0
        var high = checkpoints.lastIndex
        var answer = checkpoints.first()
        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = checkpoints[mid]
            if (candidate.offset <= offset) {
                answer = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    /**
     * Find a checkpoint within a long token that starts at [tokenStart]. Short tokens are scanned
     * directly (they are shorter than the stride, so the work is bounded).
     */
    private fun checkpointInsideToken(tokenStart: Int): Checkpoint? {
        var low = 0
        var high = checkpoints.lastIndex
        var firstAfter = checkpoints.size
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (checkpoints[mid].offset > tokenStart) {
                firstAfter = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        val candidate = checkpoints.getOrNull(firstAfter) ?: return null
        return candidate.takeIf {
            it.tokenStart == tokenStart && it.tokenEnd > it.offset && it.kind != null &&
                it.offset - tokenStart < checkpointStride * ((source.length / checkpointStride) + 1)
        }
    }

    companion object {
        private const val DEFAULT_CHECKPOINT_STRIDE = 2048

        private val jsKeywords = setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while",
            "import", "export", "from", "new", "class", "async", "await", "true", "false",
            "null", "undefined", "of", "in", "switch", "case", "break", "continue", "try",
            "catch", "throw", "default", "this", "super", "extends", "implements", "interface",
            "type", "typeof", "instanceof", "void", "delete", "do", "yield", "with", "get",
            "set", "static", "constructor"
        )
        private val mcfunctionKeywords = setOf(
            "execute", "run", "as", "at", "if", "unless", "positioned", "rotated", "align",
            "facing", "anchored", "in", "give", "tp", "teleport", "scoreboard", "tag",
            "summon", "kill", "function", "say", "tellraw", "title", "effect", "playsound",
            "setblock", "fill", "clone"
        )

        fun supports(language: Lang): Boolean =
            language == Lang.JSON || language == Lang.JS || language == Lang.MCFUNC

        fun build(
            source: String,
            language: Lang,
            checkpointStride: Int = DEFAULT_CHECKPOINT_STRIDE
        ): CheckpointedSyntaxDocument {
            require(supports(language)) { "Checkpointed syntax is not supported for $language." }
            require(checkpointStride >= 256) { "Checkpoint stride is too small." }

            val checkpoints = ArrayList<Checkpoint>((source.length / checkpointStride) + 2)
            checkpoints += Checkpoint(0)
            var nextCheckpoint = checkpointStride
            var cursor = 0
            while (cursor < source.length) {
                // A checkpoint exactly at a token start belongs to the un-tokenized boundary.
                while (nextCheckpoint <= cursor) {
                    checkpoints += Checkpoint(nextCheckpoint)
                    nextCheckpoint += checkpointStride
                }
                val token = scanToken(source, cursor, language)
                if (token == null) {
                    cursor++
                    continue
                }
                // Every checkpoint strictly inside this token receives complete token metadata.
                while (nextCheckpoint < token.end) {
                    checkpoints += Checkpoint(nextCheckpoint, cursor, token.end, token.kind)
                    nextCheckpoint += checkpointStride
                }
                cursor = token.end
            }
            return CheckpointedSyntaxDocument(source, language, checkpoints, checkpointStride)
        }

        private fun scanToken(source: String, start: Int, language: Lang): Token? {
            if (start !in source.indices) return null
            val first = source[start]

            // Comments must be recognized before slash punctuation and before strings.
            if ((language == Lang.JSON || language == Lang.JS) && first == '/' && start + 1 < source.length) {
                when (source[start + 1]) {
                    '/' -> {
                        var end = start + 2
                        while (end < source.length && source[end] != '\n' && source[end] != '\r') end++
                        return Token(end, Kind.COMMENT)
                    }
                    '*' -> {
                        var end = start + 2
                        while (end + 1 < source.length &&
                            !(source[end] == '*' && source[end + 1] == '/')
                        ) end++
                        if (end + 1 < source.length) end += 2 else end = source.length
                        return Token(end, Kind.COMMENT)
                    }
                }
            }
            if (language == Lang.MCFUNC && first == '#') {
                var end = start + 1
                while (end < source.length && source[end] != '\n' && source[end] != '\r') end++
                return Token(end, Kind.COMMENT)
            }

            val isQuote = when (language) {
                Lang.JSON -> first == '"'
                Lang.JS -> first == '"' || first == '\'' || first == '`'
                Lang.MCFUNC -> first == '"' || first == '\''
                else -> false
            }
            if (isQuote) {
                var end = start + 1
                var escaped = false
                var closed = false
                while (end < source.length) {
                    val ch = source[end]
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == first) {
                        end++
                        closed = true
                        break
                    }
                    // JSON strings cannot contain raw newlines, but continue to the next quote so
                    // malformed source still receives consistent lexical color rather than resets.
                    end++
                }
                val kind = when (language) {
                    Lang.JSON -> if (closed && nextNonWhitespace(source, end) == ':') Kind.JSON_KEY else Kind.STRING
                    Lang.JS -> if (nextNonWhitespace(source, end) == ':') Kind.PROPERTY else Kind.STRING
                    else -> Kind.STRING
                }
                return Token(end.coerceAtLeast(start + 1), kind)
            }

            if (language == Lang.MCFUNC && first == '@' && start + 1 < source.length &&
                source[start + 1] in "aeprs" &&
                (start + 2 == source.length || !isIdentifierPart(source[start + 2]))
            ) {
                return Token(start + 2, Kind.TARGET)
            }

            if (isNumberStart(source, start, language)) {
                var end = start
                if (source[end] == '-') end++
                while (end < source.length && source[end].isDigit()) end++
                if (end < source.length && source[end] == '.' &&
                    end + 1 < source.length && source[end + 1].isDigit()
                ) {
                    end++
                    while (end < source.length && source[end].isDigit()) end++
                }
                if (end < source.length && source[end] in "eE") {
                    var exponent = end + 1
                    if (exponent < source.length && source[exponent] in "+-") exponent++
                    val digitsStart = exponent
                    while (exponent < source.length && source[exponent].isDigit()) exponent++
                    if (exponent > digitsStart) end = exponent
                }
                return Token(end.coerceAtLeast(start + 1), Kind.NUMBER)
            }

            if (isIdentifierStart(first)) {
                var end = start + 1
                while (end < source.length && isIdentifierPart(source[end])) end++
                val word = if (end - start <= 64) source.substring(start, end) else ""
                val next = nextNonWhitespace(source, end)
                val kind = when (language) {
                    Lang.JSON -> if (word == "true" || word == "false" || word == "null") Kind.BOOLEAN else null
                    Lang.JS -> when {
                        word in jsKeywords -> if (word == "true" || word == "false" || word == "null" || word == "undefined") Kind.BOOLEAN else Kind.KEYWORD
                        next == '(' -> Kind.FUNCTION
                        next == ':' -> Kind.PROPERTY
                        else -> Kind.IDENTIFIER
                    }
                    Lang.MCFUNC -> if (word in mcfunctionKeywords) Kind.KEYWORD else null
                    else -> null
                }
                return if (kind != null) Token(end, kind) else null
            }
            return null
        }

        private fun isNumberStart(source: String, index: Int, language: Lang): Boolean {
            if (source[index].isDigit()) return true
            return language in setOf(Lang.JSON, Lang.MCFUNC) &&
                source[index] == '-' && index + 1 < source.length && source[index + 1].isDigit()
        }

        private fun isIdentifierStart(ch: Char): Boolean =
            ch == '_' || ch == '$' || ch.isLetter()

        private fun isIdentifierPart(ch: Char): Boolean =
            ch == '_' || ch == '$' || ch.isLetterOrDigit()

        private fun nextNonWhitespace(source: String, from: Int): Char? {
            var index = from.coerceAtLeast(0)
            while (index < source.length && source[index].isWhitespace()) index++
            return source.getOrNull(index)
        }
    }
}
