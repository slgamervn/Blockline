package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDocumentPolicyTest {
    @Test
    fun acceptsCommonBedrockAndDeveloperTextFormatsCaseInsensitively() {
        listOf(
            "main.ts", "main.mjs", "manifest.json", "entity.mcmeta", "en_US.lang",
            "settings.toml", "data.csv", "config.ini", "main.mcfunction", "index.HTML"
        ).forEach { name ->
            assertTrue("$name should be importable as text", TextDocumentPolicy.isSupported(name))
        }
    }

    @Test
    fun rejectsNamesWithoutAnAllowedTextExtension() {
        listOf("", ".", "..", "pack.mcpack", "program.apk", "archive.zip", "image.png")
            .forEach { name -> assertFalse("$name must not be imported as a text document", TextDocumentPolicy.isSupported(name)) }
    }

    @Test
    fun sanitizesPathSeparatorsAndUnsafeCharacters() {
        assertEquals("mob_.json", TextDocumentPolicy.sanitizeDisplayName("mob?.json"))
        assertEquals("my file.ts", TextDocumentPolicy.sanitizeDisplayName("my file.ts"))
    }

    @Test
    fun decodesValidUtf8AndRejectsMalformedOrBinaryContent() {
        assertEquals("Tiếng Việt 🧱", TextDocumentPolicy.decodeUtf8("Tiếng Việt 🧱".toByteArray(Charsets.UTF_8)))
        try {
            TextDocumentPolicy.decodeUtf8(byteArrayOf(0xc3.toByte(), 0x28))
            throw AssertionError("Malformed UTF-8 must not be silently replaced.")
        } catch (_: IllegalArgumentException) {
        }
        try {
            TextDocumentPolicy.decodeUtf8(byteArrayOf(0x41, 0x00, 0x42))
            throw AssertionError("Binary text containing NUL must be rejected.")
        } catch (_: IllegalArgumentException) {
        }
    }
}
