package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VanillaResourceIndexTest {
    @Test
    fun extractsTexturePathsFromOfficialAndArchivedPackTrees() {
        val source = """{
          "truncated":false,
          "tree":[
            {"path":"resource_pack/textures/entity/official.png","type":"blob"},
            {"path":"stable/resource/textures/entity/endercrystal/endercrystal.png","type":"blob"},
            {"path":"beta/resource/textures/entity/pig/pigzombie.png","type":"blob"},
            {"path":"stable/resource/entity/ender_crystal.entity.json","type":"blob"},
            {"path":"stable/resource/textures/dir","type":"tree"}
          ]
        }"""
        val paths = VanillaResourceIndex.extractTexturePaths(source)
        assertEquals(3, paths.size)
        assertTrue(paths.contains("textures/entity/official.png"))
        assertTrue(paths.contains("textures/entity/endercrystal/endercrystal.png"))
        assertTrue(paths.contains("textures/entity/pig/pigzombie.png"))
    }

    @Test
    fun rejectsTruncatedTreeResponse() {
        try {
            VanillaResourceIndex.extractTexturePaths("""{"truncated":true,"tree":[]}""")
            throw AssertionError("A truncated tree must not be trusted as a complete index.")
        } catch (_: IllegalArgumentException) {
        }
    }
}

