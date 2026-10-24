package com.blockline.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object InAppUpdateInstaller {
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    fun cachedFile(context: Context, version: String): File? {
        val safeVersion = version.takeIf { it.matches(Regex("[0-9A-Za-z.+-]{1,80}")) } ?: return null
        return File(File(context.cacheDir, "updates"), "Blockline-$safeVersion.apk").takeIf { it.isFile && it.length() > 0L }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun download(context: Context, update: AppUpdate, onProgress: (Int) -> Unit): File {
        require(update.apkUrl.startsWith("https://github.com/slgamervn/Blockline/releases/download/")) {
            "Nguồn tải APK không hợp lệ."
        }
        require(update.apkSizeBytes in 1..MAX_APK_BYTES) { "Kích thước APK không hợp lệ." }
        val safeVersion = update.version.takeIf { it.matches(Regex("[0-9A-Za-z.+-]{1,80}")) }
            ?: throw IllegalArgumentException("Phiên bản APK không hợp lệ.")
        val directory = File(context.cacheDir, "updates").apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("Không thể tạo thư mục tải xuống.")
        }
        val target = File(directory, "Blockline-$safeVersion.apk")
        val partial = File(directory, "Blockline-$safeVersion.apk.part")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Blockline-Android")
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Máy chủ tải xuống trả về HTTP $status.")
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_APK_BYTES || (contentLength > 0L && contentLength != update.apkSizeBytes)) {
                throw IllegalStateException("Kích thước APK không khớp với thông tin bản phát hành.")
            }
            var received = 0L
            var lastPercent = -1
            connection.inputStream.buffered().use { input ->
                FileOutputStream(partial).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > MAX_APK_BYTES || received > update.apkSizeBytes) {
                            throw IllegalStateException("Tệp tải xuống vượt quá kích thước dự kiến.")
                        }
                        output.write(buffer, 0, count)
                        val percent = ((received * 100L) / update.apkSizeBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            if (received != update.apkSizeBytes) {
                throw IllegalStateException("Tệp tải xuống chưa đầy đủ ($received/${update.apkSizeBytes} byte).")
            }
            if (target.exists() && !target.delete()) throw IllegalStateException("Không thể thay thế APK đã lưu.")
            if (!partial.renameTo(target)) throw IllegalStateException("Không thể hoàn tất tệp APK.")
            onProgress(100)
            return target
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun launchInstaller(context: Context, apk: File) {
        require(apk.isFile && apk.length() > 0L) { "Không tìm thấy APK đã tải xuống." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
