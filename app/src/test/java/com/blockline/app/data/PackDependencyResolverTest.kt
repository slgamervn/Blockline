package com.blockline.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackDependencyResolverTest {
    @Test
    fun distinguishesInternalModuleAndExternalDependencies() {
        val root = Files.createTempDirectory("blockline-dependencies-").toFile()
        try {
            val bp = File(root, "BP/manifest.json").apply {
                parentFile?.mkdirs()
                writeText("""{"header":{"name":"Behavior Pack","uuid":"11111111-1111-4111-8111-111111111111","version":[1,0,0]},"modules":[{"type":"data","uuid":"22222222-2222-4222-8222-222222222222","version":[1,0,0]}],"dependencies":[{"module_name":"@minecraft/server","version":"1.0.0"},{"uuid":"33333333-3333-4333-8333-333333333333","version":[1,0,0]}]}""")
            }
            val rp = File(root, "RP/manifest.json").apply {
                parentFile?.mkdirs()
                writeText("""{"header":{"name":"Resource Pack","uuid":"44444444-4444-4444-8444-444444444444","version":[1,0,0]},"modules":[{"type":"resources","uuid":"55555555-5555-4555-8555-555555555555","version":[1,0,0]}],"dependencies":[{"uuid":"11111111-1111-4111-8111-111111111111","version":[2,0,0]}]}""")
            }

            val results = PackDependencyResolver.resolve(root, listOf(bp, rp))
            val runtime = results.single { it.target == "@minecraft/server" }
            val external = results.single { it.target == "33333333-3333-4333-8333-333333333333" }
            val internal = results.single { it.sourcePath == "RP/manifest.json" }

            assertEquals(DependencyKind.EXTERNAL_MODULE, runtime.kind)
            assertEquals("1.0.0", runtime.requestedVersion)
            assertEquals(DependencyKind.EXTERNAL_UUID, external.kind)
            assertEquals(DependencyKind.INTERNAL_PACK, internal.kind)
            assertEquals("Behavior Pack", internal.target)
            assertEquals("BP/manifest.json", internal.targetPath)
            assertEquals("1.0.0", internal.installedVersion)
            assertTrue(internal.versionMismatch)
            assertFalse(external.versionMismatch)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun malformedDependencyIsClassifiedAsInvalid() {
        val root = Files.createTempDirectory("blockline-invalid-dependency-").toFile()
        try {
            val manifest = File(root, "BP/manifest.json").apply {
                parentFile?.mkdirs()
                writeText("""{"header":{"name":"Behavior","uuid":"11111111-1111-4111-8111-111111111111","version":[1,0,0]},"modules":[{"type":"data","uuid":"22222222-2222-4222-8222-222222222222","version":[1,0,0]}],"dependencies":[null,{"module_name":"@minecraft/server"}]}""")
            }
            val results = PackDependencyResolver.resolve(root, listOf(manifest))
            assertEquals(2, results.size)
            assertTrue(results.all { it.kind == DependencyKind.INVALID })
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun parsesSemanticVersionsForFormatVersionThreeDependencies() {
        val root = Files.createTempDirectory("blockline-semver-dependencies-").toFile()
        try {
            val bp = File(root, "BP/manifest.json").apply {
                parentFile?.mkdirs()
                writeText("""{"format_version":3,"header":{"name":"Behavior Pack","uuid":"11111111-1111-4111-8111-111111111111","version":"1.0.0"},"modules":[{"type":"data","uuid":"22222222-2222-4222-8222-222222222222","version":"1.0.0"}]}""")
            }
            val rp = File(root, "RP/manifest.json").apply {
                parentFile?.mkdirs()
                writeText("""{"format_version":3,"header":{"name":"Resource Pack","uuid":"33333333-3333-4333-8333-333333333333","version":"1.0.0"},"modules":[{"type":"resources","uuid":"44444444-4444-4444-8444-444444444444","version":"1.0.0"}],"dependencies":[{"uuid":"11111111-1111-4111-8111-111111111111","version":"2.0.0"}]}""")
            }

            val dependency = PackDependencyResolver.resolve(root, listOf(bp, rp))
                .single { it.sourcePath == "RP/manifest.json" }

            assertEquals(DependencyKind.INTERNAL_PACK, dependency.kind)
            assertEquals("1.0.0", dependency.installedVersion)
            assertTrue(dependency.versionMismatch)
        } finally {
            root.deleteRecursively()
        }
    }

}
