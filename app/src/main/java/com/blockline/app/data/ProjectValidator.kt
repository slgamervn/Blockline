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
    private val semanticVersionPattern = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$""")
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
            val uncommented = JsonTools.stripComments(source)
            val withoutBom = if (uncommented.startsWith("\uFEFF")) " " + uncommented.substring(1) else uncommented
            val clean = JsonTools.stripTrailingCommas(withoutBom)
            try {
                val parser = StrictJsonParser(clean)
                parser.parse()
                parser.duplicateKeys.forEach { duplicate ->
                    issues += locationIssue(
                        IssueSeverity.WARNING,
                        path,
                        source,
                        "Invalid JSON: Duplicate object key ${duplicate.rawKey}.",
                        duplicate.position
                    )
                }
                val tokener = JSONTokener(parser.sourceForObjectParser())
                val value = tokener.nextValue()
                val isManifestFile = file.name.equals("manifest.json", true) || file.name.equals("pack_manifest.json", true)
                if (value !is JSONObject && value !is JSONArray && isManifestFile) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, 1, 1, "Manifest JSON root must be an object.")
                } else if (tokener.nextClean() != '\u0000') {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Unexpected content after the JSON value.")
                } else {
                    parsed[file] = value
                }
            } catch (e: StrictJsonException) {
                issues += locationIssue(IssueSeverity.ERROR, path, source, "Invalid JSON: ${e.message}", e.position)
            } catch (e: JSONException) {
                val message = e.message ?: "Invalid JSON syntax."
                val index = Regex("(?i)(?:character|index|position)\\s*(?:at)?\\s*(\\d+)").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
                issues += locationIssue(IssueSeverity.ERROR, path, source, "Invalid JSON: $message", index)
            } catch (e: Exception) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Could not validate JSON: ${e.message ?: "unknown error"}")
            }
        }

        val manifests = files.filter { it.name.equals("manifest.json", true) || it.name.equals("pack_manifest.json", true) }
        if (manifests.isEmpty()) {
            issues += ValidationIssue(IssueSeverity.WARNING, "", message = "No manifest.json or pack_manifest.json was found. This project may not contain a supported Bedrock pack manifest.")
        }

        val uuidOwners = linkedMapOf<String, String>()
        val internalVersions = linkedMapOf<String, List<Int>>()
        manifests.forEach { file ->
            val path = relative(projectRoot, file)
            val json = parsed[file] as? JSONObject
            if (json == null) {
                if (file !in jsonFiles) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Pack manifest is not valid JSON.")
                return@forEach
            }
            val formatNumber = (json.opt("format_version") as? Number)?.toDouble()
            val formatVersion = formatNumber?.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()
            if (formatVersion == null || formatVersion !in setOf(1, 2, 3)) {
                issues += ValidationIssue(IssueSeverity.WARNING, path, message = "format_version is missing or is not a supported integer manifest format version.")
            }

            val header = json.optJSONObject("header")
            if (header == null) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Missing required object: header.")
            } else {
                if (header.optString("name").isBlank()) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "header.name is missing or empty.")
                if (formatVersion != 1 && header.optString("description").isBlank()) issues += ValidationIssue(IssueSeverity.WARNING, path, message = "header.description is missing or empty.")
                if (header.has("pack_scope")) {
                    val scope = header.opt("pack_scope")
                    if (scope !is String || scope !in setOf("global", "world", "any")) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "header.pack_scope must be one of global, world, or any.")
                    }
                }
                val headerUuid = header.optString("uuid")
                registerUuid(headerUuid, "$path · header.uuid", path, uuidOwners, issues)
                val headerVersion = manifestVersion(header.opt("version"), formatVersion)
                if (headerVersion == null) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "header.version must be a three-part integer array for format_version 1 or 2, or a semantic version string for format_version 3.")
                if (uuidPattern.matches(headerUuid)) internalVersions[headerUuid.lowercase(Locale.ROOT)] = headerVersion.orEmpty()
            }

            val modules = json.optJSONArray("modules")
            if (modules == null || modules.length() == 0) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "modules must be a non-empty array.")
            } else {
                val worldTemplateCount = (0 until modules.length()).count { modules.optJSONObject(it)?.optString("type") == "world_template" }
                if (worldTemplateCount > 1) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "A manifest may contain at most one world_template module.")
                }
                for (i in 0 until modules.length()) {
                    val module = modules.optJSONObject(i)
                    if (module == null) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "modules[$i] must be an object.")
                        continue
                    }
                    val type = module.optString("type")
                    if (type !in setOf("data", "resources", "script", "client_data", "interface", "world_template", "skin_pack", "persona_piece")) {
                        issues += ValidationIssue(IssueSeverity.WARNING, path, message = "modules[$i].type '$type' is not a commonly supported Bedrock module type.")
                    }
                    val uuid = module.optString("uuid")
                    registerUuid(uuid, "$path · modules[$i].uuid", path, uuidOwners, issues)
                    val version = manifestVersion(module.opt("version"), formatVersion)
                    if (version == null) issues += ValidationIssue(IssueSeverity.ERROR, path, message = "modules[$i].version must be a three-part integer array for format_version 1 or 2, or a semantic version string for format_version 3.")
                    if (uuidPattern.matches(uuid)) internalVersions[uuid.lowercase(Locale.ROOT)] = version.orEmpty()
                    if (type == "script" && module.optString("language") != "javascript") {
                        issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Script module should declare language: javascript.")
                    }
                    if (type == "script") {
                        val entry = module.optString("entry")
                        if (entry.isBlank()) {
                            issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Script module is missing entry.")
                        } else {
                            val packRoot = file.parentFile?.canonicalFile
                            val entryFile = runCatching { File(packRoot, entry).canonicalFile }.getOrNull()
                            if (packRoot == null || entryFile == null ||
                                !(entryFile.path.startsWith(packRoot.path + File.separator)) ||
                                !entryFile.isFile
                            ) {
                                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "Script entry '$entry' does not resolve to a file inside this pack.")
                            }
                        }
                    }
                }
            }
            val engineValue = header?.opt("min_engine_version")
            if (engineValue == null || engineValue == JSONObject.NULL) {
                if (header != null && formatVersion != 1) {
                    issues += ValidationIssue(IssueSeverity.WARNING, path, message = "header.min_engine_version is not declared; compatibility with the target Minecraft version cannot be checked.")
                }
            } else {
                val validEngineVersion = when (formatVersion) {
                    3 -> engineValue is String && semanticVersionComponents(engineValue) != null
                    1, 2 -> intVersion(engineValue) != null
                    else -> intVersion(engineValue) != null || (engineValue is String && semanticVersionComponents(engineValue) != null)
                }
                if (!validEngineVersion) {
                    val expected = if (formatVersion == 3) "a semantic version string" else "three non-negative integers, for example [1, 21, 0]"
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "header.min_engine_version must contain $expected.")
                }
            }

            val packRoot = file.parentFile
            val hasPackIcon = packRoot?.listFiles()?.any {
                it.isFile && it.name.equals("pack_icon.png", ignoreCase = true)
            } == true
            if (!hasPackIcon) {
                issues += ValidationIssue(IssueSeverity.WARNING, path, message = "pack_icon.png is missing from this pack folder.")
            }
        }

        manifests.forEach { file ->
            val path = relative(projectRoot, file)
            val json = parsed[file] as? JSONObject ?: return@forEach
            if (!json.has("dependencies")) return@forEach
            val dependencies = json.optJSONArray("dependencies")
            if (dependencies == null) {
                issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies must be an array.")
                return@forEach
            }
            for (i in 0 until dependencies.length()) {
                val dependency = dependencies.optJSONObject(i)
                if (dependency == null) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i] must be an object.")
                    continue
                }
                val uuid = dependency.optString("uuid").trim()
                val moduleName = dependency.optString("module_name").trim()
                if (uuid.isNotBlank() && moduleName.isNotBlank()) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i] cannot specify both uuid and module_name; use only one identifier.")
                    continue
                }
                if (uuid.isBlank()) {
                    if (moduleName.isBlank()) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i] must specify either uuid or module_name.")
                    } else if (!dependency.has("version") || dependency.isNull("version")) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i].version is required when module_name is used.")
                    } else if (dependency.opt("version") !is String || dependency.optString("version").isBlank()) {
                        issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i].version must be a non-empty string when module_name is used.")
                    } else {
                        issues += ValidationIssue(IssueSeverity.INFO, path, message = "Module dependency '$moduleName' is declared; its availability is determined by the Minecraft runtime.")
                    }
                    continue
                }
                if (!uuidPattern.matches(uuid)) {
                    issues += ValidationIssue(IssueSeverity.ERROR, path, message = "dependencies[$i].uuid is not a valid UUID.")
                    continue
                }
                val versionValue = dependency.opt("version")
                val localVersion = internalVersions[uuid.lowercase(Locale.ROOT)]
                if (versionValue == null || versionValue == JSONObject.NULL) {
                    if (localVersion != null) {
                        issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Internal dependency $uuid has no version.")
                    } else {
                        issues += ValidationIssue(IssueSeverity.INFO, path, message = "External dependency $uuid was not found in this project; it may be supplied by another installed pack.")
                    }
                    continue
                }
                when (versionValue) {
                    is JSONArray -> {
                        val required = intVersion(versionValue)
                        if (required == null) {
                            issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Dependency $uuid has an invalid three-part numeric version array.")
                        } else if (localVersion != null && localVersion != required) {
                            issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Internal dependency $uuid requests version ${required.joinToString(".")}, but this project contains ${localVersion.joinToString(".")}.")
                        }
                    }
                    is String -> {
                        if (versionValue.isBlank()) {
                            issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Dependency $uuid has an empty version string.")
                        } else {
                            val required = semanticVersionComponents(versionValue)
                            if (required == null) {
                                issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Dependency $uuid has an invalid semantic version string.")
                            } else if (localVersion != null && localVersion.isNotEmpty() && localVersion != required) {
                                issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Internal dependency $uuid requests version ${required.joinToString(".")}, but this project contains ${localVersion.joinToString(".")}.")
                            }
                        }
                    }
                    else -> issues += ValidationIssue(IssueSeverity.WARNING, path, message = "Dependency $uuid version must be a three-part integer array or a semantic version string.")
                }
                if (localVersion == null) {
                    issues += ValidationIssue(IssueSeverity.INFO, path, message = "External dependency $uuid was not found in this project; it may be supplied by another installed pack.")
                }
            }
        }

        issues += ResourceReferenceChecker.check(projectRoot, files)

        return issues.distinctBy { listOf(it.severity, it.path, it.line, it.column, it.message) }
            .sortedWith(compareBy<ValidationIssue> { it.severity.ordinal }.thenBy { it.path }.thenBy { it.line ?: 0 })
    }

    fun inspectJsonSyntax(source: String): ValidationIssue? {
        val uncommented = JsonTools.stripComments(source)
        val withoutBom = if (uncommented.startsWith("\uFEFF")) " " + uncommented.substring(1) else uncommented
        val clean = JsonTools.stripTrailingCommas(withoutBom)
        return try {
            val parser = StrictJsonParser(clean)
            parser.parse()
            val duplicate = parser.duplicateKeys.firstOrNull()
            if (duplicate != null) {
                locationIssue(
                    IssueSeverity.WARNING,
                    "",
                    source,
                    "Invalid JSON: Duplicate object key ${duplicate.rawKey}.",
                    duplicate.position
                )
            } else null
        } catch (e: StrictJsonException) {
            locationIssue(IssueSeverity.ERROR, "", source, "Invalid JSON: ${e.message}", e.position)
        } catch (e: JSONException) {
            val message = e.message ?: "Invalid JSON syntax."
            val index = Regex("(?i)(?:character|index|position)\\s*(?:at)?\\s*(\\d+)").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
            locationIssue(IssueSeverity.ERROR, "", source, "Invalid JSON: $message", index)
        } catch (e: Exception) {
            ValidationIssue(IssueSeverity.ERROR, "", message = "Invalid JSON: ${e.message ?: "unknown error"}")
        }
    }

    private class StrictJsonException(val position: Int, message: String) : Exception(message)

    private data class DuplicateJsonKey(val position: Int, val rawKey: String)

    private class StrictJsonParser(private val source: String) {
        private var index = 0
        val duplicateKeys = mutableListOf<DuplicateJsonKey>()
        private val shadowedKeyRanges = mutableListOf<Pair<Int, Int>>()

        fun sourceForObjectParser(): String {
            var normalized = source
            var counter = 0
            shadowedKeyRanges.sortedByDescending { it.first }.forEach { (start, end) ->
                var key = "__blockline_shadow_" + counter++ + "__"
                while (source.contains(JSONObject.quote(key))) key = "__" + key + "__"
                normalized = normalized.substring(0, start) + JSONObject.quote(key) + normalized.substring(end)
            }
            return normalized
        }

        fun parse() {
            whitespace()
            value()
            whitespace()
            if (index != source.length) fail("Unexpected content after the JSON value.")
        }

        private fun value() {
            whitespace()
            if (index >= source.length) fail("Expected a JSON value.")
            when (source[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-' -> numberValue()
                in '0'..'9' -> numberValue()
                else -> fail("Expected a JSON value.")
            }
        }

        private fun objectValue() {
            expect('{')
            whitespace()
            if (take('}')) return
            val keys = HashMap<String, Pair<Int, Int>>()
            while (true) {
                whitespace()
                if (index >= source.length || source[index] != '"') fail("Object keys must be double-quoted strings.")
                val keyStart = index
                val key = stringValue()
                val keyEnd = index
                val rawKey = source.substring(keyStart, keyEnd)
                val previous = keys.put(key, keyStart to keyEnd)
                if (previous != null) {
                    duplicateKeys += DuplicateJsonKey(keyStart, rawKey)
                    shadowedKeyRanges += previous
                }
                whitespace()
                expect(':')
                value()
                whitespace()
                if (take('}')) return
                expect(',')
                whitespace()
                if (index < source.length && source[index] == '}') fail("Trailing commas are not valid JSON.")
            }
        }

        private fun arrayValue() {
            expect('[')
            whitespace()
            if (take(']')) return
            while (true) {
                value()
                whitespace()
                if (take(']')) return
                expect(',')
                whitespace()
                if (index < source.length && source[index] == ']') fail("Trailing commas are not valid JSON.")
            }
        }

        private fun stringValue(): String {
            expect('"')
            val decoded = StringBuilder()
            while (index < source.length) {
                val ch = source[index++]
                when {
                    ch == '"' -> return decoded.toString()
                    ch == '\\' -> {
                        if (index >= source.length) fail("Incomplete escape sequence.")
                        when (source[index++]) {
                            '"' -> decoded.append('"')
                            '\\' -> decoded.append('\\')
                            '/' -> decoded.append('/')
                            'b' -> decoded.append('\b')
                            'f' -> decoded.append('\u000C')
                            'n' -> decoded.append('\n')
                            'r' -> decoded.append('\r')
                            't' -> decoded.append('\t')
                            'u' -> {
                                var codePoint = 0
                                repeat(4) {
                                    if (index >= source.length) fail("Invalid Unicode escape.")
                                    val digit = source[index].digitToIntOrNull(16)
                                        ?: fail("Invalid Unicode escape.")
                                    codePoint = codePoint * 16 + digit
                                    index++
                                }
                                decoded.append(codePoint.toChar())
                            }
                            else -> fail("Invalid escape sequence.")
                        }
                    }
                    ch.code < 0x20 -> fail("Unescaped control character in string.")
                    else -> decoded.append(ch)
                }
            }
            fail("Unterminated string.")
        }

        private fun numberValue() {
            if (take('-') && index >= source.length) fail("Incomplete number.")
            if (take('0')) {
                if (index < source.length && source[index] in '0'..'9') fail("Leading zeros are not valid JSON numbers.")
            } else {
                if (index >= source.length || source[index] !in '1'..'9') fail("Invalid number.")
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (take('.')) {
                if (index >= source.length || source[index] !in '0'..'9') fail("Expected digits after decimal point.")
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                index++
                if (index < source.length && source[index] in "+-") index++
                if (index >= source.length || source[index] !in '0'..'9') fail("Expected exponent digits.")
                while (index < source.length && source[index] in '0'..'9') index++
            }
        }

        private fun literal(expected: String) {
            if (!source.startsWith(expected, index)) fail("Expected '$expected'.")
            index += expected.length
        }

        private fun whitespace() {
            while (index < source.length && source[index] in charArrayOf(' ', '\t', '\r', '\n')) index++
        }

        private fun take(ch: Char): Boolean {
            if (index < source.length && source[index] == ch) {
                index++
                return true
            }
            return false
        }

        private fun expect(ch: Char) {
            if (!take(ch)) fail("Expected '$ch'.")
        }

        private fun fail(message: String): Nothing = throw StrictJsonException(index, message)
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

    private fun manifestVersion(value: Any?, formatVersion: Int?): List<Int>? = when (formatVersion) {
        1, 2 -> intVersion(value)
        3 -> (value as? String)?.let(::semanticVersionComponents)
        else -> intVersion(value) ?: (value as? String)?.let(::semanticVersionComponents)
    }

    private fun semanticVersionComponents(value: String): List<Int>? {
        if (!semanticVersionPattern.matches(value)) return null
        val core = value.substringBefore("+").substringBefore("-").split(".")
        val major = core.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = core.getOrNull(1)?.toIntOrNull() ?: return null
        val patch = core.getOrNull(2)?.toIntOrNull() ?: return null
        return listOf(major, minor, patch)
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
