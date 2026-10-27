package com.blockline.app.data

import com.blockline.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val version: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val releaseUrl: String,
    val publishedAt: String
)

class AppUpdateRepository(private val channel: String = BuildConfig.UPDATE_CHANNEL) {
    fun checkLatest(currentVersion: String): AppUpdate? {
        val connection = (URL(endpointForChannel(channel)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Blockline-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Update server returned HTTP $status")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = selectReleaseForChannel(body, channel) ?: return null
            val version = release.optString("tag_name", "").removePrefix("v").trim()
            if (!isValidVersion(version)) throw IllegalStateException("The selected release has an invalid version.")
            if (channel == TEST_CHANNEL && !version.endsWith(TEST_VERSION_SUFFIX)) return null

            val expectedAssetName = "Blockline-v$version.apk"
            val asset = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == expectedAssetName }
            } ?: throw IllegalStateException("The selected release does not contain $expectedAssetName.")

            val apkUrl = asset.optString("browser_download_url", "")
            val releaseUrl = release.optString("html_url", "")
            if (!apkUrl.startsWith("https://github.com/$REPOSITORY/releases/download/") ||
                !releaseUrl.startsWith("https://github.com/$REPOSITORY/releases/tag/")) {
                throw IllegalStateException("The release contains an unexpected download URL.")
            }
            if (compareVersions(version, currentVersion) <= 0) return null
            return AppUpdate(
                version = version,
                releaseName = release.optString("name", "Blockline v$version"),
                releaseNotes = release.optString("body", "").trim().take(12_000),
                apkUrl = apkUrl,
                apkSizeBytes = asset.optLong("size", 0L).coerceAtLeast(0L),
                releaseUrl = releaseUrl,
                publishedAt = release.optString("published_at", "")
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPOSITORY = "slgamervn/Blockline"
        const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
        const val LATEST_RELEASE_PAGE_URL = "https://github.com/$REPOSITORY/releases/latest"
        const val TEST_RELEASES_URL = "https://github.com/$REPOSITORY/releases"
        private const val STABLE_LATEST_API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val TEST_RELEASES_API_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val TEST_CHANNEL = "test"
        private const val TEST_VERSION_SUFFIX = "-test"

        private val VERSION_PATTERN = Regex(
            """^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$"""
        )

        fun endpointForChannel(channel: String): String =
            if (channel == TEST_CHANNEL) TEST_RELEASES_API_URL else STABLE_LATEST_API_URL

        fun isValidVersion(value: String): Boolean = SemanticVersion.isValid(value)

        fun isTestRelease(version: String, prerelease: Boolean, draft: Boolean): Boolean =
            !draft && prerelease && version.removePrefix("v").endsWith(TEST_VERSION_SUFFIX) &&
                isValidVersion(version.removePrefix("v"))

        fun selectReleaseForChannel(response: String, channel: String): JSONObject? {
            if (channel != TEST_CHANNEL) {
                val release = JSONObject(response)
                return release.takeUnless {
                    it.optBoolean("draft", false) || it.optBoolean("prerelease", false)
                }
            }
            val releases = JSONArray(response)
            return (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .filter { release ->
                    isTestRelease(
                        release.optString("tag_name", ""),
                        release.optBoolean("prerelease", false),
                        release.optBoolean("draft", false)
                    )
                }
                .maxWithOrNull { left, right ->
                    compareVersions(
                        left.optString("tag_name", "").removePrefix("v"),
                        right.optString("tag_name", "").removePrefix("v")
                    )
                }
        }

        fun compareVersions(left: String, right: String): Int {
            val leftMatch = parsedVersion(left)
            val rightMatch = parsedVersion(right)
            for (group in 1..3) {
                val compared = BigInteger(leftMatch.groupValues[group])
                    .compareTo(BigInteger(rightMatch.groupValues[group]))
                if (compared != 0) return compared
            }
            val leftPrerelease = leftMatch.groups[4]?.value
            val rightPrerelease = rightMatch.groups[4]?.value
            if (leftPrerelease == null && rightPrerelease == null) return 0
            if (leftPrerelease == null) return 1
            if (rightPrerelease == null) return -1
            val leftIdentifiers = leftPrerelease.split('.')
            val rightIdentifiers = rightPrerelease.split('.')
            for (index in 0 until minOf(leftIdentifiers.size, rightIdentifiers.size)) {
                val compared = comparePrereleaseIdentifiers(leftIdentifiers[index], rightIdentifiers[index])
                if (compared != 0) return compared
            }
            return leftIdentifiers.size.compareTo(rightIdentifiers.size)
        }

        private fun parsedVersion(version: String): MatchResult {
            require(isValidVersion(version)) { "Invalid semantic version: $version" }
            return VERSION_PATTERN.matchEntire(version)!!
        }

        private fun comparePrereleaseIdentifiers(left: String, right: String): Int {
            val leftNumeric = left.all { it in '0'..'9' }
            val rightNumeric = right.all { it in '0'..'9' }
            return when {
                leftNumeric && rightNumeric -> BigInteger(left).compareTo(BigInteger(right))
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left.compareTo(right)
            }
        }
    }
}
