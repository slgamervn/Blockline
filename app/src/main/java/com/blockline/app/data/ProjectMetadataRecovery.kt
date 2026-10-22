package com.blockline.app.data

import org.json.JSONObject
import java.io.File

internal object ProjectMetadataRecovery {
    fun recover(projectRoot: File): List<Project> {
        if (!projectRoot.isDirectory) return emptyList()
        return projectRoot.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .filter { runCatching { resolveProjectDirectory(projectRoot, it.name) }.isSuccess }
            .map { directory ->
                val timestamp = directory.lastModified().coerceAtLeast(0L)
                Project(
                    id = directory.name,
                    name = readPackName(directory) ?: "Recovered ${directory.name.take(8)}",
                    createdAt = timestamp,
                    lastOpened = timestamp
                )
            }
            .sortedByDescending { it.lastOpened }
            .toList()
    }

    private fun readPackName(projectDirectory: File): String? {
        val candidates = mutableListOf(
            File(projectDirectory, "manifest.json"),
            File(projectDirectory, "pack_manifest.json")
        )
        projectDirectory.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            .forEach { packDirectory ->
                candidates += File(packDirectory, "manifest.json")
                candidates += File(packDirectory, "pack_manifest.json")
            }

        for (manifest in candidates) {
            if (!manifest.isFile) continue
            val name = runCatching {
                JSONObject(JsonTools.stripComments(manifest.readText(Charsets.UTF_8)))
                    .optJSONObject("header")
                    ?.optString("name")
                    ?.trim()
            }.getOrNull()
            val localizedName = ManifestTextResolver.resolve(
                manifest.parentFile ?: projectDirectory,
                name,
                fallbackKey = "pack.name"
            )
            if (!localizedName.isNullOrBlank()) return localizedName
        }
        return null
    }
}
