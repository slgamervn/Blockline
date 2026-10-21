package com.blockline.app.data

import java.util.Locale

enum class ScriptApiSource { LIVE, CACHE, BUNDLED }

data class ScriptApiMember(
    val name: String,
    val signature: String,
    val documentation: String,
    val rawDeclaration: String
)

data class ScriptApiSymbol(
    val name: String,
    val kind: String,
    val signature: String,
    val documentation: String,
    val members: List<ScriptApiMember>,
    val rawDeclaration: String,
    val unparsedMembers: List<String>
)

data class ScriptApiLoadResult(
    val packageName: String,
    val version: String,
    val symbols: List<ScriptApiSymbol>,
    val rawSource: String,
    val source: ScriptApiSource,
    val availableVersions: List<String>,
    val error: String? = null
)

object ScriptApiDeclarationParser {
    private val declarationPattern = Regex(
        """(?m)^[ \t]*(?:export[ \t]+)?(?:(?:declare|abstract)[ \t]+)*(class|interface|enum|function|const|let|var|type|namespace)[ \t]+([A-Za-z_$][A-Za-z0-9_$]*)\b[^\r\n]*(?:\r?\n|$)"""
    )
    private val memberNamePattern = Regex(
        """^(?:(?:public|private|protected|static|readonly|abstract|override|declare|async|get|set)[ \t]+)*([A-Za-z_$][A-Za-z0-9_$]*|constructor)(?:\?)?[ \t]*(?:<[^;{}]*>)?[ \t]*(?=\(|:|!|=)"""
    )
    private val commentPattern = Regex("""(?s)/\*.*?\*/|//[^\r\n]*""")
    private val whitespacePattern = Regex("""\s+""")

    fun parse(source: String, packageName: String, version: String): ScriptApiLoadResult {
        val declarations = declarationPattern.findAll(source).toList()
        val symbols = ArrayList<ScriptApiSymbol>(declarations.size)
        declarations.forEachIndexed { index, match ->
            val start = match.range.first
            val end = declarations.getOrNull(index + 1)?.range?.first ?: source.length
            val raw = source.substring(start, end).trim()
            if (raw.isEmpty()) return@forEachIndexed
            val kind = match.groupValues[1]
            val name = match.groupValues[2]
            val doc = cleanDocumentation(documentationBefore(source, start))
            val headLine = raw.substringBefore('\n').trim()
            val signatureSource = if (kind in setOf("class", "interface", "enum", "namespace")) {
                headLine.substringBefore('{').trim()
            } else {
                stripComments(raw).substringBefore(';').trim()
            }
            val body = if (kind in setOf("class", "interface", "enum", "namespace")) extractBody(raw) else null
            val parsedMembers = body?.let(::parseMembers) ?: ParsedMembers(emptyList(), emptyList())
            symbols += ScriptApiSymbol(
                name = name,
                kind = kind,
                signature = normalizeSignature(signatureSource),
                documentation = doc,
                members = parsedMembers.members,
                rawDeclaration = raw,
                unparsedMembers = parsedMembers.unparsed
            )
        }
        return ScriptApiLoadResult(
            packageName = packageName,
            version = version,
            symbols = symbols,
            rawSource = source,
            source = ScriptApiSource.BUNDLED,
            availableVersions = listOf(version),
            error = if (symbols.isEmpty() && source.isNotBlank()) {
                "No declarations were recognized; raw TypeScript is retained."
            } else null
        )
    }

    private data class ParsedMembers(
        val members: List<ScriptApiMember>,
        val unparsed: List<String>
    )

    private fun parseMembers(body: String): ParsedMembers {
        val members = ArrayList<ScriptApiMember>()
        val unparsed = ArrayList<String>()
        splitMembers(body).forEach { rawMember ->
            val doc = leadingDocumentation(rawMember)
            val clean = stripComments(rawMember).trim()
            if (clean.isBlank()) return@forEach
            val match = memberNamePattern.find(clean)
            if (match == null) {
                unparsed += rawMember.trim()
            } else {
                members += ScriptApiMember(
                    name = match.groupValues[1],
                    signature = normalizeSignature(clean),
                    documentation = cleanDocumentation(doc),
                    rawDeclaration = rawMember.trim()
                )
            }
        }
        return ParsedMembers(members, unparsed)
    }

    private fun splitMembers(body: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var i = 0
        var parentheses = 0
        var brackets = 0
        var braces = 0
        var angles = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (i < body.length) {
            val c = body[i]
            val next = body.getOrNull(i + 1)
            if (lineComment) {
                if (c == '\n') lineComment = false
                i++
                continue
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false
                    i += 2
                } else i++
                continue
            }
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
                i++
                continue
            }
            if (c == '/' && next == '/') {
                lineComment = true
                i += 2
                continue
            }
            if (c == '/' && next == '*') {
                blockComment = true
                i += 2
                continue
            }
            if (c == '"' || c == '\'') {
                quote = c
                i++
                continue
            }
            when (c) {
                '(' -> parentheses++
                ')' -> if (parentheses > 0) parentheses--
                '[' -> brackets++
                ']' -> if (brackets > 0) brackets--
                '{' -> braces++
                '}' -> if (braces > 0) braces--
                '<' -> angles++
                '>' -> if (angles > 0) angles--
                ';' -> if (parentheses == 0 && brackets == 0 && braces == 0 && angles == 0) {
                    result += body.substring(start, i + 1)
                    start = i + 1
                }
            }
            i++
        }
        if (start < body.length) result += body.substring(start)
        return result
    }

    private fun extractBody(raw: String): String? {
        val open = raw.indexOf('{')
        if (open < 0) return null
        val close = matchingBrace(raw, open) ?: return null
        return raw.substring(open + 1, close)
    }

    private fun matchingBrace(text: String, open: Int): Int? {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            if (lineComment) {
                if (c == '\n') lineComment = false
                i++
                continue
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false
                    i += 2
                } else i++
                continue
            }
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
                i++
                continue
            }
            if (c == '/' && next == '/') {
                lineComment = true
                i += 2
                continue
            }
            if (c == '/' && next == '*') {
                blockComment = true
                i += 2
                continue
            }
            if (c == '"' || c == '\'') {
                quote = c
                i++
                continue
            }
            if (c == '{') depth++
            if (c == '}') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return null
    }

    private fun documentationBefore(text: String, end: Int): String {
        val prefix = text.substring(0, end.coerceIn(0, text.length)).trimEnd()
        return Regex("""(?s)(/\*\*.*?\*/)\s*$""").find(prefix)?.groupValues?.get(1).orEmpty()
    }

    private fun leadingDocumentation(text: String): String =
        Regex("""(?s)^\s*(/\*\*.*?\*/)""").find(text)?.groupValues?.get(1).orEmpty()

    private fun cleanDocumentation(documentation: String): String =
        documentation.removePrefix("/**").removeSuffix("*/")
            .lineSequence()
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private fun stripComments(text: String): String = commentPattern.replace(text, " ")

    private fun normalizeSignature(text: String): String =
        whitespacePattern.replace(stripComments(text), " ").trim()
}
