package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectValidatorTest {
    @Test
    fun reportsMalformedDependenciesInsteadOfSilentlyIgnoringThem() {
        val root = Files.createTempDirectory("blockline-validator-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[null,{"version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)
            val messages = issues.map { it.message }

            assertTrue(messages.any { it.contains("dependencies[0] must be an object") })
            assertTrue(messages.any { it.contains("dependencies[1] must specify either uuid or module_name") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reportsDependenciesFieldWithWrongType() {
        val root = Files.createTempDirectory("blockline-validator-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":{}}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(issues.any { it.message == "dependencies must be an array." })
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun acceptsModuleNameDependencyWithStringVersion() {
        val root = Files.createTempDirectory("blockline-validator-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0]},"modules":[{"type":"script","language":"javascript","entry":"scripts/main.js","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"module_name":"@minecraft/server","version":"2.0.0"}]}"""
            )
            File(pack, "scripts").mkdirs()
            File(pack, "scripts/main.js").writeText("")

            val issues = ProjectValidator.validate(root)
            assertTrue(issues.any { it.severity == IssueSeverity.INFO && it.message.contains("Module dependency '@minecraft/server'") })
            assertTrue(issues.none { it.severity == IssueSeverity.ERROR && it.message.contains("dependencies[0]") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsNullOrNonStringModuleDependencyVersion() {
        val root = Files.createTempDirectory("blockline-validator-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"module_name":"@minecraft/server","version":null},{"module_name":"@minecraft/server","version":[2,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)
            assertTrue(issues.any { it.message == "dependencies[0].version is required when module_name is used." })
            assertTrue(issues.any { it.message == "dependencies[1].version must be a non-empty string when module_name is used." })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsSchemaSupportedStringVersionForUuidDependency() {
        val root = Files.createTempDirectory("blockline-validator-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"uuid":"12345678-1234-1234-1234-123456789abd","version":"1.0.0"}]}"""
            )

            val issues = ProjectValidator.validate(root)
            assertTrue(issues.none { it.message.contains("Dependency 12345678-1234-1234-1234-123456789abd has an invalid three-part") })
            assertTrue(issues.none { it.message.contains("Internal dependency 12345678-1234-1234-1234-123456789abd requests version") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservesLineAndColumnForBomPrefixedJsonDiagnostics() {
        val root = Files.createTempDirectory("blockline-bom-").toFile()
        try {
            val source = "\uFEFF{\n  \"value\": 1,\n  \"value\": 2\n}"
            File(root, "settings.json").writeText(source)
            val issue = ProjectValidator.validate(root).single { it.message.contains("Duplicate object key") }
            assertEquals(3, issue.line)
            assertEquals(10, issue.column)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTrailingCommasAndReportsTheAffectedLine() {
        val root = Files.createTempDirectory("blockline-trailing-comma-").toFile()
        try {
            File(root, "settings.json").writeText("{\n  \"value\": 1,\n}")
            val issue = ProjectValidator.validate(root).single { it.message.contains("Trailing commas") }
            assertEquals(3, issue.line)
            assertTrue((issue.column ?: 0) > 0)
        } finally {
            root.deleteRecursively()
        }
    }

}
