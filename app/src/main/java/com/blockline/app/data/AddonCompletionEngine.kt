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
    data class Request(val path: String, val text: String, val cursor: Int, val documentId: String = path)
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
            val owner = if (dot >= 0) resolveJavaScriptOwner(before.substring(0, dot)) else null
            if (owner != null && js != null) {
                val members = js.optJSONArray(owner).asStrings()
                add(members, "$owner API")
                if (members.isNotEmpty()) {
                    prefix = Regex("[A-Za-z0-9_$]*$").find(before)?.value.orEmpty()
                    replaceStart = cursor - prefix.length
                }
            } else if (dot < 0) {
                add(js?.optJSONArray("globals").asStrings(), "@minecraft/server")
                add(schema.optJSONArray("javascriptKeywords").asStrings(), "JavaScript")
            }
        } else {
            val isManifest = normalizedPath.endsWith("/manifest.json") || normalizedPath == "manifest.json"
            val fileSchema = when {
                isManifest ->
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
                    val keys = if (isManifest) manifestKeysForContext(before, fileSchema)
                    else fileSchema.optJSONArray("keys").asStrings()
                    add(keys, "Bedrock JSON key")
                }
                val values = fileSchema.optJSONObject("values")
                when {
                    isManifest && isCurrentStringValueForKey(before, "uuid") ->
                        add(listOf(uuidFor(request.documentId, before)), "Generated UUID")
                    isManifest && isCurrentNumericValueForKey(before, "format_version") ->
                        add(values?.optJSONArray("format_version").asStrings(), "Manifest format version")
                    isManifest && isCurrentStringValueForKey(before, "type") ->
                        add((values?.optJSONArray("module_type").asStrings().orEmpty() +
                            values?.optJSONArray("type").asStrings().orEmpty()).distinct(), "Manifest module type")
                    !isManifest && isCurrentStringValueForKey(before, "format_version") ->
                        add(values?.optJSONArray("format_version").asStrings(), "Supported format version")
                    !isManifest && isJsonKeyContext(before) && isInsideComponentKeyMap(before) -> {
                        val detail = when {
                            pathContains(normalizedPath, "items") -> "Item component"
                            pathContains(normalizedPath, "blocks") -> "Block component"
                            else -> "Entity component"
                        }
                        add(values?.optJSONArray("components").asStrings(), detail)
                    }
                }
            }
        }
        return Result(replaceStart.coerceIn(0, cursor), cursor, candidates.values
            .sortedWith(compareBy<Suggestion> { it.label.lowercase() }.thenBy { it.label })
            .take(60))
    }

    private fun resolveJavaScriptOwner(expression: String): String? {
        val value = expression.trimEnd()
        return when {
            Regex("""(?:^|\.)getDimension\s*\([^)]*\)\s*$""").containsMatchIn(value) -> "Dimension"
            Regex("""(?:^|\.)getBlock\s*\([^)]*\)\s*$""").containsMatchIn(value) -> "Block"
            Regex("""(?:^|\.)permutation\s*$""").containsMatchIn(value) -> "BlockPermutation"
            else -> Regex("""([A-Za-z_$][A-Za-z0-9_$]*)\s*$""").find(value)?.groupValues?.getOrNull(1)?.let {
                when (it) {
                    "player" -> "Player"
                    "entity" -> "Entity"
                    "dim", "dimension" -> "Dimension"
                    "block" -> "Block"
                    "permutation", "blockPermutation" -> "BlockPermutation"
                    "itemStack", "itemstack", "stack" -> "ItemStack"
                    else -> it
                }
            }
        }
    }

    private fun manifestKeysForContext(before: String, fileSchema: JSONObject): List<String> {
        val contexts = fileSchema.optJSONObject("keysByContext")
            ?: return fileSchema.optJSONArray("keys").asStrings()
        val context = openObjectKeys(before).lastOrNull() ?: "root"
        return contexts.optJSONArray(context).asStrings()
            .ifEmpty { contexts.optJSONArray("root").asStrings() }
    }

    private fun isJsonKeyContext(before: String): Boolean {
        var inString = false
        var escaped = false
        var stringStart = -1
        before.forEachIndexed { index, ch ->
            if (escaped) {
                escaped = false
            } else if (ch.code == 92 && inString) {
                escaped = true
            } else if (ch == '"') {
                if (!inString) stringStart = index
                inString = !inString
            }
        }
        if (inString) {
            val prefix = before.substring(0, stringStart).trimEnd()
            return prefix.endsWith("{") || prefix.endsWith(",")
        }
        val trimmed = before.trimEnd()
        return trimmed.endsWith("{") || trimmed.endsWith(",")
    }

    private fun isInsideComponentKeyMap(before: String): Boolean {
        val keys = openObjectKeys(before)
        return keys.lastOrNull() == "components" ||
            (keys.size >= 2 && keys.lastOrNull() != null &&
                keys[keys.lastIndex - 1] == "component_groups")
    }

    private fun uuidFor(documentId: String, before: String): String {
        val keyStart = before.lastIndexOf("\\"uuid\\"")
        val priorUuidCount = Regex("\\"uuid\\"").findAll(before.substring(0, keyStart.coerceAtLeast(0))).count()
        val identity = documentId.lowercase(java.util.Locale.ROOT) + ":" + priorUuidCount
        return UUID.nameUUIDFromBytes((uuidSuggestion + ":" + identity).toByteArray(Charsets.UTF_8)).toString()
    }

    private fun isCurrentNumericValueForKey(before: String, key: String): Boolean =
        Regex("""\"${Regex.escape(key)}\"\s*:\s*\d*$""").containsMatchIn(before)

    private fun isCurrentStringValueForKey(before: String, key: String): Boolean {
        val pattern = "\"" + Regex.escape(key) + "\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*$"
        return Regex(pattern).containsMatchIn(before)
    }

    private fun openObjectKeys(before: String): List<String?> {
        data class Container(val isArray: Boolean, val key: String?)
        val stack = mutableListOf<Container>()
        var pendingKey: String? = null
        var index = 0
        while (index < before.length) {
            when (before[index]) {
                '"' -> {
                    val start = index + 1
                    index++
                    val value = StringBuilder()
                    var escaped = false
                    var closed = false
                    while (index < before.length) {
                        val ch = before[index]
                        if (escaped) {
                            value.append(ch)
                            escaped = false
                        } else if (ch == '\\') {
                            escaped = true
                        } else if (ch == '"') {
                            closed = true
                            break
                        } else {
                            value.append(ch)
                        }
                        index++
                    }
                    if (!closed) {
                        pendingKey = null
                        break
                    }
                    var next = index + 1
                    while (next < before.length && before[next].isWhitespace()) next++
                    pendingKey = if (next < before.length && before[next] == ':') value.toString() else null
                    index++
                    continue
                }
                '{' -> {
                    val arrayParentKey = stack.lastOrNull()?.takeIf { it.isArray }?.key
                    stack.add(Container(false, pendingKey ?: arrayParentKey))
                    pendingKey = null
                }
                '[' -> {
                    stack.add(Container(true, pendingKey))
                    pendingKey = null
                }
                '}', ']' -> {
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    pendingKey = null
                }
                ',' -> pendingKey = null
                else -> if (!before[index].isWhitespace() && before[index] != ':') pendingKey = null
            }
            index++
        }
        return stack.filterNot { it.isArray }.map { it.key }
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
