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
            "items/":{"keys":["minecraft:item","components"],"values":{"components":["minecraft:icon","minecraft:max_stack_size"],"format_version":["1.21.0"]}},
            "blocks/":{"keys":["minecraft:block","components"],"values":{"components":["minecraft:geometry","minecraft:material_instances"],"format_version":["1.21.0"]}}
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
        val uuidAfterType = "\"type\": \"data\", \"uuid\": \""
        val afterType = engine.suggest(AddonCompletionEngine.Request("manifest.json", uuidAfterType, uuidAfterType.length))
        assertTrue(afterType.items.any { it.detail == "Generated UUID" && it.label == "123e4567-e89b-12d3-a456-426614174000" })
        assertEquals("123e4567-e89b-12d3-a456-426614174000", first.items.first { it.detail == "Generated UUID" }.label)
        assertEquals(first.items.map { it.label }, second.items.map { it.label })
    }

    @Test fun recognizesWindowsPathsAndItemFiles() {
        val text = "{\"minecraft:i"
        val result = engine.suggest(AddonCompletionEngine.Request("BP\\items\\custom_item.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:item" })
        assertTrue(result.items.all { it.label.startsWith("minecraft:i", ignoreCase = true) })
    }

    @Test fun cursorIsClampedAndEmptyInputSuggestsKeys() {
        val text = "{"
        val result = engine.suggest(AddonCompletionEngine.Request("manifest.json", text, text.length + 20))
        assertEquals(text.length, result.end)
        assertTrue(result.items.any { it.label == "format_version" })
    }

    @Test fun suggestsEntityComponentsInsideComponentsObject() {
        val text = "{\"components\":{\"minecraft:he"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/entities/zombie.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:health" && it.detail == "Entity component" })
        assertTrue(result.items.none { it.label == "minecraft:movement" })
    }

    @Test fun suggestsItemComponentsInItemFiles() {
        val text = "{\"components\":{\"minecraft:ma"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/items/custom_item.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:max_stack_size" && it.detail == "Item component" })
        assertTrue(result.items.none { it.label == "minecraft:icon" })
    }

    @Test fun suggestsBlockComponentsInBlockFiles() {
        val text = "{\"components\":{\"minecraft:ge"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/blocks/custom_block.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:geometry" && it.detail == "Block component" })
        assertTrue(result.items.none { it.label == "minecraft:material_instances" })
    }
}
