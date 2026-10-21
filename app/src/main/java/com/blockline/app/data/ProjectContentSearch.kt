package com.blockline.app.data

import java.util.Locale

object ProjectContentSearch {
    fun search(
        repo: ProjectRepository,
        projectId: String,
        query: String,
        filter: SearchFileFilter
    ): List<ContentSearchResult> {
        if (query.isBlank()) return emptyList()
        return repo.allFiles(projectId).asSequence()
            .filter { file ->
                file.length() <= 1024L * 1024L &&
                    file.extension.lowercase(Locale.ROOT) in TEXT_EXT &&
                    filter.matches(file, repo.relative(projectId, file))
            }
            .flatMap { file ->
                val path = repo.relative(projectId, file)
                file.useLines(Charsets.UTF_8) { lines ->
                    lines.mapIndexedNotNull { index, line ->
                        if (line.contains(query, ignoreCase = true)) {
                            ContentSearchResult(path, index + 1, line.trim().take(200))
                        } else null
                    }.toList().asSequence()
                }
            }
            .take(500)
            .toList()
    }
}
