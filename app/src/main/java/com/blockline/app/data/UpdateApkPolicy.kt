package com.blockline.app.data

object UpdateApkPolicy {
    private const val REPOSITORY = "slgamervn/Blockline"

    fun expectedAssetName(version: String): String {
        require(AppUpdateRepository.isValidVersion(version)) { "Invalid release version." }
        return "Blockline-v$version.apk"
    }

    fun isAllowedApkUrl(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val prefix = "/$REPOSITORY/releases/download/"
        return uri.scheme == "https" && uri.host == "github.com" && uri.rawUserInfo == null &&
            uri.query == null && uri.fragment == null && uri.path.startsWith(prefix) &&
            uri.path.removePrefix(prefix).split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    fun resumableBytes(partialBytes: Long, totalBytes: Long): Long =
        partialBytes.takeIf { totalBytes > 0L && it in 1 until totalBytes } ?: 0L

    fun progressPercent(receivedBytes: Long, totalBytes: Long): Int {
        require(totalBytes > 0L) { "Total download size must be positive." }
        return ((receivedBytes.coerceAtLeast(0L).coerceAtMost(totalBytes) * 100L) / totalBytes).toInt()
    }

    fun validatePackageMetadata(
        expectedPackageName: String,
        actualPackageName: String,
        actualVersionName: String,
        expectedVersionName: String,
        actualVersionCode: Long,
        installedVersionCode: Long
    ) {
        require(expectedPackageName == actualPackageName) { "This APK belongs to a different Blockline channel or package." }
        require(actualVersionName == expectedVersionName) { "The APK version does not match the selected release." }
        require(actualVersionCode > installedVersionCode) { "This APK is not a newer version of the installed app." }
    }

    fun validateSha256(actual: String, expected: String?) {
        if (expected == null) return
        require(expected.matches(Regex("[0-9a-fA-F]{64}"))) { "The published SHA-256 digest is malformed." }
        require(actual.matches(Regex("[0-9a-fA-F]{64}"))) { "Could not calculate a valid APK SHA-256 digest." }
        require(actual.equals(expected, ignoreCase = true)) {
            "The APK SHA-256 digest does not match the published release asset."
        }
    }

    fun signaturesCompatible(
        installedHistory: Set<String>,
        candidateHistory: Set<String>,
        requireExact: Boolean = false
    ): Boolean = when {
        installedHistory.isEmpty() || candidateHistory.isEmpty() -> false
        requireExact -> installedHistory == candidateHistory
        else -> installedHistory.intersect(candidateHistory).isNotEmpty()
    }

    fun signerSet(vararg signers: String): Set<String> = signers.toSet()
}
