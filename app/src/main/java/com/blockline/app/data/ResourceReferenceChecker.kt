package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal object ResourceReferenceChecker {
    private val textureKey = Regex("(?i)^textures?$")

    fun check(projectRoot: File, files: List<File>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val textureFiles = files.asSequence()
            .filter { it.isFile && it.extension.equals("png", true) }
            .map { runCatching { it.canonicalFile }.getOrNull() }
            .filterNotNull()
            .toSet()

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
                inspectTextures(root, false) { reference ->
                    val normalized = reference.trim().replace('\\\\', '/').removePrefix("/")
                    if (!normalized.startsWith("textures/", true) || normalized.contains("..")) return@inspectTextures
                    val candidate = if (normalized.substringAfterLast('/').contains('.')) normalized
                        else "$normalized.png"
                    val target = runCatching { File(projectRoot, candidate).canonicalFile }.getOrNull()
                    if (target == null || !target.path.startsWith(projectRoot.canonicalPath + File.separator)) return@inspectTextures
                    if (target !in textureFiles) {
                        issues += ValidationIssue(
                            IssueSeverity.WARNING,
                            relative(projectRoot, file),
                            message = "Texture reference '$reference' does not match a PNG file in this project. Verify the path and extension."
                        )
                    }
                }
            }
        return issues
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
