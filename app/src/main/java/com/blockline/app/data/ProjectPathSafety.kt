package com.blockline.app.data

import java.io.File

internal fun resolveProjectDirectory(root: File, id: String): File {
    require(id.isNotBlank() && id != "." && id != ".." &&
        '/' !in id && '\\\\' !in id
    ) {
        "Invalid project ID"
    }
    val canonicalRoot = root.canonicalFile
    val candidate = File(canonicalRoot, id).canonicalFile
    require(candidate.parentFile == canonicalRoot &&
        candidate.path.startsWith(canonicalRoot.path + File.separator)
    ) {
        "Invalid project ID"
    }
    return candidate
}
