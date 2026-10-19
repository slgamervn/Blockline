package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcePreviewTest {
    @Test
    fun identifiesManifestAndExtractsMetadataFromCommentedJson() {
        val source = """
            // Bedrock accepts comments in pack JSON.
            {
              "format_version": "1.20.0",
              "minecraft:entity": {
                "description": {
                  "identifier": "demo:bee",
                  "is_spawnable": true
                }
              }
            }
        """.trimIndent()

        val preview = ResourcePreviewBuilder.build("BP/manifest.json", source)

        assertEquals(ResourceKind.MANIFEST, preview.kind)
        assertTrue(preview.metadata.any { it.first == "format_version" && it.second == "1.20.0" })
        assertTrue(preview.metadata.any { it.first == "identifier" && it.second == "demo:bee" })
        assertEquals(source, preview.source)
        assertTrue(!preview.isTruncated)
    }

    @Test
    fun classifiesBedrockResourceFolders() {
        assertEquals(ResourceKind.ENTITY, ResourcePreviewBuilder.build("BP/entities/bee.json", "{}").kind)
        assertEquals(ResourceKind.ITEM, ResourcePreviewBuilder.build("BP/items/hammer.json", "{}").kind)
        assertEquals(ResourceKind.BLOCK, ResourcePreviewBuilder.build("BP/blocks/marble.json", "{}").kind)
        assertEquals(ResourceKind.RECIPE, ResourcePreviewBuilder.build("BP/recipes/hammer.json", "{}").kind)
        assertEquals(ResourceKind.LOOT_TABLE, ResourcePreviewBuilder.build("BP/loot_tables/chests/rare.json", "{}").kind)
        assertEquals(ResourceKind.FUNCTION, ResourcePreviewBuilder.build("BP/functions/start.mcfunction", "say hi").kind)
        assertEquals(ResourceKind.SCRIPT, ResourcePreviewBuilder.build("BP/scripts/main.js", "console.log('hi')").kind)
        assertEquals(ResourceKind.LANGUAGE, ResourcePreviewBuilder.build("RP/texts/en_US.lang", "key=value").kind)
    }

    @Test
    fun readsLanguageKeysWithoutTreatingCommentsAsEntries() {
        val preview = ResourcePreviewBuilder.build(
            "RP/texts/en_US.lang",
            "# Comment\nitem.demo:hammer=Hammer\n// another comment\nentity.demo:bee.name=Bee\n"
        )

        assertEquals(
            listOf("item.demo:hammer" to "Hammer", "entity.demo:bee.name" to "Bee"),
            preview.metadata
        )
        assertEquals(4, preview.lineCount)
    }

    @Test
    fun truncatesVeryLongPreviewWithoutLosingOriginalFileSize() {
        val source = "x".repeat(32_001)
        val preview = ResourcePreviewBuilder.build("BP/scripts/main.js", source)

        assertEquals(32_001L, preview.bytes)
        assertEquals(32_000, preview.source.length)
        assertTrue(preview.isTruncated)
    }
}
