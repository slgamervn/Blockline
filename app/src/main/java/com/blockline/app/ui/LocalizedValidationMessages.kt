package com.blockline.app.ui

import android.content.Context
import com.blockline.app.R

internal fun localizeValidationMessage(context: Context, message: String): String {
    fun text(id: Int, vararg args: Any): String = context.getString(id, *args)
    fun match(pattern: String): List<String>? = Regex(pattern).matchEntire(message)?.groupValues

    return when {
        message == "Project folder is missing." -> text(R.string.validation_msg_project_folder_missing)
        message == "JSON root must be an object or array." -> text(R.string.validation_msg_json_root)
        message == "Unexpected content after the JSON value." -> text(R.string.validation_msg_unexpected_json)
        message == "Invalid JSON syntax." -> text(R.string.validation_msg_invalid_json_syntax)
        message == "No manifest.json or pack_manifest.json was found. This project may not contain a supported Bedrock pack manifest." -> text(R.string.validation_msg_manifest_missing)
        message == "Pack manifest is not valid JSON." -> text(R.string.validation_msg_manifest_invalid)
        message == "Missing required object: header." -> text(R.string.validation_msg_header_missing)
        message == "header.name is missing or empty." -> text(R.string.validation_msg_header_name)
        message == "header.description is missing or empty." -> text(R.string.validation_msg_header_description)
        message == "header.version must contain three non-negative integers." -> text(R.string.validation_msg_header_version)
        message == "modules must be a non-empty array." -> text(R.string.validation_msg_modules_empty)
        message == "Script module should declare language: javascript." -> text(R.string.validation_msg_script_language)
        message == "Script module is missing entry." -> text(R.string.validation_msg_script_entry_missing)
        message == "format_version is missing or older than the common manifest format." -> text(R.string.validation_msg_format_version_old)
        message == "dependencies must be an array." -> text(R.string.validation_msg_dependencies_array)
        message.startsWith("Cannot read file: ") -> text(R.string.validation_msg_cannot_read_file, message.removePrefix("Cannot read file: "))
        message.startsWith("Invalid JSON: ") -> {
            val detail = message.removePrefix("Invalid JSON: ")
            val duplicate = Regex("""Duplicate object key (.+)\.""").matchEntire(detail)
            if (duplicate != null) text(R.string.validation_msg_duplicate_json_key, duplicate.groupValues[1].trim('"'))
            else text(R.string.validation_msg_invalid_json, detail)
        }
        message.startsWith("Could not validate JSON: ") -> text(R.string.validation_msg_cannot_validate_json, message.removePrefix("Could not validate JSON: "))
        match("""modules\[(\d+)] must be an object\.""") != null -> text(R.string.validation_msg_module_object, match("""modules\[(\d+)] must be an object\.""")!![1].toInt())
        match("""modules\[(\d+)]\.type '([^']*)' is not a commonly supported Bedrock module type\.""") != null -> {
            val m = match("""modules\[(\d+)]\.type '([^']*)' is not a commonly supported Bedrock module type\.""")!!
            text(R.string.validation_msg_module_type, m[1].toInt(), m[2])
        }
        match("""modules\[(\d+)]\.version must contain three non-negative integers\.""") != null -> text(R.string.validation_msg_module_version, match("""modules\[(\d+)]\.version must contain three non-negative integers\.""")!![1].toInt())
        match("""Script entry '([^']*)' does not resolve to a file inside this pack\.""") != null -> text(R.string.validation_msg_script_entry_unresolved, match("""Script entry '([^']*)' does not resolve to a file inside this pack\.""")!![1])
        match("""dependencies\[(\d+)] must be an object\.""") != null -> text(R.string.validation_msg_dependency_object, match("""dependencies\[(\d+)] must be an object\.""")!![1].toInt())
        match("""dependencies\[(\d+)] must specify either uuid or module_name\.""") != null -> text(R.string.validation_msg_dependency_identity, match("""dependencies\[(\d+)] must specify either uuid or module_name\.""")!![1].toInt())
        match("""dependencies\[(\d+)]\.version is required when module_name is used\.""") != null -> text(R.string.validation_msg_dependency_version_required, match("""dependencies\[(\d+)]\.version is required when module_name is used\.""")!![1].toInt())
        match("""dependencies\[(\d+)]\.version must be a non-empty string when module_name is used\.""") != null -> text(R.string.validation_msg_dependency_version_string, match("""dependencies\[(\d+)]\.version must be a non-empty string when module_name is used\.""")!![1].toInt())
        match("""Module dependency '([^']*)' is declared; its availability is determined by the Minecraft runtime\.""") != null -> text(R.string.validation_msg_module_dependency, match("""Module dependency '([^']*)' is declared; its availability is determined by the Minecraft runtime\.""")!![1])
        match("""dependencies\[(\d+)]\.uuid is not a valid UUID\.""") != null -> text(R.string.validation_msg_dependency_uuid_invalid, match("""dependencies\[(\d+)]\.uuid is not a valid UUID\.""")!![1].toInt())
        match("""Internal dependency (.+) has no version\.""") != null -> text(R.string.validation_msg_internal_dep_no_version, match("""Internal dependency (.+) has no version\.""")!![1])
        match("""External dependency (.+) was not found in this project; it may be supplied by another installed pack\.""") != null -> text(R.string.validation_msg_external_dep_missing, match("""External dependency (.+) was not found in this project; it may be supplied by another installed pack\.""")!![1])
        match("""Dependency (.+) has an invalid three-part numeric version array\.""") != null -> text(R.string.validation_msg_dependency_version_array, match("""Dependency (.+) has an invalid three-part numeric version array\.""")!![1])
        match("""Internal dependency (.+) requests version (.+), but this project contains (.+)\.""") != null -> {
            val m = match("""Internal dependency (.+) requests version (.+), but this project contains (.+)\.""")!!
            text(R.string.validation_msg_internal_dep_mismatch, m[1], m[2], m[3])
        }
        match("""Dependency (.+) has an empty version string\.""") != null -> text(R.string.validation_msg_dependency_version_empty, match("""Dependency (.+) has an empty version string\.""")!![1])
        match("""Dependency (.+) version must be a three-part integer array or a version string\.""") != null -> text(R.string.validation_msg_dependency_version_shape, match("""Dependency (.+) version must be a three-part integer array or a version string\.""")!![1])
        match("""(.+) is missing or is not a valid UUID\.""") != null -> text(R.string.validation_msg_uuid_invalid, match("""(.+) is missing or is not a valid UUID\.""")!![1])
        match("""Duplicate UUID (.+); also used by (.+)\.""") != null -> {
            val m = match("""Duplicate UUID (.+); also used by (.+)\.""")!!
            text(R.string.validation_msg_duplicate_uuid, m[1], m[2])
        }
        match("""Texture reference '([^']*)' does not match a PNG file in this pack\. Verify the path and extension\.""") != null -> text(R.string.validation_msg_texture_reference, match("""Texture reference '([^']*)' does not match a PNG file in this pack\. Verify the path and extension\.""")!![1])
        else -> message
    }
}
