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

    fun isStable(value: String): Boolean =
        isValid(value) && !value.substringBefore("+").contains("-")

    /** Returns a positive value when [left] has higher SemVer precedence. */
    fun compare(left: String, right: String): Int {
        if (!isValid(left) || !isValid(right)) return left.compareTo(right)

        val leftCore = left.substringBefore("+").substringBefore("-").split(".")
        val rightCore = right.substringBefore("+").substringBefore("-").split(".")
        for (index in 0..2) {
            val comparison = compareNumeric(leftCore[index], rightCore[index])
            if (comparison != 0) return comparison
        }

        val leftPrerelease = left.substringBefore("+").substringAfter("-", "")
        val rightPrerelease = right.substringBefore("+").substringAfter("-", "")
        if (leftPrerelease.isEmpty() && rightPrerelease.isEmpty()) return 0
        if (leftPrerelease.isEmpty()) return 1
        if (rightPrerelease.isEmpty()) return -1

        val leftParts = leftPrerelease.split(".")
        val rightParts = rightPrerelease.split(".")
        for (index in 0 until minOf(leftParts.size, rightParts.size)) {
            val a = leftParts[index]
            val b = rightParts[index]
            val aNumeric = a.all { it in '0'..'9' }
            val bNumeric = b.all { it in '0'..'9' }
            val comparison = when {
                aNumeric && bNumeric -> compareNumeric(a, b)
                aNumeric -> -1
                bNumeric -> 1
                else -> a.compareTo(b)
            }
            if (comparison != 0) return comparison
        }
        return leftParts.size.compareTo(rightParts.size)
    }

    private fun compareNumeric(left: String, right: String): Int {
        if (left.length != right.length) return left.length.compareTo(right.length)
        return left.compareTo(right)
    }
}
