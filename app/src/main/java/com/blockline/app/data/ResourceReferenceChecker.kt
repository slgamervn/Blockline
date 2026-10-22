package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal object ResourceReferenceChecker {
    private val textureKey = Regex("(?i)^textures?$")

    fun check(
        projectRoot: File,
        files: List<File>,
        vanillaResourcePaths: Set<String> = emptySet()
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val canonicalRoot = runCatching { projectRoot.canonicalFile }.getOrNull() ?: return issues
        val pngFiles = files.asSequence()
            .filter { it.isFile && it.extension.equals("png", true) }
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
            .toSet()

        val textureIndexByPack = mutableMapOf<String, Set<File>>()
        files.asSequence()
            .filter { it.isFile && it.extension.equals("json", true) }
            .filter { file ->
                val path = relative(projectRoot, file).lowercase(Locale.ROOT)
                path.contains("entity") || path.contains("render_controller") ||
                    path.contains("animation") || path.contains("geometry")
            }
            .forEach { file ->
                val root = runCatching { JSONObject(JsonTools.stripComments(file.readText(Charsets.UTF_8))) }.getOrNull()
                    ?: return@forEach
                val packRoot = findPackRoot(canonicalRoot, file) ?: canonicalRoot
                val packPath = packRoot.path + File.separator
                val packTextures = textureIndexByPack.getOrPut(packRoot.path) {
                    pngFiles.filter { it.path.startsWith(packPath) }.toSet()
                }
                inspectTextures(root, false) { reference ->
                    val normalized = reference.trim().replace('\\', '/').removePrefix("/")
                    if (!normalized.startsWith("textures/", true) || normalized.contains("..")) return@inspectTextures
                    val candidates = if (normalized.substringAfterLast('/').contains('.')) {
                        listOf(normalized)
                    } else {
                        listOf("$normalized.png", "$normalized.tga", "$normalized.jpg", "$normalized.jpeg", "$normalized.webp")
                    }
                    val localMatches = candidates.mapNotNull { candidate ->
                        runCatching { File(packRoot, candidate).canonicalFile }.getOrNull()
                    }.filter { it.path.startsWith(packPath) }
                    if (localMatches.none { it in packTextures }) {
                        val knownVanilla = candidates.any { candidate ->
                            VanillaResourceIndex.normalizePath(candidate) in vanillaResourcePaths
                        }
                        if (!knownVanilla) {
                            issues += ValidationIssue(
                                IssueSeverity.INFO,
                                relative(projectRoot, file),
                                message = "Texture reference '$reference' is not present in this pack or the downloaded vanilla reference index. It may be provided by Minecraft vanilla or another active resource pack; verify in-game before treating it as broken."
                            )
                        }
                    }
                }
            }
        return issues
    }

    private fun findPackRoot(projectRoot: File, file: File): File? {
        var current = file.parentFile ?: return null
        while (current.path.startsWith(projectRoot.path)) {
            if (File(current, "manifest.json").isFile || File(current, "pack_manifest.json").isFile) return current
            if (current == projectRoot) break
            current = current.parentFile ?: break
        }
        return null
    }

    private fun inspectTextures(value: Any?, insideTextureMap: Boolean, onReference: (String) -> Unit) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    val textureMap = textureKey.matches(key)
                    if (insideTextureMap && child is String) {
                        onReference(child)
                    } else {
                        inspectTextures(child, textureMap, onReference)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                inspectTextures(value.opt(i), insideTextureMap, onReference)
            }
        }
    }

    private fun relative(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')
}
