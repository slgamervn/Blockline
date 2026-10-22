package com.blockline.app.data

import org.json.JSONArray
import java.io.File
import java.util.Locale

object ManifestTextResolver {
    fun resolve(packDirectory: File, rawValue: String?, preferredLocale: String? = Locale.getDefault().toLanguageTag()): String? {
        val value = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return rawValue
        val textsDirectory = File(packDirectory, "texts")
        if (!textsDirectory.isDirectory) return value

        for (locale in languageCandidates(textsDirectory, preferredLocale)) {
            val file = File(textsDirectory, "$locale.lang")
            if (!file.isFile) continue
            val translated = findTranslation(file, value)
            if (!translated.isNullOrBlank()) return translated
        }
        return value
    }

    private fun languageCandidates(textsDirectory: File, preferredLocale: String?): List<String> {
        val declared = runCatching {
            val languagesFile = File(textsDirectory, "languages.json")
            if (!languagesFile.isFile) emptyList()
            else (org.json.JSONTokener(JsonTools.stripComments(languagesFile.readText(Charsets.UTF_8))).nextValue() as? JSONArray)
                ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
                .orEmpty()
        }.getOrDefault(emptyList())
        val files = textsDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("lang", true) }
            .map { it.nameWithoutExtension }
        val supported = (declared + files + "en_US").distinctBy(::normalizedLocale)
        val requested = normalizedLocale(preferredLocale.orEmpty())
        val language = requested.substringBefore('_')
        return buildList {
            supported.firstOrNull { normalizedLocale(it) == requested }?.let { add(it) }
            if (language.isNotBlank()) {
                supported.filter { normalizedLocale(it).substringBefore('_') == language }.forEach { add(it) }
            }
            supported.firstOrNull { normalizedLocale(it) == "en_us" }?.let { add(it) }
            supported.forEach { add(it) }
        }.distinctBy(::normalizedLocale)
    }

    private fun normalizedLocale(value: String): String =
        value.replace('-', '_').lowercase(Locale.ROOT)

    private fun findTranslation(file: File, key: String): String? = runCatching {
        file.useLines { lines ->
            lines.mapNotNull { rawLine ->
                val line = rawLine.removePrefix("\uFEFF")
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@mapNotNull null
                val equals = line.indexOf('=')
                if (equals <= 0 || line.substring(0, equals).trim() != key) null
                else line.substring(equals + 1).trim().let(::decodeValue)
            }.firstOrNull { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun decodeValue(value: String): String =
        value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
}
