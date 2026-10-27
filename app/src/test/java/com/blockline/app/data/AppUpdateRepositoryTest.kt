package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
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
    fun expectedAssetNameTracksReleaseVersionForBothChannels() {
        assertEquals("Blockline-v1.2.3.apk", UpdateApkPolicy.expectedAssetName("1.2.3"))
        assertEquals("Blockline-v0.1.54-test.apk", UpdateApkPolicy.expectedAssetName("0.1.54-test"))
        assertTrue(UpdateApkPolicy.isAllowedApkUrl("https://github.com/slgamervn/Blockline/releases/download/v1/Blockline-v1.apk"))
        assertFalse(UpdateApkPolicy.isAllowedApkUrl("https://example.com/Blockline.apk"))
    }

    @Test
    fun parsesPublishedSha256AndRejectsMalformedDigests() {
        val digest = "ab".repeat(32)
        assertEquals(digest, AppUpdateRepository.digestForAsset(JSONObject("""{"digest":"sha256:$digest"}""")))
        assertEquals(null, AppUpdateRepository.digestForAsset(JSONObject()))
        assertFalse(runCatching {
            AppUpdateRepository.digestForAsset(JSONObject("""{"digest":"md5:$digest"}"""))
        }.isSuccess)
        assertFalse(runCatching {
            AppUpdateRepository.digestForAsset(JSONObject("""{"digest":"sha256:xyz"}"""))
        }.isSuccess)
    }

    @Test
    fun sha256VerificationAcceptsOnlyThePublishedDigest() {
        val expected = "a1".repeat(32)
        UpdateApkPolicy.validateSha256(expected, expected)
        UpdateApkPolicy.validateSha256("b2".repeat(32), null)
        assertFalse(runCatching {
            UpdateApkPolicy.validateSha256("b2".repeat(32), expected)
        }.isSuccess)
        assertFalse(runCatching {
            UpdateApkPolicy.validateSha256("not-a-digest", expected)
        }.isSuccess)
    }

    @Test
    fun packageVerificationRejectsWrongPackageVersionAndDowngrade() {
        UpdateApkPolicy.validatePackageMetadata("com.blockline.app.test", "com.blockline.app.test", "0.1.54-test", "0.1.54-test", 54L, 53L)
        assertFalse(runCatching { UpdateApkPolicy.validatePackageMetadata("com.blockline.app", "com.blockline.app.test", "0.1.54-test", "0.1.54-test", 54L, 53L) }.isSuccess)
        assertFalse(runCatching { UpdateApkPolicy.validatePackageMetadata("com.blockline.app.test", "com.blockline.app.test", "0.1.55-test", "0.1.54-test", 54L, 53L) }.isSuccess)
        assertFalse(runCatching { UpdateApkPolicy.validatePackageMetadata("com.blockline.app.test", "com.blockline.app.test", "0.1.54-test", "0.1.54-test", 52L, 53L) }.isSuccess)
    }

    @Test
    fun interruptedDownloadsResumeOnlyWhenPartialFileIsIncomplete() {
        assertEquals(0L, UpdateApkPolicy.resumableBytes(0L, 100L))
        assertEquals(25L, UpdateApkPolicy.resumableBytes(25L, 100L))
        assertEquals(0L, UpdateApkPolicy.resumableBytes(100L, 100L))
        assertEquals(0L, UpdateApkPolicy.resumableBytes(20L, 0L))
    }

    @Test
    fun downloadProgressIsClampedAndCertificateHistoryMustOverlap() {
        assertEquals(50, UpdateApkPolicy.progressPercent(50L, 100L))
        assertEquals(100, UpdateApkPolicy.progressPercent(101L, 100L))
        assertTrue(UpdateApkPolicy.signaturesCompatible(setOf("old", "current"), setOf("current")))
        assertFalse(UpdateApkPolicy.signaturesCompatible(setOf("installed"), setOf("untrusted")))
        assertEquals(setOf("A", "B"), UpdateApkPolicy.signerSet("A", "B"))
    }

    @Test
    fun testChannelUsesItsOwnEndpointAndFiltersOnlyTestPrereleases() {
        assertTrue(AppUpdateRepository.endpointForChannel("test").contains("/releases?per_page="))
        assertTrue(AppUpdateRepository.endpointForChannel("stable").endsWith("/releases/latest"))
        assertTrue(AppUpdateRepository.isSupportedChannel("stable"))
        assertTrue(AppUpdateRepository.isSupportedChannel("test"))
        assertFalse(AppUpdateRepository.isSupportedChannel("disabled"))
        assertFalse(UpdateApkPolicy.isAllowedApkUrl("https://github.com@evil.example/slgamervn/Blockline/releases/download/v1/Blockline-v1.apk"))
        assertFalse(UpdateApkPolicy.isAllowedApkUrl("https://github.com/slgamervn/Blockline/releases/download/../../evil.apk"))
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
