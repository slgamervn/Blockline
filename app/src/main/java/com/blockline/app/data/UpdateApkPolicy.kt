package com.blockline.app.data

object UpdateApkPolicy {
    private const val REPOSITORY = "slgamervn/Blockline"

    fun expectedAssetName(version: String): String {
        require(AppUpdateRepository.isValidVersion(version)) { "Invalid release version." }
        return "Blockline-v$version.apk"
    }

    fun isAllowedApkUrl(url: String): Boolean =
        url.startsWith("https://github.com/$REPOSITORY/releases/download/") &&
            url.substringAfter("https://github.com/$REPOSITORY/releases/download/").isNotBlank() &&
            !url.contains("@") && !url.contains("\\n")

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
