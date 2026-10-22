package com.blockline.app.data

internal object SemanticVersion {
    private val pattern = Regex(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$"""
    )

    fun isValid(value: String): Boolean {
        val match = pattern.matchEntire(value) ?: return false
        val prerelease = match.groups[4]?.value ?: return true
        return prerelease.split('.').all { identifier ->
            !identifier.all { it in '0'..'9' } ||
                identifier.length == 1 ||
                !identifier.startsWith('0')
        }
    }

    fun coreComponents(value: String): List<Int>? {
        if (!isValid(value)) return null
        val core = value.substringBefore("+").substringBefore("-").split(".")
        if (core.size != 3) return null
        return core.map { it.toIntOrNull() ?: return null }
    }
}
