package com.blockline.app.data

object JsonTools {

    /** Replaces // and /* */ comments with nothing (keeps newlines so line numbers stay valid). */
    fun stripComments(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        var inStr = false
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                sb.append(c)
                if (c == '\\' && i + 1 < s.length) {
                    sb.append(s[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') inStr = false
                i++
            } else if (c == '"') {
                inStr = true
                sb.append(c)
                i++
            } else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                while (i < s.length && s[i] != '\n') i++
            } else if (c == '/' && i + 1 < s.length && s[i + 1] == '*') {
                val start = i
                i += 2
                while (i < s.length && !(s[i] == '*' && i + 1 < s.length && s[i + 1] == '/')) i++
                if (i >= s.length) {
                    sb.append(s.substring(start))
                    break
                }
                val comment = s.substring(start, i + 2)
                comment.forEach { if (it == '\n') sb.append('\n') }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Pretty-prints JSON without removing comments. Works on text that is not strictly valid JSON. */
    fun format(s: String, unit: String = "  "): String {
        val out = StringBuilder()
        var depth = 0
        var i = 0
        var lineStart = true

        fun nl() {
            out.append('\n')
            lineStart = true
        }

        fun emit(t: String) {
            if (lineStart) {
                repeat(depth) { out.append(unit) }
                lineStart = false
            }
            out.append(t)
        }

        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> {
                    val st = i
                    i++
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\') i++
                        i++
                    }
                    i = minOf(i + 1, s.length)
                    emit(s.substring(st, i))
                }
                c == '/' && i + 1 < s.length && s[i + 1] == '/' -> {
                    val st = i
                    while (i < s.length && s[i] != '\n') i++
                    if (!lineStart) out.append(' ')
                    emit(s.substring(st, i).trimEnd())
                    nl()
                }
                c == '/' && i + 1 < s.length && s[i + 1] == '*' -> {
                    val st = i
                    i += 2
                    while (i < s.length && !(s[i] == '*' && i + 1 < s.length && s[i + 1] == '/')) i++
                    i = minOf(i + 2, s.length)
                    if (!lineStart) out.append(' ')
                    emit(s.substring(st, i))
                    nl()
                }
                c == '{' || c == '[' -> {
                    emit(c.toString())
                    depth++
                    nl()
                    i++
                }
                c == '}' || c == ']' -> {
                    depth = maxOf(0, depth - 1)
                    if (!lineStart) nl()
                    emit(c.toString())
                    i++
                }
                c == ',' -> {
                    emit(",")
                    i++
                    nl()
                }
                c == ':' -> {
                    out.append(": ")
                    i++
                    while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
                }
                c.isWhitespace() -> i++
                else -> {
                    val st = i
                    while (i < s.length && !s[i].isWhitespace() && s[i] !in ",:]}{[\"/") i++
                    if (i == st) i++
                    emit(s.substring(st, i))
                }
            }
        }
        return out.toString().trimEnd() + "\n"
    }
}
