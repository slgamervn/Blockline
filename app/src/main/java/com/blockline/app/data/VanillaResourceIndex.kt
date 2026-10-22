package com.blockline.app.data

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

object VanillaResourceIndex {
    private const val MAX_TREE_RESPONSE_BYTES = 16L * 1024L * 1024L
    private val treeUrls = listOf(
        "https://api.github.com/repos/Mojang/bedrock-samples/git/trees/main?recursive=1",
        "https://api.github.com/repos/bedrock-dot-dev/packs/git/trees/master?recursive=1"
    )

    fun cacheFile(filesDir: File): File = File(filesDir, "vanilla-reference/resource-textures.txt")

    fun read(cacheFile: File): Set<String> {
        if (!cacheFile.isFile) return emptySet()
        return runCatching {
            cacheFile.useLines { lines ->
                lines.map { normalizePath(it) }
                    .filter { it.startsWith("textures/") && hasTextureExtension(it) }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    fun refresh(cacheFile: File): Int {
        val combined = linkedSetOf<String>()
        val failures = mutableListOf<String>()
        for (url in treeUrls) {
            try {
                val raw = fetch(url)
                combined += extractTexturePaths(raw)
            } catch (error: Exception) {
                failures += (error.message ?: error.javaClass.simpleName)
            }
        }
        require(combined.isNotEmpty()) {
            "Could not load a vanilla texture index. " + failures.joinToString("; ")
        }

        cacheFile.parentFile?.mkdirs()
        val content = combined.sorted().joinToString("\n", postfix = "\n")
        AtomicFileWriter.write(cacheFile, content, suffix = ".tmp")
        return combined.size
    }

    internal fun extractTexturePaths(treeJson: String): Set<String> {
        val json = JSONObject(treeJson)
        require(!json.optBoolean("truncated", false)) {
            "GitHub returned an incomplete vanilla file tree."
        }
        val tree = json.optJSONArray("tree")
            ?: throw IllegalArgumentException("Vanilla source did not return a file tree.")
        val result = linkedSetOf<String>()
        for (index in 0 until tree.length()) {
            val row = tree.optJSONObject(index) ?: continue
            if (row.optString("type") != "blob") continue
            val raw = row.optString("path").replace('\\', '/')
            val normalized = when {
                raw.startsWith("stable/resource/textures/", true) ->
                    raw.removePrefix("stable/resource/")
                raw.startsWith("beta/resource/textures/", true) ->
                    raw.removePrefix("beta/resource/")
                raw.startsWith("resource_pack/textures/", true) ->
                    raw.removePrefix("resource_pack/")
                else -> null
            } ?: continue
            val path = normalizePath(normalized)
            if (path.startsWith("textures/") && hasTextureExtension(path)) result += path
        }
        require(result.isNotEmpty()) { "Vanilla source did not contain any indexed texture paths." }
        return result
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Blockline-Android")
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw IllegalStateException("Vanilla index HTTP " + status + (detail?.take(180)?.let { ": " + it } ?: ""))
            }
            val stream = connection.inputStream
            val bytes = stream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var size = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    size += count
                    require(size <= MAX_TREE_RESPONSE_BYTES) { "Vanilla index response is larger than 16 MiB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    internal fun normalizePath(value: String): String =
        value.trim().replace('\\', '/').removePrefix("/").lowercase(Locale.ROOT)

    private fun hasTextureExtension(path: String): Boolean =
        path.endsWith(".png") || path.endsWith(".tga") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".webp")
}

