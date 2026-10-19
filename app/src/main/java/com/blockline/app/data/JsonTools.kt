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
                i += 2
                while (i < s.length && !(s[i] == '*' && i + 1 < s.length && s[i + 1] == '/')) {
                    if (s[i] == '\n') sb.append('\n')
                    i++
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
