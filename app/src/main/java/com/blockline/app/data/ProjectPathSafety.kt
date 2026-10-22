package com.blockline.app.data

import java.io.File

internal fun resolveProjectDirectory(root: File, id: String): File {
    val canonicalRoot = root.canonicalFile
    val candidate = File(canonicalRoot, id).canonicalFile
    require(candidate.path.startsWith(canonicalRoot.path + File.separator)) {
        "Invalid project ID"
    }
    return candidate
}
