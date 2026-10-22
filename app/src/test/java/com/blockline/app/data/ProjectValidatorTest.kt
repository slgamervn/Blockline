package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun detectsDuplicateKeysAfterUnicodeEscapeDecoding() {
        val root = Files.createTempDirectory("blockline-escaped-duplicate-key-").toFile()
        try {
            val escapedKey = "u" + "0061"
            val source = "{\"a\":1,\"" + "\\" + escapedKey + "\":2}"
            File(root, "settings.json").writeText(source)
            val issue = ProjectValidator.validate(root).single { it.message.contains("Duplicate object key") }
            assertTrue(issue.message.contains("\\" + escapedKey))
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

    @Test
    fun validatesMinimumEngineVersionAndWarnsWhenPackIconIsMissing() {
        val root = Files.createTempDirectory("blockline-manifest-rules-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,21]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(issues.any { it.severity == IssueSeverity.ERROR && it.message.contains("min_engine_version must contain three") })
            assertTrue(issues.any { it.severity == IssueSeverity.WARNING && it.message.contains("pack_icon.png is missing") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsThreePartMinimumEngineVersion() {
        val root = Files.createTempDirectory("blockline-engine-version-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Test","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,21,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(issues.none { it.severity == IssueSeverity.ERROR && it.message.contains("min_engine_version") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsFormatVersionOneSkinPackWithoutVersionTwoHeaderFields() {
        val root = Files.createTempDirectory("blockline-skin-manifest-").toFile()
        try {
            val pack = File(root, "SkinPack").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":1,"header":{"name":"Skin Pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0]},"modules":[{"type":"skin_pack","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )
            File(pack, "pack_icon.png").writeBytes(byteArrayOf(0))

            val issues = ProjectValidator.validate(root)

            assertFalse(issues.any { it.severity == IssueSeverity.ERROR })
            assertTrue(issues.none { it.message.contains("format_version is missing or is not a supported") })
            assertTrue(issues.none { it.message.contains("header.description is missing") })
            assertTrue(issues.none { it.message.contains("header.min_engine_version is not declared") })
            assertTrue(issues.none { it.message.contains("not a commonly supported Bedrock module type") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsFormatVersionThreeSemanticVersionStrings() {
        val root = Files.createTempDirectory("blockline-format-v3-").toFile()
        try {
            val pack = File(root, "RP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":3,"header":{"name":"Resource Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":"1.2.3-beta.1+build.7","min_engine_version":"1.21.0"},"modules":[{"type":"resources","uuid":"12345678-1234-1234-1234-123456789abd","version":"1.2.3"}]}"""
            )
            File(pack, "pack_icon.png").writeBytes(byteArrayOf(0))

            val issues = ProjectValidator.validate(root)

            assertFalse("Unexpected errors: $issues", issues.any { it.severity == IssueSeverity.ERROR })
            assertTrue(issues.none { it.message.contains("format_version is missing or is not a supported") })
            assertTrue(issues.none { it.message.contains("header.version must") })
            assertTrue(issues.none { it.message.contains("modules[0].version must") })
            assertTrue(issues.none { it.message.contains("header.min_engine_version must") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidPackScopeAgainstCreatorToolsRules() {
        val root = Files.createTempDirectory("blockline-pack-scope-").toFile()
        try {
            val pack = File(root, "RP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Resource Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0],"pack_scope":"local"},"modules":[{"type":"resources","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )
            val issue = ProjectValidator.validate(root).single { it.message.contains("header.pack_scope") }
            assertEquals(IssueSeverity.ERROR, issue.severity)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsAllSupportedPackScopeValues() {
        listOf("global", "world", "any").forEach { scope ->
            val root = Files.createTempDirectory("blockline-pack-scope-valid-").toFile()
            try {
                val pack = File(root, "RP").apply { mkdirs() }
                File(pack, "manifest.json").writeText(
                    """{"format_version":2,"header":{"name":"Resource Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0],"pack_scope":"$scope"},"modules":[{"type":"resources","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
                )

                val issues = ProjectValidator.validate(root)

                assertTrue("Supported pack_scope '$scope' was rejected: $issues", issues.none { it.message.contains("header.pack_scope") })
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun rejectsMultipleWorldTemplateModules() {
        val root = Files.createTempDirectory("blockline-world-template-").toFile()
        try {
            val pack = File(root, "WorldTemplate").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Template","description":"Test template","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"world_template","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]},{"type":"world_template","uuid":"12345678-1234-1234-1234-123456789abe","version":[1,0,0]}]}"""
            )
            val issue = ProjectValidator.validate(root).single { it.message.contains("at most one world_template") }
            assertEquals(IssueSeverity.ERROR, issue.severity)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMalformedDependencyUuidAndMissingModuleVersion() {
        val root = Files.createTempDirectory("blockline-invalid-dependency-fields-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"uuid":"not-a-uuid","version":[1,0,0]},{"module_name":"@minecraft/server"}]}"""
            )

            val issues = ProjectValidator.validate(root)
            assertTrue(issues.any { it.message.contains("dependencies[0].uuid is not a valid UUID") && it.severity == IssueSeverity.ERROR })
            assertTrue(issues.any { it.message.contains("dependencies[1].version is required when module_name is used") && it.severity == IssueSeverity.ERROR })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsWellFormedExternalModuleDependencyWithoutTreatingItAsMissingPack() {
        val root = Files.createTempDirectory("blockline-valid-module-dependency-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"script","language":"javascript","entry":"scripts/main.js","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"module_name":"@minecraft/server","version":"2.0.0"}]}"""
            )
            File(pack, "scripts/main.js").apply {
                parentFile?.mkdirs()
                writeText("import {} from '@minecraft/server';")
            }

            val issues = ProjectValidator.validate(root)
            assertFalse("Unexpected errors: $issues", issues.any { it.severity == IssueSeverity.ERROR })
            assertTrue(issues.any { it.message.contains("Module dependency '@minecraft/server' is declared") && it.severity == IssueSeverity.INFO })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDependencyWithBothUuidAndModuleName() {
        val root = Files.createTempDirectory("blockline-dependency-identifiers-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"uuid":"12345678-1234-1234-1234-123456789abe","module_name":"@minecraft/server","version":"2.0.0"}]}"""
            )
            val issue = ProjectValidator.validate(root).single { it.message.contains("cannot specify both uuid and module_name") }
            assertEquals(IssueSeverity.ERROR, issue.severity)
        } finally {
            root.deleteRecursively()
        }
    }

}
