package com.blockline.app.data

import java.io.File
import java.nio.file.Files
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
}
