package com.blockline.app.data

import org.json.JSONObject
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
        const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
        private const val REPOSITORY = "slgamervn/Blockline"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000

        fun isValidVersion(value: String): Boolean =
            Regex("""^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$""").matches(value)

        fun compareVersions(left: String, right: String): Int {
            val a = numericParts(left)
            val b = numericParts(right)
            for (index in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(index) { 0L }
                val y = b.getOrElse(index) { 0L }
                if (x != y) return x.compareTo(y)
            }
            return compareSuffix(left, right)
        }

        private fun numericParts(version: String): List<Long> =
            version.substringBefore('-').substringBefore('+')
                .split('.').map { it.toLongOrNull() ?: 0L }

        private fun compareSuffix(left: String, right: String): Int {
            val leftStable = '-' !in left && '+' !in left
            val rightStable = '-' !in right && '+' !in right
            if (leftStable != rightStable) return if (leftStable) 1 else -1
            return left.compareTo(right, ignoreCase = true)
        }
    }
}
