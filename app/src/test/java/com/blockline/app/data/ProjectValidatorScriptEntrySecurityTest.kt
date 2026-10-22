package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectValidatorScriptEntrySecurityTest {
    @Test
    fun rejectsScriptEntryThatEscapesPackDirectory() {
        val root = Files.createTempDirectory("blockline-script-entry-escape-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(root, "outside.js").writeText("export {};")
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"12345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"script","language":"javascript","entry":"../outside.js","uuid":"12345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(
                "Expected script entry escaping pack root to be rejected, got: $issues",
                issues.any {
                    it.severity == IssueSeverity.ERROR &&
                        it.message.contains("Script entry '../outside.js' does not resolve to a file inside this pack.")
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsScriptEntryThatDoesNotExist() {
        val root = Files.createTempDirectory("blockline-script-entry-missing-").toFile()
        try {
            val pack = File(root, "BP").apply { mkdirs() }
            File(pack, "manifest.json").writeText(
                """{"format_version":2,"header":{"name":"Behavior Pack","description":"Test pack","uuid":"22345678-1234-1234-1234-123456789abc","version":[1,0,0],"min_engine_version":[1,20,0]},"modules":[{"type":"script","language":"javascript","entry":"scripts/main.js","uuid":"22345678-1234-1234-1234-123456789abd","version":[1,0,0]}]}"""
            )

            val issues = ProjectValidator.validate(root)

            assertTrue(
                "Expected missing script entry to be rejected, got: $issues",
                issues.any {
                    it.severity == IssueSeverity.ERROR &&
                        it.message.contains("Script entry 'scripts/main.js' does not resolve to a file inside this pack.")
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
