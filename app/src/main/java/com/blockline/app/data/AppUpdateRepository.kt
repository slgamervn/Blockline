package com.blockline.app.data

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

class AppUpdateRepository {
    fun checkLatest(currentVersion: String): AppUpdate? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
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
            val release = JSONObject(body)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return null
            val version = release.optString("tag_name", "").removePrefix("v").trim()
            if (!isValidVersion(version)) throw IllegalStateException("The latest release has an invalid version.")
            val asset = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == "Blockline-v$version.apk" }
            } ?: throw IllegalStateException("The latest release does not contain its expected APK.")
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
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000

        private val VERSION_PATTERN = Regex(
            """^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$"""
        )

        fun isValidVersion(value: String): Boolean = SemanticVersion.isValid(value)

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
