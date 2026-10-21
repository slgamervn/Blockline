package com.blockline.app.data

data class BatchRenameCandidate(val oldPath: String, val newPath: String)
data class BatchRenamePreview(val candidates: List<BatchRenameCandidate>, val conflicts: List<String>)
data class BatchRenameOutcome(
    val changedCount: Int = 0,
    val backupFailed: Boolean = false,
    val restored: Boolean = false,
    val details: String? = null
)

object BatchRenameEngine {
    fun preview(
        filePaths: List<String>,
        existingPaths: Set<String>,
        query: String,
        replacement: String
    ): BatchRenamePreview {
        require(query.isNotBlank()) { "Search text cannot be empty." }
        require('/' !in query && '\\' !in query) { "Search text must stay within file names." }
        require('/' !in replacement && '\\' !in replacement) { "Replacement cannot contain a path separator." }

        val candidates = filePaths.distinct().mapNotNull { oldPath ->
            val fileName = oldPath.substringAfterLast('/')
            if (!fileName.contains(query)) return@mapNotNull null
            val newName = fileName.replace(query, replacement)
            if (newName == fileName) return@mapNotNull null
            require(FileOps.safeName(newName) != null) { "Replacement creates an invalid file name for $oldPath." }
            val parent = oldPath.substringBeforeLast('/', "")
            val newPath = if (parent.isEmpty()) newName else "$parent/$newName"
            BatchRenameCandidate(oldPath, newPath)
        }

        val conflicts = mutableListOf<String>()
        candidates.forEach { candidate ->
            if (candidate.newPath in existingPaths && candidate.newPath != candidate.oldPath) {
                conflicts += "${candidate.oldPath} → ${candidate.newPath}: target already exists"
            }
        }
        candidates.groupBy { it.newPath }.filterValues { it.size > 1 }.forEach { (target, group) ->
            conflicts += "${group.joinToString { it.oldPath }} → $target: multiple files would use the same name"
        }
        return BatchRenamePreview(candidates, conflicts.distinct())
    }
}
