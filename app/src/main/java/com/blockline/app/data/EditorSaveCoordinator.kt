package com.blockline.app.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EditorSaveCoordinator {
    private val writeMutex = Mutex()

    suspend fun write(
        projectRoot: File,
        target: File,
        value: String,
        suffix: String = ".saving"
    ) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val canonicalRoot = projectRoot.canonicalFile.toPath()
                val canonicalTarget = target.canonicalFile
                require(canonicalTarget.toPath().startsWith(canonicalRoot)) {
                    "Unsafe file path."
                }
                check(canonicalTarget.isFile) {
                    "File no longer exists."
                }
                AtomicFileWriter.write(canonicalTarget, value, suffix)
            }
        }
    }
}
