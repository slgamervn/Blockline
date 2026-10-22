package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectValidatorDependencyEdgeCasesTest {
    @Test
    fun warnsWhenInternalDependencyRequestsDifferentVersion() {
        val root = Files.createTempDirectory("blockline-internal-dependency-version-").toFile()
        try {
            val behaviorPack = File(root, "BP").apply { mkdirs() }
            val resourcePack = File(root, "RP").apply { mkdirs() }
            File(behaviorPack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}],"dependencies":[{"uuid":"22345678-1234-1234-1234-123456789abc","version":[2,0,0]}]}"""
            )
            File(resourcePack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Resource Pack","description":"Test pack","uuid":"32345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"resources","uuid":"22345678-1234-1234-1234-123456789abc","version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(
                "Expected an internal dependency version mismatch warning, got: $issues",
                issues.any {
                    it.severity == IssueSeverity.WARNING &&
                        it.message.contains("Internal dependency 22345678-1234-1234-1234-123456789abc requests version 2.0.0") &&
                        it.message.contains("this project contains 1.0.0")
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDuplicateUuidAcrossPackManifestsCaseInsensitively() {
        val root = Files.createTempDirectory("blockline-duplicate-manifest-uuid-").toFile()
        try {
            val behaviorPack = File(root, "BP").apply { mkdirs() }
            val resourcePack = File(root, "RP").apply { mkdirs() }
            File(behaviorPack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"data","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )
            File(resourcePack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Resource Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789ABC","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"resources","uuid":"22345678-1234-1234-1234-123456789abc","version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(
                "Expected duplicate UUID diagnostic, got: $issues",
                issues.any {
                    it.severity == IssueSeverity.ERROR &&
                        it.message.lowercase().contains("duplicate uuid 12345678-1234-1234-1234-123456789abc")
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
