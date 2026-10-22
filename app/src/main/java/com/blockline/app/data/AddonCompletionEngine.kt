package com.blockline.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Schema-backed completion engine for Minecraft Bedrock add-on files. */
class AddonCompletionEngine private constructor(
    private val schema: JSONObject,
    private val uuidSuggestion: String
) {
    data class Suggestion(val label: String, val insertText: String = label, val detail: String = "")
    data class Request(val path: String, val text: String, val cursor: Int)
    data class Result(val start: Int, val end: Int, val items: List<Suggestion>)

    fun suggest(request: Request): Result {
        val text = request.text
        val cursor = request.cursor.coerceIn(0, text.length)
        val before = text.substring(0, cursor)
        val normalizedPath = request.path.replace('\\', '/').lowercase()
        val isJavaScript = normalizedPath.endsWith(".js") || normalizedPath.endsWith(".mjs")
        val token = Regex("[A-Za-z0-9_$:.-]*$").find(before)?.value.orEmpty()
        var replaceStart = cursor - token.length
        var prefix = token
        val jsDot = if (isJavaScript) before.lastIndexOf('.') else -1
        if (jsDot >= 0) {
            prefix = Regex("[A-Za-z0-9_$]*$").find(before)?.value.orEmpty()
            replaceStart = cursor - prefix.length
        }
        val candidates = linkedMapOf<String, Suggestion>()

        fun add(values: Iterable<String>, detail: String) {
            values.forEach { value ->
                if (value.isNotBlank() && value.startsWith(prefix, ignoreCase = true)) {
                    candidates.putIfAbsent(value, Suggestion(value, value, detail))
                }
            }
        }

        if (isJavaScript) {
            val js = schema.optJSONObject("js")
            val dot = before.lastIndexOf('.')
            val owner = if (dot >= 0) {
                val ownerMatch = Regex("([A-Za-z_$][A-Za-z0-9_$]*)\\s*$").find(before.substring(0, dot))
                ownerMatch?.groupValues?.getOrNull(1)?.let {
                    when (it) {
                        "player", "entity" -> "Player"
                        "dim", "dimension" -> "Dimension"
                        else -> it
                    }
                }
            } else null
            if (owner != null && js != null) {
                val members = js.optJSONArray(owner).asStrings()
                add(members, "$owner API")
                if (members.isNotEmpty()) {
                    prefix = Regex("[A-Za-z0-9_$]*$").find(before)?.value.orEmpty()
                    replaceStart = cursor - prefix.length
                }
            } else {
                add(js?.optJSONArray("globals").asStrings(), "@minecraft/server")
                add(schema.optJSONArray("javascriptKeywords").asStrings(), "JavaScript")
            }
        } else {
            val fileSchema = when {
                normalizedPath.endsWith("/manifest.json") || normalizedPath == "manifest.json" ->
                    schema.optJSONObject("files")?.optJSONObject("manifest.json")
                pathContains(normalizedPath, "entities") ->
                    schema.optJSONObject("files")?.optJSONObject("entities/")
                pathContains(normalizedPath, "items") ->
                    schema.optJSONObject("files")?.optJSONObject("items/")
                pathContains(normalizedPath, "blocks") ->
                    schema.optJSONObject("files")?.optJSONObject("blocks/")
                else -> null
            }
            if (fileSchema != null) {
                val isLikelyKeyPosition = isJsonKeyContext(before)
                if (isLikelyKeyPosition) {
                    add(fileSchema.optJSONArray("keys").asStrings(), "Bedrock JSON key")
                }
                val values = fileSchema.optJSONObject("values")
                when {
                    normalizedPath.endsWith("manifest.json") &&
                        Regex("\"uuid\"\\s*:\\s*\"?[^\"]*$").containsMatchIn(before) ->
                        add(listOf(uuidSuggestion), "Generated UUID")
                    before.contains("\"components\"") || before.contains("\"component_groups\"") -> {
                        val detail = when {
                            pathContains(normalizedPath, "items") -> "Item component"
                            pathContains(normalizedPath, "blocks") -> "Block component"
                            else -> "Entity component"
                        }
                        add(values?.optJSONArray("components").asStrings(), detail)
                    }
                    before.contains("\"module_type\"") || before.contains("\"type\"") ->
                        add((values?.optJSONArray("module_type").asStrings().orEmpty() + values?.optJSONArray("type").asStrings().orEmpty()).distinct(), "Manifest module type")
                    before.contains("\"format_version\"") ->
                        add(values?.optJSONArray("format_version").asStrings(), "Supported format version")
                }
            }
        }
        return Result(replaceStart.coerceIn(0, cursor), cursor, candidates.values
            .sortedWith(compareBy<Suggestion> { it.label.lowercase() }.thenBy { it.label })
            .take(60))
    }

    private fun isJsonKeyContext(before: String): Boolean {
        var inString = false
        var escaped = false
        var lastColonOutsideString = -1
        var lastQuoteStart = -1
        before.forEachIndexed { index, ch ->
            if (escaped) {
                escaped = false
            } else if (ch.code == 92 && inString) {
                escaped = true
            } else if (ch == '"') {
                if (!inString) lastQuoteStart = index
                inString = !inString
            } else if (ch == ':' && !inString) {
                lastColonOutsideString = index
            }
        }
        if (inString) return lastColonOutsideString < lastQuoteStart
        val trimmed = before.trimEnd()
        return trimmed.endsWith("{") || trimmed.endsWith(",")
    }

    private fun pathContains(path: String, folder: String): Boolean =
        path.startsWith("$folder/") || path.contains("/$folder/")

    private fun JSONArray?.asStrings(): List<String> =
        this?.let { array -> (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        } } ?: emptyList()

    companion object {
        fun load(context: Context): AddonCompletionEngine {
            val json = context.assets.open("addon_completion_schema.json")
                .bufferedReader().use { it.readText() }
            return fromSchemaJson(json)
        }

        internal fun fromSchemaJson(json: String, uuid: String = UUID.randomUUID().toString()): AddonCompletionEngine =
            AddonCompletionEngine(JSONObject(json), uuid)
    }
}
