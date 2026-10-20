package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class GeneratedPackType(val folder: String, private val moduleType: String) {
    BEHAVIOR("BP", "data"),
    RESOURCE("RP", "resources");

    internal fun moduleTypeValue(): String = moduleType
}

object PackManifestGenerator {
    fun generate(name: String, description: String, type: GeneratedPackType): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Pack name cannot be empty." }
        require(cleanName.length <= 120) { "Pack name must be 120 characters or fewer." }

        val cleanDescription = description.trim().ifBlank { "Generated with Blockline" }.take(500)
        val header = JSONObject()
            .put("name", cleanName)
            .put("description", cleanDescription)
            .put("uuid", UUID.randomUUID().toString())
            .put("version", intArray(1, 0, 0))
            .put("min_engine_version", intArray(1, 20, 0))
        val module = JSONObject()
            .put("type", type.moduleTypeValue())
            .put("uuid", UUID.randomUUID().toString())
            .put("version", intArray(1, 0, 0))
        return JSONObject()
            .put("format_version", 2)
            .put("header", header)
            .put("modules", JSONArray().put(module))
            .toString(2) + "\n"
    }

    private fun intArray(vararg values: Int): JSONArray =
        JSONArray().apply { values.forEach(::put) }
}
