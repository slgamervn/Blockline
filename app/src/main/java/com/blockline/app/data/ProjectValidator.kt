package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.Locale

enum class IssueSeverity { ERROR, WARNING, INFO }

data class ValidationIssue(
    val severity: IssueSeverity,
    val path: String,
    val line: Int? = null,
    val column: Int? = null,
    val message: String
)

object ProjectValidator {
    private val uuidPattern = Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val jsonExtensions = setOf("json", "mcmeta")

    fun validate(projectRoot: File): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (!projectRoot.isDirectory) {
            return listOf(ValidationIssue(IssueSeverity.ERROR, "", message = "Project folder is missing."))
        }

        val files = projectRoot.walkTopDown().filter { it.isFile }.toList()
        val jsonFiles = files.filter { it.extension.lowercase(Locale.ROOT) in jsonExtensions }
        val parsed = linkedMapOf<File, Any>()

        jsonFiles.forEach { file ->
            val path = relative(projectRoot, file)
            val source = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Cannot read file: ${it.message ?: "unknown error"}")
                return@forEach
            }
            val clean = JsonTools.stripComments(source).removePrefix("\uFEFF")
            try {
                val tokener = JSONTokener(clean)
                val value = tokener.nextValue()
                if (value !is JSONObject && value !is JSONArray) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, 1, 1, "JSON root must be an object or array.")
                } else if (tokener.nextClean() != '\u0000') {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Unexpected content after the JSON value.")
                } else {
                    parsed[file] = value
                }
            } catch (e: JSONException) {
                val message = e.message ?: "Invalid JSON syntax."
                val index = Regex("(?i)(?:character|index|position)\\s*(?:at)?\\s*(\\d+)").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
                issues += locationIssue(IssueSeverity.ERROR, path, source, "Invalid JSON: $message", index)
            } catch (e: Exception) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Could not validate JSON: ${e.message ?: "unknown error"}")
            }
        }

        val manifests = files.filter { it.name.equals("manifest.json", true) }
        if (manifests.isEmpty()) {
            issues += ValidationIssue(IssueSeverity.WARNING, "", message = "No manifest.json was found. This project may not contain a Bedrock pack.")
        }

        val uuidOwners = linkedMapOf<String, String>()
        val internalVersions = linkedMapOf<String, List<Int>>()
        manifests.forEach { file ->
            val path = relative(projectRoot, file)
            val json = parsed[file] as? JSONObject
            if (json == null) {
                if (file !in jsonFiles) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Manifest is not valid JSON.")
                return@forEach
            }
            val header = json.optJSONObject("header")
            if (header == null) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Missing required object: header.")
            } else {
                if (header.optString("name").isBlank()) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "header.name is missing or empty.")
                if (header.optString("description").isBlank()) issues += ValidationIssue(IssueSeverity.WARNING, path, message = "header.description is missing or empty.")
                val headerUuid = header.optString("uuid")
                registerUuid(headerUuid, "$path · header.uuid", path, uuidOwners, issues)
                val headerVersion = intVersion(header.opt("version"))
                if (headerVersion == null) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "header.version must contain three non-negative integers.")
                if (!headerUuid.isNullOrBlank() && uuidPattern.matches(headerUuid)) internalVersions[headerUuid.lowercase(Locale.ROOT)] = headerVersion.orEmpty()
            }

            val modules = json.optJSONArray("modules")
            if (modules == null || modules.length() == 0) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "modules must be a non-empty array.")
            } else {
                for (i in 0 until modules.length()) {
                    val module = modules.optJSONObject(i)
                    if (module == null) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "modules[$i] must be an object.")
                        continue
                    }
                    val type = module.optString("type")
                    if (type !in setOf("data", "resources", "script", "client_data", "interface", "world_template", "script")) {
                        issues += ValidationIssue(IssueSeverity.WARNING, path, message = "modules[$i].type '$type' is not a commonly supported Bedrock module type.")
                    }
                    val uuid = module.optString("uuid")
                    registerUuid(uuid, "$path · modules[$i].uuid", path, uuidOwners, issues)
                    val version = intVersion(module.opt("version"))
                    if (version == null) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "modules[$i].version must contain three non-negative integers.")
                    if (uuidPattern.matches(uuid)) internalVersions[uuid.lowercase(Locale.ROOT)] = version.orEmpty()
                    if (type == "script" && module.optString("language") != "javascript") {
                        issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Script module should declare language: javascript.")
                    }
                    if (type == "script" && module.optString("entry").isBlank()) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Script module is missing entry.")
                    }
                }
            }
            val formatVersion = json.optInt("format_version", -1)
            if (formatVersion < 2) issues += ValidationIssue(IssueSeverity.WARNING, path, message = "format_version is missing or older than the common manifest format.")
        }

        manifests.forEach { file ->
            val path = relative(projectRoot, file)
            val json = parsed[file] as? JSONObject ?: return@forEach
            val dependencies = json.optJSONArray("dependencies") ?: return@forEach
            for (i in 0 until dependencies.length()) {
                val dependency = dependencies.optJSONObject(i) ?: continue
                val uuid = dependency.optString("uuid")
                if (uuid.isBlank()) continue
                if (!uuidPattern.matches(uuid)) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i].uuid is not a valid UUID.")
                    continue
                }
                val localVersion = internalVersions[uuid.lowercase(Locale.ROOT)]
                if (localVersion != null) {
                    val required = intVersion(dependency.opt("version"))
                    if (required != null && localVersion != required) {
                        issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Internal dependency $uuid requests version ${required.joinToString(".")}, but this project contains ${localVersion.joinToString(".")}.")
                    }
                } else {
                    issues += ValidationIssue(IssueSeverity.INFO, path, message = "External dependency $uuid was not found in this project; it may be supplied by another installed pack.")
                }
            }
        }

        return issues.distinctBy { listOf(it.severity, it.path, it.line, it.column, it.message) }
            .sortedWith(compareBy<ValidationIssue> { it.severity.ordinal }.thenBy { it.path }.thenBy { it.line ?: 0 })
    }

    private fun registerUuid(
        uuid: String,
        owner: String,
        path: String,
        owners: MutableMap<String, String>,
        issues: MutableList<ValidationIssue>
    ) {
        if (!uuidPattern.matches(uuid)) {
            issues += ValidationIssue(IssueSeverity.ERROR, path, message = "$owner is missing or is not a valid UUID.")
            return
        }
        val normalized = uuid.lowercase(Locale.ROOT)
        val previous = owners.putIfAbsent(normalized, owner)
        if (previous != null) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Duplicate UUID $uuid; also used by $previous.")
    }

    private fun intVersion(value: Any?): List<Int>? {
        val array = value as? JSONArray ?: return null
        if (array.length() != 3) return null
        val values = (0..2).map { i ->
            val n = array.opt(i)
            when (n) {
                is Int -> n
                is Long -> n.toInt().takeIf { it.toLong() == n }
                else -> null
            } ?: return null
        }
        return values.takeIf { it.all { n -> n >= 0 } }
    }

    private fun relative(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')

    private fun locationIssue(
        severity: IssueSeverity,
        path: String,
        source: String,
        message: String,
        index: Int?
    ): ValidationIssue {
        if (index == null || index < 0 || index > source.length) {
            return ValidationIssue(severity, path, message = message)
        }
        val before = source.substring(0, index.coerceAtMost(source.length))
        val line = before.count { it == '\n' } + 1
        val lastNewline = before.lastIndexOf('\n')
        val column = before.length - lastNewline
        return ValidationIssue(severity, path, line, column, message)
    }
}
