package com.blockline.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccentColorTest {
    @Test
    fun everyAccentHasDistinctLightAndDarkPrimaries() {
        AccentColor.values().forEach { accent ->
            assertNotEquals(accent.primary(dark = true), accent.primary(dark = false))
            assertEquals(Color.White, accent.onPrimary(dark = false))
        }
    }

    @Test
    fun emeraldMatchesBlocklineBrandPalette() {
        assertEquals(Color(0xFF3DBA7A), AccentColor.EMERALD.primary(dark = true))
        assertEquals(Color(0xFF267F45), AccentColor.EMERALD.primary(dark = false))
        assertEquals(Color(0xFF062315), AccentColor.EMERALD.onPrimary(dark = true))
    }
}
