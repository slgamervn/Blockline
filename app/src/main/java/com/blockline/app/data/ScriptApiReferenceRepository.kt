package com.blockline.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScriptApiReferenceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.filesDir, "script-api-reference")
    private val supportedPackages = setOf(
        "@minecraft/server",
        "@minecraft/server-ui",
        "@minecraft/common"
    )

    private data class PackageMetadata(
        val latest: String,
        val versions: List<String>
    )

    suspend fun load(
        packageName: String,
        requestedVersion: String? = null,
        refreshOnline: Boolean = false
    ): ScriptApiLoadResult = withContext(Dispatchers.IO) {
        require(packageName in supportedPackages) { "Unsupported Script API package." }
        if (requestedVersion != null) {
            require(Regex("""^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$""").matches(requestedVersion)) {
                "Invalid package version."
            }
        }
        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) { "Could not create API cache." }

        var metadata = readMetadata(packageName)
        var registryVersions: List<String> = metadata?.versions.orEmpty()
        var targetVersion = requestedVersion ?: metadata?.latest ?: readCachedVersion(packageName) ?: bundledVersion(packageName)
        var errorMessage: String? = null

        if (refreshOnline) {
            try {
                metadata = fetchMetadata(packageName)
                registryVersions = metadata.versions
                targetVersion = requestedVersion ?: metadata.latest
                writeMetadata(packageName, metadata)
            } catch (error: Exception) {
                errorMessage = "Online version lookup failed: " + (error.message ?: "network error")
                if (requestedVersion == null && sourceCacheFile(packageName, targetVersion).isFile) {
                    targetVersion = targetVersion
                }
            }
        }

        val cachedTarget = sourceCacheFile(packageName, targetVersion)
        var sourceText = if (!refreshOnline && cachedTarget.isFile) {
            runCatching { cachedTarget.readText(Charsets.UTF_8) }.getOrNull()
        } else null
        var sourceKind = ScriptApiSource.CACHE

        if (sourceText == null && (refreshOnline || requestedVersion != null)) {
            try {
                sourceText = fetchDeclarationSource(packageName, targetVersion)
                writeSourceCache(packageName, targetVersion, sourceText!!)
                sourceKind = ScriptApiSource.LIVE
            } catch (error: Exception) {
                errorMessage = "Type declarations could not be refreshed: " + (error.message ?: "network error")
            }
        }

        var finalVersion = targetVersion
        if (sourceText == null) {
            val cachedVersion = readCachedVersion(packageName)
            if (cachedVersion != null) {
                val cacheFile = sourceCacheFile(packageName, cachedVersion)
                if (cacheFile.isFile) {
                    sourceText = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
                    if (sourceText != null) {
                        sourceKind = ScriptApiSource.CACHE
                        finalVersion = cachedVersion
                    }
                }
            }
        }
        if (sourceText == null && (requestedVersion == null || requestedVersion == bundledVersion(packageName))) {
            sourceText = readBundledSource(packageName)
            if (sourceText != null) {
                sourceKind = ScriptApiSource.BUNDLED
                finalVersion = bundledVersion(packageName)
            }
        }
        val actualSource = sourceText
        if (actualSource == null) {
            return@withContext ScriptApiLoadResult(
                packageName = packageName,
                version = finalVersion,
                symbols = emptyList(),
                rawSource = "",
                source = ScriptApiSource.CACHE,
                availableVersions = (registryVersions + finalVersion).distinct().sortedWith(versionComparator).take(80),
                error = errorMessage ?: "No cached or bundled declarations are available for this version."
            )
        }

        val parsed = ScriptApiDeclarationParser.parse(actualSource, packageName, finalVersion)
        parsed.copy(
            source = sourceKind,
            availableVersions = (registryVersions + finalVersion).distinct().sortedWith(versionComparator).take(80),
            error = errorMessage ?: parsed.error
        )
    }

    private fun fetchMetadata(packageName: String): PackageMetadata {
        val encodedName = packageName.replace("@", "%40").replace("/", "%2f")
        val json = JSONObject(readHttps("https://registry.npmjs.org/" + encodedName, MAX_METADATA_BYTES))
        val tags = json.optJSONObject("dist-tags")
        val versionsObject = json.optJSONObject("versions") ?: JSONObject()
        val stableVersions = mutableListOf<String>()
        val keys = versionsObject.keys()
        while (keys.hasNext()) {
            val version = keys.next()
            if (Regex("""^\d+\.\d+\.\d+$""").matches(version)) stableVersions += version
        }
        val stableSorted = stableVersions.distinct().sortedWith(versionComparator)
        val taggedLatest = tags?.optString("latest").orEmpty()
        val latest = if (taggedLatest in stableSorted) taggedLatest else stableSorted.firstOrNull()
            ?: error("The registry returned no stable package versions.")
        return PackageMetadata(latest, stableSorted.take(120))
    }

    private fun fetchDeclarationSource(packageName: String, version: String): String {
        val encodedName = packageName.replace("@", "%40").replace("/", "%2f")
        val versionMetadata = runCatching {
            JSONObject(readHttps(
                "https://registry.npmjs.org/" + encodedName + "/" + version,
                MAX_METADATA_BYTES
            ))
        }.getOrNull()
        var declarationPath = versionMetadata?.optString("types").orEmpty()
        if (declarationPath.isBlank()) declarationPath = versionMetadata?.optString("typings").orEmpty()
        val candidates = (listOf(declarationPath, "index.d.ts", "lib/index.d.ts", "dist/index.d.ts"))
            .map { it.removePrefix("./").trim() }
            .filter { it.endsWith(".d.ts") && !it.contains("..") && !it.startsWith("/") }
            .distinct()

        var lastError: Exception? = null
        candidates.forEach { path ->
            try {
                return readHttps(
                    "https://cdn.jsdelivr.net/npm/" + packageName + "@" + version + "/" + path,
                    MAX_SOURCE_BYTES
                )
            } catch (error: Exception) {
                lastError = error
            }
        }

        val listing = runCatching {
            readHttps(
                "https://data.jsdelivr.com/v1/package/npm/" + packageName + "@" + version + "/flat",
                MAX_METADATA_BYTES
            )
        }.getOrNull()
        val files = runCatching { JSONArray(listing ?: "[]") }.getOrNull()
        val fallbackPaths = if (files == null) emptyList() else {
            (0 until files.length()).mapNotNull { index ->
                files.optJSONObject(index)?.optString("name")?.takeIf {
                    it.endsWith(".d.ts") && !it.contains("..") && !it.startsWith("/")
                }
            }.sortedWith(compareBy<String> { if (it == "index.d.ts") 0 else 1 }.thenBy { it.length }).take(8)
        }
        fallbackPaths.forEach { path ->
            try {
                return readHttps(
                    "https://cdn.jsdelivr.net/npm/" + packageName + "@" + version + "/" + path,
                    MAX_SOURCE_BYTES
                )
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IOException(lastError?.message ?: "No TypeScript declaration file was found.")
    }

    private fun readHttps(urlText: String, maxBytes: Int): String {
        val url = URL(urlText)
        require(url.protocol == "https" && url.host in ALLOWED_HOSTS) { "Blocked API source URL." }
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Blockline-Script-API-Reference")
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP " + code)
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw IOException("Downloaded source exceeds the safety limit.")
                    output.write(buffer, 0, count)
                }
                return String(output.toByteArray(), StandardCharsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readMetadata(packageName: String): PackageMetadata? = runCatching {
        val json = JSONObject(metadataCacheFile(packageName).readText(Charsets.UTF_8))
        val entries = json.optJSONArray("versions") ?: JSONArray()
        val versions = (0 until entries.length()).mapNotNull {
            entries.optString(it).takeIf(String::isNotBlank)
        }
        val latest = json.optString("latest")
        if (latest.isBlank()) null else PackageMetadata(latest, versions)
    }.getOrNull()

    private fun writeMetadata(packageName: String, metadata: PackageMetadata) {
        val json = JSONObject()
            .put("latest", metadata.latest)
            .put("versions", JSONArray(metadata.versions))
        writeAtomically(metadataCacheFile(packageName), json.toString())
    }

    private fun readCachedVersion(packageName: String): String? {
        val metadata = readMetadata(packageName) ?: return null
        if (sourceCacheFile(packageName, metadata.latest).isFile) return metadata.latest
        return metadata.versions.firstOrNull { sourceCacheFile(packageName, it).isFile }
    }

    private fun readBundledSource(packageName: String): String? = runCatching {
        val asset = when (packageName) {
            "@minecraft/server" -> "script-api/server.d.ts"
            "@minecraft/server-ui" -> "script-api/server-ui.d.ts"
            "@minecraft/common" -> "script-api/common.d.ts"
            else -> return null
        }
        appContext.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun bundledVersion(packageName: String): String = when (packageName) {
        "@minecraft/server" -> "2.10.0"
        "@minecraft/server-ui" -> "2.2.0"
        "@minecraft/common" -> "1.3.0"
        else -> "0.0.0"
    }

    private fun sourceCacheFile(packageName: String, version: String) =
        File(cacheDirectory, cacheKey(packageName) + "-" + version + ".d.ts")

    private fun metadataCacheFile(packageName: String) =
        File(cacheDirectory, cacheKey(packageName) + "-versions.json")

    private fun cacheKey(packageName: String) = packageName.removePrefix("@").replace("/", "_")

    private fun writeSourceCache(packageName: String, version: String, source: String) {
        writeAtomically(sourceCacheFile(packageName, version), source)
        val metadata = readMetadata(packageName)
        if (metadata != null && version !in metadata.versions) {
            writeMetadata(packageName, metadata.copy(versions = (listOf(version) + metadata.versions).distinct()))
        }
    }

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.delete()
                check(temp.renameTo(target)) { "Could not save Script API cache." }
            }
        } finally {
            temp.delete()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return left.compareTo(right)
    }

    private val versionComparator = Comparator<String> { left, right -> compareVersions(right, left) }

    companion object {
        private const val NETWORK_TIMEOUT_MS = 7_000
        private const val MAX_METADATA_BYTES = 4_000_000
        private const val MAX_SOURCE_BYTES = 3_500_000
        private val ALLOWED_HOSTS = setOf("registry.npmjs.org", "cdn.jsdelivr.net", "data.jsdelivr.net")
    }
}
