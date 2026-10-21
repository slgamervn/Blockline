package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun comparesNumericVersionSegmentsInsteadOfLexicographicText() {
        assertTrue(AppUpdateRepository.compareVersions("0.1.332", "0.1.99") > 0)
        assertTrue(AppUpdateRepository.compareVersions("0.2.0", "0.1.999") > 0)
        assertTrue(AppUpdateRepository.compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, AppUpdateRepository.compareVersions("0.1.332", "0.1.332"))
    }

    @Test
    fun stableReleaseIsNewerThanSameNumericPrerelease() {
        assertTrue(AppUpdateRepository.compareVersions("1.0.0", "1.0.0-rc.1") > 0)
        assertTrue(AppUpdateRepository.compareVersions("1.0.0-beta.2", "1.0.0-beta.1") > 0)
    }

    @Test
    fun validatesReleaseVersionShape() {
        assertTrue(AppUpdateRepository.isValidVersion("0.1.332"))
        assertTrue(AppUpdateRepository.isValidVersion("1.2.3-rc.1"))
        assertFalse(AppUpdateRepository.isValidVersion("latest"))
        assertFalse(AppUpdateRepository.isValidVersion("1.2"))
    }
}
