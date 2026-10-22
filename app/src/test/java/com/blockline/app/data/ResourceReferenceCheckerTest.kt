package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceReferenceCheckerTest {
    @Test
    fun reportsUnbundledTextureAsInformationalInsteadOfFalseWarning() {
        val root = Files.createTempDirectory("blockline-texture-missing-").toFile()
        try {
            val entityDir = File(root, "entities").apply { mkdirs() }
            val entity = File(entityDir, "client_entity.json")
            entity.writeText("""{"minecraft:client_entity":{"description":{"textures":{"default":"textures/entity/missing"}}}}""")

            val issues = ResourceReferenceChecker.check(root, listOf(entity))

            assertEquals(1, issues.size)
            assertTrue(issues.single().message.contains("textures/entity/missing"))
            assertEquals(IssueSeverity.INFO, issues.single().severity)
            assertEquals("entities/client_entity.json", issues.single().path)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun knownVanillaTextureIsNotReportedMissing() {
        val root = Files.createTempDirectory("blockline-vanilla-texture-").toFile()
        try {
            val entity = File(root, "entity/client_entity.json").apply {
                parentFile?.mkdirs()
                writeText("""{"description":{"textures":{"default":"textures/entity/endercrystal/endercrystal"}}}""")
            }
            val issue = ResourceReferenceChecker.check(
                root,
                listOf(entity),
                setOf("textures/entity/endercrystal/endercrystal.png")
            )
            assertTrue("Known vanilla texture should resolve without an issue: $issue", issue.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsExistingTextureAndIgnoresAliases() {
        val root = Files.createTempDirectory("blockline-texture-existing-").toFile()
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
    fun acceptsExistingTgaTextureWithoutReportingItMissing() {
        val root = Files.createTempDirectory("blockline-tga-texture-").toFile()
        try {
            val entity = File(root, "entity/client_entity.json").apply {
                parentFile?.mkdirs()
                writeText("""{"description":{"textures":{"default":"textures/entity/skin"}}}""")
            }
            val texture = File(root, "textures/entity").apply { mkdirs() }.resolve("skin.tga")
            texture.writeBytes(byteArrayOf(1, 2, 3))

            val issues = ResourceReferenceChecker.check(root, listOf(entity, texture))

            assertTrue("Existing TGA texture should be accepted: $issues", issues.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotResolveTextureFromAnotherPackWithTheSameRelativePath() {
        val root = Files.createTempDirectory("blockline-isolated-pack-texture-").toFile()
        try {
            val firstPack = File(root, "RP1").apply { mkdirs() }
            val firstManifest = File(firstPack, "manifest.json").apply { writeText("""{"header":{"name":"RP1"}}""") }
            val entity = File(firstPack, "entity/client_entity.json").apply {
                parentFile?.mkdirs()
                writeText("""{"description":{"textures":{"default":"textures/entity/mob"}}}""")
            }
            val secondPack = File(root, "RP2").apply { mkdirs() }
            val secondManifest = File(secondPack, "manifest.json").apply { writeText("""{"header":{"name":"RP2"}}""") }
            val otherPackTexture = File(secondPack, "textures/entity").apply { mkdirs() }.resolve("mob.png")
            otherPackTexture.writeBytes(byteArrayOf(1, 2, 3))

            val issues = ResourceReferenceChecker.check(root, listOf(firstManifest, entity, secondManifest, otherPackTexture))

            assertEquals(1, issues.size)
            assertEquals(IssueSeverity.INFO, issues.single().severity)
            assertTrue(issues.single().message.contains("textures/entity/mob"))
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun resolvesTexturesRelativeToNestedPackRoot() {
        val root = Files.createTempDirectory("blockline-nested-pack-").toFile()
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
