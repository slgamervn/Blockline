package com.blockline.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class DependencyKind { INTERNAL_PACK, EXTERNAL_MODULE, EXTERNAL_UUID, INVALID }

data class PackDependency(
    val sourcePath: String,
    val sourceName: String,
    val target: String,
    val requestedVersion: String? = null,
    val kind: DependencyKind,
    val targetPath: String? = null,
    val installedVersion: String? = null,
    val versionMismatch: Boolean = false
)

internal object PackDependencyResolver {
    private val uuidPattern = Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val manifestNames = setOf("manifest.json", "pack_manifest.json")
    private data class Identity(val name: String, val path: String, val version: List<Int>?)

    fun resolve(projectRoot: File, files: List<File>): List<PackDependency> {
        if (!projectRoot.isDirectory) return emptyList()
        val root = runCatching { projectRoot.canonicalFile }.getOrNull() ?: return emptyList()
        val manifests = files.asSequence()
            .filter { it.isFile && it.name.lowercase(Locale.ROOT) in manifestNames }
            .filter { runCatching { it.canonicalFile.toPath().startsWith(root.toPath()) }.getOrDefault(false) }
            .sortedBy { relative(root, it) }
            .mapNotNull { file ->
                runCatching { file to JSONObject(JsonTools.stripComments(file.readText(Charsets.UTF_8))) }.getOrNull()
            }.toList()

        val identities = linkedMapOf<String, Identity>()
        manifests.forEach { (file, json) ->
            val header = json.optJSONObject("header") ?: return@forEach
            val path = relative(root, file)
            val name = header.optString("name").trim().ifBlank { file.parentFile?.name ?: path }
            val identity = Identity(name, path, intVersion(header.opt("version")))
            addIdentity(identities, header.optString("uuid"), identity)
            val modules = json.optJSONArray("modules") ?: return@forEach
            for (i in 0 until modules.length()) {
                val module = modules.optJSONObject(i) ?: continue
                addIdentity(identities, module.optString("uuid"), identity.copy(version = intVersion(module.opt("version"))))
            }
        }

        val result = mutableListOf<PackDependency>()
        manifests.forEach { (file, json) ->
            val path = relative(root, file)
            val sourceName = json.optJSONObject("header")?.optString("name")?.trim()
                ?.ifBlank { null } ?: file.parentFile?.name ?: path
            val value = json.opt("dependencies")
            if (value == null) return@forEach
            val dependencies = value as? JSONArray
            if (dependencies == null) {
                result += PackDependency(path, sourceName, "dependencies", kind = DependencyKind.INVALID)
                return@forEach
            }
            for (i in 0 until dependencies.length()) {
                val entry = dependencies.optJSONObject(i)
                if (entry == null) {
                    result += PackDependency(path, sourceName, "Dependency #${i + 1}", kind = DependencyKind.INVALID)
                    continue
                }
                val uuid = entry.optString("uuid").trim()
                val moduleName = entry.optString("module_name").trim()
                val rawVersion = entry.opt("version")
                val version = versionText(rawVersion)
                if (uuid.isBlank()) {
                    when {
                        moduleName.isBlank() -> result += PackDependency(path, sourceName, "Dependency #${i + 1}", version, DependencyKind.INVALID)
                        rawVersion !is String || rawVersion.isBlank() -> result += PackDependency(path, sourceName, moduleName, version, DependencyKind.INVALID)
                        else -> result += PackDependency(path, sourceName, moduleName, version, DependencyKind.EXTERNAL_MODULE)
                    }
                    continue
                }
                if (!uuidPattern.matches(uuid)) {
                    result += PackDependency(path, sourceName, uuid, version, DependencyKind.INVALID)
                    continue
                }
                val installed = identities[uuid.lowercase(Locale.ROOT)]
                if (installed == null) {
                    result += PackDependency(path, sourceName, uuid, version, DependencyKind.EXTERNAL_UUID)
                    continue
                }
                val requestedNumeric = intVersion(rawVersion)
                result += PackDependency(
                    sourcePath = path,
                    sourceName = sourceName,
                    target = installed.name,
                    requestedVersion = version,
                    kind = DependencyKind.INTERNAL_PACK,
                    targetPath = installed.path,
                    installedVersion = installed.version?.joinToString("."),
                    versionMismatch = requestedNumeric != null && installed.version != null && requestedNumeric != installed.version
                )
            }
        }
        return result.distinct().sortedWith(compareBy<PackDependency> { it.sourcePath }.thenBy { it.target })
    }

    private fun addIdentity(target: MutableMap<String, Identity>, uuid: String, identity: Identity) {
        if (uuidPattern.matches(uuid)) target.putIfAbsent(uuid.lowercase(Locale.ROOT), identity)
    }

    private fun versionText(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is JSONArray -> intVersion(value)?.joinToString(".")
        else -> null
    }

    private fun intVersion(value: Any?): List<Int>? {
        val array = value as? JSONArray ?: return null
        if (array.length() != 3) return null
        val values = (0..2).map { i ->
            when (val n = array.opt(i)) {
                is Int -> n
                is Long -> n.toInt().takeIf { it.toLong() == n }
                else -> null
            } ?: return null
        }
        return values.takeIf { it.all { n -> n >= 0 } }
    }

    private fun relative(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')
}
