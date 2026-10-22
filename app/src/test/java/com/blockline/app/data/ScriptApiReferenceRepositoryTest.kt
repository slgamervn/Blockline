package com.blockline.app.data

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptApiReferenceRepositoryTest {
    @Test
    fun allowsOnlyTrustedHttpsHosts() {
        assertTrue(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("https://registry.npmjs.org/@minecraft%2fserver")))
        assertTrue(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("https://cdn.jsdelivr.net/npm/@minecraft/server@2.9.0/index.d.ts")))
        assertTrue(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("https://DATA.JSDELIVR.NET/v1/package/npm/example")))
        assertFalse(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("http://registry.npmjs.org/package")))
        assertFalse(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("https://example.invalid/package")))
        assertFalse(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("https://registry.npmjs.org@example.invalid/package")))
        assertFalse(ScriptApiReferenceRepository.isAllowedSourceUrl(URL("https://user@registry.npmjs.org/package")))
    }
}
