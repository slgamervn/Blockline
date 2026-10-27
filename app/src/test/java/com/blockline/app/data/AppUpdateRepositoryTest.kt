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
    fun comparesPrereleaseIdentifiersAccordingToSemanticVersioning() {
        assertTrue(AppUpdateRepository.compareVersions("1.0.0-beta.11", "1.0.0-beta.2") > 0)
        assertTrue(AppUpdateRepository.compareVersions("1.0.0-1", "1.0.0-alpha") < 0)
        assertTrue(AppUpdateRepository.compareVersions("1.0.0-alpha", "1.0.0-alpha.1") < 0)
        assertEquals(0, AppUpdateRepository.compareVersions("1.0.0-rc.1+build.1", "1.0.0-rc.1+build.2"))
        assertEquals(0, AppUpdateRepository.compareVersions("1.0.0+build.1", "1.0.0"))
    }

    @Test
    fun validatesReleaseVersionShape() {
        assertTrue(AppUpdateRepository.isValidVersion("0.1.332"))
        assertTrue(AppUpdateRepository.isValidVersion("1.2.3-rc.1"))
        assertTrue(AppUpdateRepository.isValidVersion("1.2.3-rc.1+build.5"))
        assertFalse(AppUpdateRepository.isValidVersion("latest"))
        assertFalse(AppUpdateRepository.isValidVersion("1.2"))
        assertFalse(AppUpdateRepository.isValidVersion("1.02.3"))
        assertFalse(AppUpdateRepository.isValidVersion("1.2.3-01"))
        assertFalse(AppUpdateRepository.isValidVersion("1.2.3-alpha..1"))
    }

    @Test
    fun updatePromptOpensOfficialLatestStablePageOrMatchingTestRelease() {
        assertEquals(
            "https://github.com/slgamervn/Blockline/releases/latest",
            AppUpdateRepository.updatePromptUrl("stable", "https://github.com/slgamervn/Blockline/releases/tag/v9.9.9")
        )
        assertEquals(
            "https://github.com/slgamervn/Blockline/releases/tag/v0.1.111-test",
            AppUpdateRepository.updatePromptUrl("test", "https://github.com/slgamervn/Blockline/releases/tag/v0.1.111-test")
        )
        try {
            AppUpdateRepository.updatePromptUrl("test", "https://example.com/fake-release")
            throw AssertionError("Unexpected release URL should be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected: update links must stay on the official repository.
        }
    }

    @Test
    fun testChannelUsesItsOwnEndpointAndFiltersOnlyTestPrereleases() {
        assertTrue(AppUpdateRepository.endpointForChannel("test").contains("/releases?per_page="))
        assertTrue(AppUpdateRepository.endpointForChannel("stable").endsWith("/releases/latest"))
        assertTrue(AppUpdateRepository.isTestRelease("v0.1.101-test", prerelease = true, draft = false))
        assertFalse(AppUpdateRepository.isTestRelease("v0.1.101", prerelease = false, draft = false))
        assertFalse(AppUpdateRepository.isTestRelease("v0.1.101-beta", prerelease = true, draft = false))
        assertFalse(AppUpdateRepository.isTestRelease("v0.1.101-test", prerelease = true, draft = true))
    }

    @Test
    fun selectsNewestTestPrereleaseWithoutMixingStableRelease() {
        val releases = """[
            {"tag_name":"v0.1.109-test","name":"Blockline Test 109","draft":false,"prerelease":true},
            {"tag_name":"v0.1.999","name":"Blockline Stable","draft":false,"prerelease":false},
            {"tag_name":"v0.1.110-test","name":"Blockline Test 110","draft":false,"prerelease":true},
            {"tag_name":"v0.1.111-test","name":"Draft test build","draft":true,"prerelease":true},
            {"tag_name":"v0.1.120-beta","name":"Other prerelease","draft":false,"prerelease":true}
        ]"""
        val release = AppUpdateRepository.selectReleaseForChannel(releases, "test")
        assertEquals("v0.1.110-test", release?.optString("tag_name"))
        val stable = AppUpdateRepository.selectReleaseForChannel(
            """{"tag_name":"v0.1.999","draft":false,"prerelease":false}""",
            "stable"
        )
        assertEquals("v0.1.999", stable?.optString("tag_name"))
    }

}
