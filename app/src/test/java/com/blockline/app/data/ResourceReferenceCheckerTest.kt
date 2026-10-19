package com.blockline.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceReferenceCheckerTest {
    @Test
    fun warnsWhenExplicitTexturePathIsMissing() {
        val root = createTempDir(prefix = "blockline-texture-missing-")
        try {
            val entityDir = File(root, "entities").apply { mkdirs() }
            val entity = File(entityDir, "client_entity.json")
            entity.writeText("""{"minecraft:client_entity":{"description":{"textures":{"default":"textures/entity/missing"}}}}""")

            val issues = ResourceReferenceChecker.check(root, listOf(entity))

            assertEquals(1, issues.size)
            assertTrue(issues.single().message.contains("textures/entity/missing"))
            assertEquals("entities/client_entity.json", issues.single().path)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsExistingTextureAndIgnoresAliases() {
        val root = createTempDir(prefix = "blockline-texture-existing-")
        try {
            val entityDir = File(root, "entities").apply { mkdirs() }
            val texture = File(root, "textures/entity").apply { mkdirs() }.resolve("mob.png")
            texture.writeBytes(byteArrayOf(1, 2, 3))
            val entity = File(entityDir, "client_entity.json")
            entity.writeText("""{"description":{"textures":{"default":"textures/entity/mob","alias":"mob_alias"}}}""")

            val issues = ResourceReferenceChecker.check(root, listOf(entity, texture))

            assertTrue(issues.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolvesTexturesRelativeToNestedPackRoot() {
        val root = createTempDir(prefix = "blockline-nested-pack-")
        try {
            val pack = File(root, "RP").apply { mkdirs() }
            val manifest = File(pack, "manifest.json").apply { writeText("""{"header":{"name":"RP"}}""") }
            val entity = File(pack, "entity/client_entity.json").apply {
                parentFile?.mkdirs()
                writeText("""{"description":{"textures":{"default":"textures/entity/mob"}}}""")
            }
            val texture = File(pack, "textures/entity").apply { mkdirs() }.resolve("mob.png")
            texture.writeBytes(byteArrayOf(1, 2, 3))

            val issues = ResourceReferenceChecker.check(root, listOf(manifest, entity, texture))

            assertTrue("Valid texture inside RP should not be reported missing: $issues", issues.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
