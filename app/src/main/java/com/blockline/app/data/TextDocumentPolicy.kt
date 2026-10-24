package com.blockline.app.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object TextDocumentPolicy {
    private val supportedExtensions = setOf(
        "txt", "html", "htm", "css", "js", "mjs", "cjs", "ts", "json", "mcmeta",
        "xml", "md", "markdown", "log", "yml", "yaml", "properties", "mcfunction",
        "lang", "toml", "csv", "ini", "conf", "cfg", "sh", "py", "kt", "java", "sql"
    )

    fun isSupported(displayName: String): Boolean {
        val name = displayName.trim()
        if (name.isEmpty() || name == "." || name == "..") return false
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in supportedExtensions
    }

    fun sanitizeDisplayName(displayName: String): String {
        val name = displayName.trim()
        require(name.isNotEmpty() && name != "." && name != "..") { "Invalid file name." }
        val safe = name.map { character ->
            if (character.isLetterOrDigit() || character in "._- ") character else '_'
        }.joinToString("").trim()
        require(safe.isNotEmpty() && safe != "." && safe != "..") { "Invalid file name." }
        return safe
    }

    fun decodeUtf8(bytes: ByteArray): String {
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("The selected file is not valid UTF-8 text.", error)
        }
        require('\u0000' !in text) { "The selected file is not plain text." }
        return text
    }
}
