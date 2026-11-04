package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun stableReleaseHasHigherPrecedenceThanItsPrereleases() {
        assertTrue(SemanticVersion.compare("2.11.0", "2.11.0-rc.1.26.70-preview.23") > 0)
        assertTrue(SemanticVersion.compare("2.11.0", "2.11.0-beta.1.26.60-stable") > 0)
        assertTrue(SemanticVersion.isStable("2.11.0"))
        assertFalse(SemanticVersion.isStable("2.11.0-rc.1"))
    }

    @Test
    fun comparesPrereleaseIdentifiersNumerically() {
        assertTrue(SemanticVersion.compare("2.12.0-rc.10", "2.12.0-rc.2") > 0)
        assertTrue(SemanticVersion.compare("2.12.0-rc.2", "2.12.0-rc.10") < 0)
    }

    @Test
    fun comparesCoreVersionsBeforePrereleaseLabels() {
        assertTrue(SemanticVersion.compare("2.13.0-beta.1", "2.12.0-rc.99") > 0)
        assertEquals(0, SemanticVersion.compare("2.11.0+build.2", "2.11.0+build.9"))
    }

    @Test
    fun validatesNpmPreviewVersionFormat() {
        assertTrue(SemanticVersion.isValid("2.13.0-beta.1.26.70-preview.23"))
        assertTrue(SemanticVersion.isValid("2.12.0-rc.1.26.70-preview.23"))
        assertFalse(SemanticVersion.isValid("02.11.0"))
    }
}
