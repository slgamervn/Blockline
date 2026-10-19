package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

enum class ResourceKind { MANIFEST, ENTITY, ITEM, BLOCK, RECIPE, LOOT_TABLE, FUNCTION, SCRIPT, LANGUAGE, JSON }

data class ResourcePreview(
    val kind: ResourceKind,
    val bytes: Long,
    val lineCount: Int,
    val metadata: List<Pair<String, String>>,
    val source: String,
    val isTruncated: Boolean
)

object ResourcePreviewBuilder {
    private const val MAX_SOURCE_CHARS = 32_000
    private val interestingKeys = setOf(
        "format_version", "identifier", "type", "parent", "texture", "textures",
        "geometry", "materials", "render_controllers", "max_stack_size", "category",
        "components", "minecraft:entity", "minecraft:item", "minecraft:block",
        "minecraft:recipe_shaped", "minecraft:recipe_shapeless", "minecraft:recipe_furnace",
        "pools", "entries", "rolls", "functions", "result", "ingredients", "description",
        "uuid", "version", "modules", "dependencies", "min_engine_version"
    )

    fun build(path: String, source: String): ResourcePreview {
        val kind = classify(path)
        val metadata = when (kind) {
            ResourceKind.JSON, ResourceKind.MANIFEST, ResourceKind.ENTITY, ResourceKind.ITEM,
            ResourceKind.BLOCK, ResourceKind.RECIPE, ResourceKind.LOOT_TABLE -> parseFields(source)
            ResourceKind.LANGUAGE -> parseLanguage(source)
            else -> emptyList()
        }
        val bytes = source.toByteArray(Charsets.UTF_8).size.toLong()
        return ResourcePreview(
            kind = kind,
            bytes = bytes,
            lineCount = if (source.isEmpty()) 0 else source.count { it == '\n' } + if (source.endsWith("\n")) 0 else 1,
            metadata = metadata,
            source = source.take(MAX_SOURCE_CHARS),
            isTruncated = source.length > MAX_SOURCE_CHARS
        )
    }

    private fun classify(path: String): ResourceKind {
        val parts = path.lowercase(Locale.ROOT).split('/')
        val name = parts.lastOrNull().orEmpty()
        val ext = name.substringAfterLast('.', "")
        return when {
            name == "manifest.json" || name == "pack_manifest.json" -> ResourceKind.MANIFEST
            ext == "mcfunction" || parts.contains("functions") -> ResourceKind.FUNCTION
            ext == "js" || ext == "mjs" || ext == "ts" -> ResourceKind.SCRIPT
            ext == "lang" || ext == "properties" || parts.contains("texts") -> ResourceKind.LANGUAGE
            parts.any { it in setOf("entities", "entity") } -> ResourceKind.ENTITY
            parts.any { it in setOf("items", "item") } -> ResourceKind.ITEM
            parts.any { it in setOf("blocks", "block") } -> ResourceKind.BLOCK
            parts.any { it in setOf("recipes", "recipe") } -> ResourceKind.RECIPE
            parts.any { it in setOf("loot_tables", "loot_table") } -> ResourceKind.LOOT_TABLE
            else -> ResourceKind.JSON
        }
    }

    private fun parseFields(source: String): List<Pair<String, String>> = runCatching {
        var clean = JsonTools.stripComments(source)
        if (clean.startsWith('\uFEFF')) clean = clean.substring(1)
        val root = JSONTokener(clean).nextValue()
        val fields = linkedMapOf<String, String>()
        fun visit(value: Any?, depth: Int) {
            if (depth > 10 || fields.size >= 14) return
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext() && fields.size < 14) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (key.lowercase(Locale.ROOT) in interestingKeys) fields.putIfAbsent(key, compact(child))
                        if (child is JSONObject || child is JSONArray) visit(child, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until minOf(value.length(), 100)) {
                    if (fields.size >= 14) break
                    val child = value.opt(i)
                    if (child is JSONObject || child is JSONArray) visit(child, depth + 1)
                }
            }
        }
        visit(root, 0)
        fields.entries.map { it.key to it.value }
    }.getOrDefault(emptyList())

    private fun parseLanguage(source: String): List<Pair<String, String>> = source.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") && '=' in it }
        .take(12)
        .map { line ->
            val split = line.indexOf('=')
            line.substring(0, split).trim() to line.substring(split + 1).trim().take(180)
        }
        .toList()

    private fun compact(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> "Object (${value.length()} fields)"
        is JSONArray -> "Array (${value.length()} items)"
        else -> value.toString().take(220)
    }
}
