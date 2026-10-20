package com.blockline.app.ui

internal fun findWrappedMatch(source: String, query: String, startAt: Int): Int {
    if (query.isEmpty() || source.isEmpty()) return -1
    val start = startAt.coerceIn(0, source.length)
    return source.indexOf(query, start, ignoreCase = true)
        .takeIf { it >= 0 }
        ?: source.indexOf(query, 0, ignoreCase = true)
}
