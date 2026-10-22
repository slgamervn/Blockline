package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonCompletionEngineTest {
    private val schema = """
        {
          "javascriptKeywords":["const","function"],
          "files":{
            "manifest.json":{"keys":["format_version","header","modules","uuid"],"values":{"module_type":["script","data"]}},
            "entities/":{"keys":["minecraft:entity","components","description"],"values":{"components":["minecraft:health","minecraft:movement"],"format_version":["1.21.0"]}},
            "items/":{"keys":["minecraft:item","components"],"values":{"format_version":["1.21.0"]}},
            "blocks/":{"keys":["minecraft:block","components"],"values":{"format_version":["1.21.0"]}}
          },
          "js":{"globals":["world","system","Player"],"world":["getDimension","getPlayers"],"system":["run","runInterval"],"Player":["name","teleport"],"Dimension":["getBlock","spawnEntity"]}
        }
    """.trimIndent()

    private val engine = AddonCompletionEngine.fromSchemaJson(schema, "123e4567-e89b-12d3-a456-426614174000")

    @Test fun detectsEntityPathAndFiltersPrefix() {
        val text = "{\"minecraft:en"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/entities/zombie.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:entity" })
        assertTrue(result.items.all { it.label.startsWith("minecraft:en", ignoreCase = true) })
        assertEquals(text.length - "minecraft:en".length, result.start)
    }

    @Test fun suggestsJavaScriptGlobals() {
        val text = "wo"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/scripts/main.js", text, text.length))
        assertTrue(result.items.any { it.label == "world" })
    }

    @Test fun suggestsMembersAfterDot() {
        val text = "world.getD"
        val result = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", text, text.length))
        assertTrue(result.items.any { it.label == "getDimension" })
        assertTrue(result.items.none { it.label == "getPlayers" })
        assertEquals(text.length - "getD".length, result.start)
    }

    @Test fun suggestsManifestModuleTypesAndStableUuid() {
        val moduleText = "\"type\": \"scr"
        val modules = engine.suggest(AddonCompletionEngine.Request("manifest.json", moduleText, moduleText.length))
        assertTrue(modules.items.any { it.label == "script" })
        val uuidText = "\"uuid\": \""
        val first = engine.suggest(AddonCompletionEngine.Request("manifest.json", uuidText, uuidText.length))
        val second = engine.suggest(AddonCompletionEngine.Request("manifest.json", uuidText, uuidText.length))
        assertEquals("123e4567-e89b-12d3-a456-426614174000", first.items.first { it.detail == "Generated UUID" }.label)
        assertEquals(first.items.map { it.label }, second.items.map { it.label })
    }
}
