package com.blockline.app.data

import org.json.JSONObject
import org.json.JSONTokener

object UnicodeEscapeTools {
    private val unicodeEscape = Regex("""\\u([0-9a-fA-F]{4})""")
    private val literalCharacters = setOf('#', '$', '@', '*', '(', ')', '-', '\'', '=', '_')

    fun decode(input: String): String {
        if (input.isEmpty()) return input
        val cleaned = JsonTools.stripTrailingCommas(JsonTools.stripComments(input))
        val issue = ProjectValidator.inspectJsonSyntax(input)
        if (issue == null || issue.severity == IssueSeverity.WARNING) {
            return runCatching {
                JsonTools.format(transformJsonStrings(cleaned, encode = false))
            }.getOrElse { decodeRaw(input) }
        }
        return decodeRaw(input)
    }

    fun encode(input: String): String {
        if (input.isEmpty()) return input
        val cleaned = JsonTools.stripTrailingCommas(JsonTools.stripComments(input))
        val issue = ProjectValidator.inspectJsonSyntax(input)
        if (issue == null || issue.severity == IssueSeverity.WARNING) {
            return runCatching {
                JsonTools.format(transformJsonStrings(cleaned, encode = true))
            }.getOrElse { encodeRaw(input) }
        }
        return encodeRaw(input)
    }

    fun autoFixJson(input: String): String {
        val commentsRemoved = stripCommentsAware(input)
        val looseSyntaxFixed = quoteLooseJsonTokens(commentsRemoved)
        return JsonTools.stripTrailingCommas(looseSyntaxFixed)
    }

    fun formatJson(input: String): String {
        val normalized = JsonTools.stripTrailingCommas(JsonTools.stripComments(input))
        requireNoJsonErrors(normalized)
        return JsonTools.format(normalized)
    }

    fun minifyJson(input: String): String {
        val fixed = autoFixJson(input)
        requireNoJsonErrors(fixed)
        return compactJson(fixed)
    }

    private fun transformJsonStrings(source: String, encode: Boolean): String {
        val output = StringBuilder(source.length + 16)
        var index = 0
        while (index < source.length) {
            if (source.startsWith("//", index)) {
                val end = source.indexOf('\n', index).let { if (it < 0) source.length else it }
                output.append(source, index, end)
                index = end
            } else if (source.startsWith("/*", index)) {
                val closing = source.indexOf("*/", index + 2)
                val end = if (closing < 0) source.length else closing + 2
                output.append(source, index, end)
                index = end
            } else if (source[index] == '"') {
                val end = quotedTokenEnd(source, index, '"')
                    ?: throw IllegalArgumentException("Unterminated JSON string.")
                val token = source.substring(index, end)
                val decoded = JSONTokener(token).nextValue() as? String
                    ?: throw IllegalArgumentException("Unable to read JSON string.")
                output.append(if (encode) "\"" + encodeCharacters(decoded) + "\"" else JSONObject.quote(decoded))
                index = end
            } else {
                output.append(source[index])
                index++
            }
        }
        return output.toString()
    }

    private fun decodeRaw(input: String): String = unicodeEscape.replace(input) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }

    private fun encodeRaw(input: String): String = encodeCharacters(input)

    private fun encodeCharacters(value: String): String {
        val output = StringBuilder(value.length * 6)
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            index += Character.charCount(codePoint)
            if (codePoint <= Char.MAX_VALUE.code) {
                val char = codePoint.toChar()
                if (char in literalCharacters) {
                    output.append(char)
                } else {
                    output.append("\\u").append(codePoint.toString(16).padStart(4, '0'))
                }
            } else {
                val scalar = codePoint - 0x10000
                val high = 0xD800 + (scalar shr 10)
                val low = 0xDC00 + (scalar and 0x3FF)
                output.append("\\u").append(high.toString(16).padStart(4, '0'))
                output.append("\\u").append(low.toString(16).padStart(4, '0'))
            }
        }
        return output.toString()
    }

    private fun requireNoJsonErrors(input: String) {
        val issue = ProjectValidator.inspectJsonSyntax(input)
        require(issue == null || issue.severity != IssueSeverity.ERROR) {
            val location = if (issue?.line != null) " at line " + issue.line + ", column " + (issue.column ?: 1) else ""
            "Invalid JSON" + location + ": " + (issue?.message.orEmpty())
        }
    }

    private fun compactJson(input: String): String {
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            when {
                input.startsWith("//", index) -> {
                    val end = input.indexOf('\n', index).let { if (it < 0) input.length else it }
                    index = end
                }
                input.startsWith("/*", index) -> {
                    val closing = input.indexOf("*/", index + 2)
                    index = if (closing < 0) input.length else closing + 2
                }
                input[index] == '"' -> {
                    val end = quotedTokenEnd(input, index, '"')
                        ?: throw IllegalArgumentException("Unterminated JSON string.")
                    output.append(input, index, end)
                    index = end
                }
                input[index].isWhitespace() -> index++
                else -> {
                    output.append(input[index])
                    index++
                }
            }
        }
        return output.toString()
    }

    private fun stripCommentsAware(input: String): String {
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char == '"' || char == '\'') {
                val end = quotedTokenEnd(input, index, char)
                if (end == null) {
                    output.append(char)
                    index++
                } else {
                    output.append(input, index, end)
                    index = end
                }
            } else if (input.startsWith("//", index)) {
                while (index < input.length && input[index] != '\n' && input[index] != '\r') {
                    output.append(' ')
                    index++
                }
            } else if (input.startsWith("/*", index)) {
                val closing = input.indexOf("*/", index + 2)
                val end = if (closing < 0) input.length else closing + 2
                while (index < end) {
                    val current = input[index++]
                    output.append(if (current == '\n' || current == '\r') current else ' ')
                }
            } else {
                output.append(char)
                index++
            }
        }
        return output.toString()
    }

    private fun quoteLooseJsonTokens(input: String): String {
        val output = StringBuilder(input.length + 16)
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '"' -> {
                    val end = quotedTokenEnd(input, index, '"')
                    if (end == null) {
                        output.append(char)
                        index++
                    } else {
                        output.append(input, index, end)
                        index = end
                    }
                }
                char == '\'' -> {
                    val single = singleQuotedString(input, index)
                    if (single == null) {
                        output.append(char)
                        index++
                    } else {
                        output.append(JSONObject.quote(single.first))
                        index = single.second
                    }
                }
                char == '_' || char == '$' || char.isLetter() -> {
                    val start = index
                    index++
                    while (index < input.length &&
                        (input[index] == '_' || input[index] == '$' || input[index].isLetterOrDigit())
                    ) index++
                    val token = input.substring(start, index)
                    var previous = start - 1
                    while (previous >= 0 && input[previous].isWhitespace()) previous--
                    var next = index
                    while (next < input.length && input[next].isWhitespace()) next++
                    if (previous >= 0 && input[previous] in "{," && next < input.length && input[next] == ':') {
                        output.append(JSONObject.quote(token))
                    } else {
                        output.append(token)
                    }
                }
                else -> {
                    output.append(char)
                    index++
                }
            }
        }
        return output.toString()
    }

    private fun singleQuotedString(input: String, start: Int): Pair<String, Int>? {
        val decoded = StringBuilder()
        var index = start + 1
        while (index < input.length) {
            val char = input[index++]
            when (char) {
                '\'' -> return decoded.toString() to index
                '\\' -> {
                    if (index >= input.length) return null
                    val escaped = input[index++]
                    decoded.append(
                        when (escaped) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '\\' -> '\\'
                            '\'' -> '\''
                            '"' -> '"'
                            else -> escaped
                        }
                    )
                }
                else -> decoded.append(char)
            }
        }
        return null
    }

    private fun quotedTokenEnd(input: String, start: Int, quote: Char): Int? {
        if (start >= input.length || input[start] != quote) return null
        var index = start + 1
        while (index < input.length) {
            when (input[index]) {
                '\\' -> index += 2
                quote -> return index + 1
                else -> index++
            }
        }
        return null
    }
}

