package com.blockline.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackManifestGeneratorTest {
    @Test
    fun behaviorManifestHasFreshUuidAndDataModule() {
        val json = JSONObject(PackManifestGenerator.generate("My Behavior Pack", "Test description", GeneratedPackType.BEHAVIOR))
        val header = json.getJSONObject("header")
        val module = json.getJSONArray("modules").getJSONObject(0)

        assertEquals(2, json.getInt("format_version"))
        assertEquals("My Behavior Pack", header.getString("name"))
        assertEquals("Test description", header.getString("description"))
        assertEquals("data", module.getString("type"))
        assertTrue(UUID_PATTERN.matches(header.getString("uuid")))
        assertTrue(UUID_PATTERN.matches(module.getString("uuid")))
        assertNotEquals(header.getString("uuid"), module.getString("uuid"))
        assertEquals(3, header.getJSONArray("version").length())
        assertEquals(3, header.getJSONArray("min_engine_version").length())
    }

    @Test
    fun resourceManifestUsesResourceModuleAndEscapesText() {
        val name = "My \"Quoted\" Resource Pack"
        val description = "Line one\nLine two"
        val json = JSONObject(PackManifestGenerator.generate(name, description, GeneratedPackType.RESOURCE))
        val header = json.getJSONObject("header")
        val module = json.getJSONArray("modules").getJSONObject(0)

        assertEquals(name, header.getString("name"))
        assertEquals(description, header.getString("description"))
        assertEquals("resources", module.getString("type"))
    }

    @Test
    fun blankNameIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PackManifestGenerator.generate("   ", "", GeneratedPackType.BEHAVIOR)
        }
    }

    companion object {
        private val UUID_PATTERN = Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
