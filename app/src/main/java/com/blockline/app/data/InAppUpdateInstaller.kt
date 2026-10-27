package com.blockline.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class UpdateDownloadState {
    IDLE, DOWNLOADING, VERIFYING, READY_TO_INSTALL, WAITING_PERMISSION, INSTALLING, FAILED, CANCELLED
}

/** Download + verify + invoke Android's confirmation UI. The app never installs silently. */
object InAppUpdateInstaller {
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private const val STORAGE_HEADROOM_BYTES = 4L * 1024L * 1024L
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    fun cachedFile(context: Context, version: String): File? {
        val safeVersion = version.takeIf { it.matches(Regex("[0-9A-Za-z.+-]{1,80}")) } ?: return null
        return File(File(context.cacheDir, "updates"), "Blockline-$safeVersion.apk")
            .takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun download(
        context: Context,
        update: AppUpdate,
        onProgress: suspend (Int) -> Unit
    ): File {
        require(UpdateApkPolicy.isAllowedApkUrl(update.apkUrl)) { "The APK URL is not an official Blockline release asset." }
        require(update.apkSizeBytes in 1..MAX_APK_BYTES) { "The release asset has an invalid size." }
        require(update.apkUrl.substringAfterLast('/').substringBefore('?') == UpdateApkPolicy.expectedAssetName(update.version)) {
            "The release asset name does not match version ${update.version}."
        }
        val directory = File(context.cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Cannot create the update cache directory.")
        val target = File(directory, "Blockline-${update.version}.apk")
        val partial = File(directory, "Blockline-${update.version}.apk.part")
        val resumableBytes = partial.length().takeIf { it in 1 until update.apkSizeBytes } ?: 0L
        val stat = StatFs(directory.absolutePath)
        if (stat.availableBytes < update.apkSizeBytes - resumableBytes + STORAGE_HEADROOM_BYTES) {
            throw java.io.IOException("Not enough free storage to download this update safely.")
        }
        return withContext(Dispatchers.IO) {
            var resumeFrom = partial.length().takeIf { it in 1 until update.apkSizeBytes } ?: 0L
            if (resumeFrom == 0L && partial.exists() && !partial.delete()) {
                throw java.io.IOException("Cannot clear an invalid partial download.")
            }
            val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Blockline-Android")
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            try {
                var status = connection.responseCode
                if (status == HttpURLConnection.HTTP_PARTIAL) {
                    val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                    require(contentRange.startsWith("bytes $resumeFrom-")) { "The server returned an invalid resume range." }
                    val expectedRemaining = update.apkSizeBytes - resumeFrom
                    if (connection.contentLengthLong > 0L && connection.contentLengthLong != expectedRemaining) {
                        throw java.io.IOException("The resumed download size does not match release metadata.")
                    }
                } else if (status == HttpURLConnection.HTTP_OK) {
                    // Server ignored Range; restart cleanly instead of appending duplicate bytes.
                    resumeFrom = 0L
                } else {
                    throw java.io.IOException("The download server returned HTTP $status.")
                }
                val responseLength = connection.contentLengthLong
                if (responseLength > MAX_APK_BYTES || (resumeFrom == 0L && responseLength > 0L && responseLength != update.apkSizeBytes)) {
                    throw java.io.IOException("The downloaded size does not match release metadata.")
                }
                var received = resumeFrom
                var lastPercent = -1
                connection.inputStream.buffered().use { input ->
                    FileOutputStream(partial, resumeFrom > 0L).buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > update.apkSizeBytes || received > MAX_APK_BYTES) {
                                throw java.io.IOException("The download exceeded its declared size.")
                            }
                            output.write(buffer, 0, count)
                            val percent = UpdateApkPolicy.progressPercent(received, update.apkSizeBytes)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
                if (received != update.apkSizeBytes) {
                    throw java.io.IOException("The download was interrupted ($received of ${update.apkSizeBytes} bytes). Retry to resume.")
                }
                val header = ByteArray(4)
                partial.inputStream().use { input ->
                    if (input.read(header) != header.size || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                        throw java.io.IOException("The downloaded file is not a valid APK archive.")
                    }
                }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                target
            } catch (error: Throwable) {
                // Keep a bounded .part file for safe Range retry; never present it to the installer.
                if (partial.length() > update.apkSizeBytes || partial.length() > MAX_APK_BYTES) partial.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }
    }

    fun verifyDownloadedApk(context: Context, apk: File, update: AppUpdate) {
        require(apk.isFile && apk.length() == update.apkSizeBytes && apk.length() in 1..MAX_APK_BYTES) {
            "The downloaded APK is missing or has an unexpected size."
        }
        val pm = context.packageManager
        val archive = getArchiveInfo(pm, apk) ?: throw IllegalArgumentException("Android could not read the downloaded APK.")
        val installed = getInstalledInfo(pm, context.packageName)
        val archiveVersionCode = longVersionCode(archive)
        val installedVersionCode = longVersionCode(installed)
        UpdateApkPolicy.validatePackageMetadata(
            context.packageName,
            archive.packageName.orEmpty(),
            archive.versionName.orEmpty(),
            update.version,
            archiveVersionCode,
            installedVersionCode
        )
        require(UpdateApkPolicy.signaturesCompatible(
            certificateHistory(installed).map(::fingerprint).toSet(),
            certificateHistory(archive).map(::fingerprint).toSet(),
            requireExact = currentSigners(installed).size > 1 || currentSigners(archive).size > 1
        )) {
            "The APK signing certificate is incompatible with the installed Blockline app."
        }
    }

    private fun getArchiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, signingFlags())
        }

    private fun getInstalledInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, signingFlags())
        }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private fun currentSigners(info: PackageInfo): List<android.content.pm.Signature> =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }

    private fun certificateHistory(info: PackageInfo): List<android.content.pm.Signature> =
        if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo
            (signing?.signingCertificateHistory ?: signing?.apkContentsSigners)?.toList().orEmpty()
        } else currentSigners(info)

    private fun fingerprint(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun launchInstaller(context: Context, apk: File) {
        require(apk.isFile && apk.length() > 0L) { "The verified APK file is missing." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
