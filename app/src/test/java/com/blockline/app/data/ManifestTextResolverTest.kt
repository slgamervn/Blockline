package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestTextResolverTest {
    @Test
    fun resolvesManifestKeysFromThePreferredBedrockLanguageFile() {
        val pack = Files.createTempDirectory("blockline-manifest-text-").toFile()
        try {
            val texts = File(pack, "texts").apply { mkdirs() }
            File(texts, "languages.json").writeText("""["en_US", "vi_VN"]""")
            File(texts, "en_US.lang").writeText("pack.name=Sample pack\npack.description=Sample description\n")
            File(texts, "vi_VN.lang").writeText("pack.name=Gói mẫu\npack.description=Mô tả mẫu\n")

            assertEquals("Gói mẫu", ManifestTextResolver.resolve(pack, "pack.name", "vi-VN"))
            assertEquals("Mô tả mẫu", ManifestTextResolver.resolve(pack, "pack.description", "vi_VN"))
            assertEquals("Sample pack", ManifestTextResolver.resolve(pack, "pack.name", "fr-FR"))
        } finally {
            pack.deleteRecursively()
        }
    }

    @Test
    fun resolvesDefaultPackKeysWhenManifestValueIsBlank() {
        val pack = Files.createTempDirectory("blockline-manifest-text-fallback-").toFile()
        try {
            val texts = File(pack, "texts").apply { mkdirs() }
            File(texts, "en_US.lang").writeText("pack.name=Fallback pack\npack.description=Fallback description\n")

            assertEquals("Fallback pack", ManifestTextResolver.resolve(pack, "", "vi-VN", "pack.name"))
            assertEquals("Fallback description", ManifestTextResolver.resolve(pack, null, "vi-VN", "pack.description"))
        } finally {
            pack.deleteRecursively()
        }
    }

    @Test
    fun leavesLiteralManifestTextUnchangedWhenNoLanguageKeyMatches() {
        val pack = Files.createTempDirectory("blockline-manifest-literal-").toFile()
        try {
            val texts = File(pack, "texts").apply { mkdirs() }
            File(texts, "en_US.lang").writeText("pack.name=Translated\n")
            assertEquals("A direct pack name", ManifestTextResolver.resolve(pack, "A direct pack name", "en-US"))
        } finally {
            pack.deleteRecursively()
        }
    }
}
