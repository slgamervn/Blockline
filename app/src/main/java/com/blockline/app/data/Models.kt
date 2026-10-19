package com.blockline.app.data

import java.io.File

data class Project(val id: String, val name: String, val createdAt: Long, val lastOpened: Long)

data class BackupInfo(val file: File, val time: Long)

enum class ExportMode { BP, RP, BOTH }

class NothingToExport : Exception()

data class ManifestInfo(
    val dirName: String,
    val name: String,
    val description: String,
    val uuid: String,
    val version: String,
    val minEngine: String,
    val formatVersion: String,
    val modules: List<String>,
    val dependencies: List<String>,
    val error: String?
)

val TEXT_EXT = setOf(
    "json", "json5", "js", "mjs", "ts", "mcfunction", "lang", "txt", "md", "material", "properties",
    "xml", "csv", "tsv", "fragment", "vertex", "glsl", "hlsl", "mcmeta", "cfg", "yaml", "yml",
    "html", "css", "geo", "toml", "ini", "log"
)
