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
          "js":{"globals":["world","system","Player","Entity","Dimension","Block","BlockPermutation","ItemStack"],"world":["getDimension","getPlayers","getAimAssist"],"system":["run","runInterval"],"Player":["name","teleport","getItemCooldown","getAimAssist"],"Entity":["getComponent","getHeadLocation","getBlockStandingOn"],"Dimension":["getBlock","spawnEntity"],"Block":["getLightLevel"],"BlockPermutation":["resolve","withState"],"ItemStack":["getLore","clone"]}
        }
    """.trimIndent()

    private val engine = AddonCompletionEngine.fromSchemaJson(schema, "123e4567-e89b-12d3-a456-426614174000")


    @Test fun suggestsAdditionalServerApiMembers() {
        val worldText = "world.getA"
        val worldResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", worldText, worldText.length))
        assertTrue(worldResult.items.any { it.label == "getAimAssist" })

        val playerText = "player.getA"
        val playerResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", playerText, playerText.length))
        assertTrue(playerResult.items.any { it.label == "getAimAssist" })

        val dimensionText = "dimension.getBl"
        val dimensionResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", dimensionText, dimensionText.length))
        assertTrue(dimensionResult.items.any { it.label == "getBlock" })
        assertTrue(dimensionResult.items.none { it.label == "getWeather" })

        val entityText = "entity.getBlockS"
        val entityResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", entityText, entityText.length))
        assertTrue(entityResult.items.any { it.label == "getBlockStandingOn" })
    }

    @Test fun suggestsBlockPermutationAndItemStackMembers() {
        val blockText = "block.getLi"
        val blockResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", blockText, blockText.length))
        assertTrue(blockResult.items.any { it.label == "getLightLevel" })

        val permutationText = "permutation.res"
        val permutationResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", permutationText, permutationText.length))
        assertTrue(permutationResult.items.any { it.label == "resolve" })

        val stackText = "itemStack.getLo"
        val stackResult = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", stackText, stackText.length))
        assertTrue(stackResult.items.any { it.label == "getLore" })
    }


    @Test fun doesNotSuggestJsonKeysInsideStringValues() {
        val text = "{\"description\":\"vers"
        val result = engine.suggest(AddonCompletionEngine.Request("manifest.json", text, text.length))
        assertTrue(result.items.none { it.detail == "Bedrock JSON key" })
        assertTrue(result.items.none { it.label == "version" })
    }

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

    @Test fun suggestsEntityMembersFromEntityApiRatherThanPlayerOnlyApi() {
        val text = "entity.get"
        val result = engine.suggest(AddonCompletionEngine.Request("scripts/main.js", text, text.length))
        assertTrue(result.items.any { it.label == "getComponent" })
        assertTrue(result.items.any { it.label == "getHeadLocation" })
        assertTrue(result.items.none { it.label == "getItemCooldown" })
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
        assertTrue(afterType.items.any { it.detail == "Generated UUID" })
        assertTrue(afterType.items.first { it.detail == "Generated UUID" }.label != first.items.first { it.detail == "Generated UUID" }.label)
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

    @Test fun doesNotSuggestComponentsOutsideComponentsObject() {
        val text = "{\"components\":{},\"description\":\"minecraft:he"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/entities/zombie.json", text, text.length))
        assertTrue(result.items.none { it.detail == "Entity component" })
    }

    @Test fun suggestsItemComponentsInItemFiles() {
        val text = "{\"components\":{\"minecraft:ma"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/items/custom_item.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:max_stack_size" && it.detail == "Item component" })
        assertTrue(result.items.none { it.label == "minecraft:icon" })
    }


    @Test fun doesNotSuggestComponentsInsideComponentValueObject() {
        val text = "{\"components\":{\"minecraft:health\":{\"value\":1,\"minecraft:he"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/entities/zombie.json", text, text.length))
        assertTrue(result.items.none { it.detail == "Entity component" })
    }

    @Test fun suggestsComponentsInsideComponentGroups() {
        val text = "{\"component_groups\":{\"minecraft:angry\":{\"minecraft:he"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/entities/zombie.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:health" && it.detail == "Entity component" })
    }

    @Test fun doesNotSuggestManifestTypesOutsideTypeValue() {
        val text = "\"type\": \"data\", \"description\": \"scr"
        val result = engine.suggest(AddonCompletionEngine.Request("manifest.json", text, text.length))
        assertTrue(result.items.none { it.detail == "Manifest module type" })
    }


    @Test fun suggestsManifestFormatVersionAsNumber() {
        val text = """{"format_version": """
        val result = engine.suggest(AddonCompletionEngine.Request("manifest.json", text, text.length))
        assertTrue(result.items.any { it.label == "2" && it.detail == "Manifest format version" })
    }

    @Test fun doesNotSuggestFormatVersionsOutsideFormatVersionValue() {
        val text = "\"format_version\": \"1.21.0\", \"description\": \"1.21"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/entities/zombie.json", text, text.length))
        assertTrue(result.items.none { it.detail == "Supported format version" })
    }

    @Test fun suggestsBlockComponentsInBlockFiles() {
        val text = "{\"components\":{\"minecraft:ge"
        val result = engine.suggest(AddonCompletionEngine.Request("BP/blocks/custom_block.json", text, text.length))
        assertTrue(result.items.any { it.label == "minecraft:geometry" && it.detail == "Block component" })
        assertTrue(result.items.none { it.label == "minecraft:material_instances" })
    }
}
