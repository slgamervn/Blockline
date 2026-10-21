package com.blockline.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blockline.app.data.ExportMode
import com.blockline.app.data.SearchFileFilter
import com.blockline.app.data.ContentSearchResult
import com.blockline.app.data.Project
import com.blockline.app.data.ProjectRepository
import com.blockline.app.data.ResourcePreview
import com.blockline.app.data.ResourcePreviewBuilder
import com.blockline.app.data.ProjectValidator
import com.blockline.app.data.PackDependency
import com.blockline.app.data.PackDependencyResolver
import com.blockline.app.data.BatchReplaceEngine
import com.blockline.app.data.BatchReplaceFailure
import com.blockline.app.data.BatchReplaceFilePreview
import com.blockline.app.data.BatchReplaceScanResult
import com.blockline.app.data.BatchRenameCandidate
import com.blockline.app.data.BatchRenameEngine
import com.blockline.app.data.BatchRenameOutcome
import com.blockline.app.data.ValidationIssue
import com.blockline.app.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    val repo = ProjectRepository(app)
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var current by mutableStateOf<Project?>(null)
        private set
    val projectEntries = mutableStateMapOf<String, List<File>>()
    var isIndexingProjectFiles by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.DARK)
    var fontSize by mutableStateOf(14)
    var activeFile by mutableStateOf<String?>(null)
        private set
    var generatedFileToOpen by mutableStateOf<String?>(null)
        private set
    var content by mutableStateOf("")
        private set
    var pendingEditorLine by mutableStateOf<Int?>(null)
        private set
    var readOnly by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isExporting by mutableStateOf(false)
        private set
    var isFileOperationRunning by mutableStateOf(false)
        private set
    var isValidating by mutableStateOf(false)
        private set
    var validationIssues by mutableStateOf<List<ValidationIssue>>(emptyList())
        private set
    var validationCompleted by mutableStateOf(false)
        private set
    var dependencies by mutableStateOf<List<PackDependency>>(emptyList())
        private set
    var isLoadingDependencies by mutableStateOf(false)
        private set
    var dependencyAnalysisCompleted by mutableStateOf(false)
        private set
    var batchReplacePreview by mutableStateOf<List<BatchReplaceFilePreview>>(emptyList())
        private set
    var isPreviewingBatchReplace by mutableStateOf(false)
        private set
    var batchReplacePreviewReady by mutableStateOf(false)
        private set
    var batchReplaceTruncated by mutableStateOf(false)
        private set
    var isApplyingBatchReplace by mutableStateOf(false)
        private set

    val packHealthScore: Int?
        get() = if (!validationCompleted) null else {
            val errors = validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR }
            val warnings = validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING }
            (100 - errors * 12 - warnings * 4).coerceIn(0, 100)
        }
    var previewPath by mutableStateOf<String?>(null)
        private set
    var resourcePreview by mutableStateOf<ResourcePreview?>(null)
        private set
    var isLoadingPreview by mutableStateOf(false)
        private set
    var previewError by mutableStateOf<String?>(null)
        private set
    var isSearchingContent by mutableStateOf(false)
        private set
    var contentSearchResults by mutableStateOf<List<ContentSearchResult>>(emptyList())
        private set
    val openTabs = mutableStateMapOf<String, String>()
    private val undo = mutableStateMapOf<String, MutableList<String>>()
    private val redo = mutableStateMapOf<String, MutableList<String>>()
    private val lastEditAt = mutableMapOf<String, Long>()
    private var contentSearchRequestId = 0
    private var validationRequestId = 0
    private var dependencyRequestId = 0
    private var batchReplaceRequestId = 0
    private var batchReplaceProjectId: String? = null
    private var batchReplaceQuery = ""
    private var batchReplaceReplacement = ""
    private var batchReplaceFilter = SearchFileFilter.ALL
    private var fileIndexRequestId = 0
    private var autoSaveJob: Job? = null

    init { refresh() }

    fun refresh() { projects = repo.list() }

    private fun refreshProjectEntries(projectId: String) {
        if (current?.id != projectId) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { repo.allEntries(projectId) } }
                    .onSuccess { projectEntries[projectId] = it }
            }
            return
        }
        val requestId = ++fileIndexRequestId
        isIndexingProjectFiles = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.allEntries(projectId) } }
            if (requestId != fileIndexRequestId) return@launch
            if (current?.id == projectId) isIndexingProjectFiles = false
            result.onSuccess { projectEntries[projectId] = it }
                .onFailure { if (current?.id == projectId) message = it.message ?: text(R.string.scanning_files) }
        }
    }

    fun loadCurrentProjectDependencies() {
        val project = current
        if (project == null) {
            dependencies = emptyList()
            dependencyAnalysisCompleted = false
            message = text(R.string.select_project_before_validating)
            return
        }
        if (isLoadingDependencies) return
        val requestId = ++dependencyRequestId
        isLoadingDependencies = true
        dependencyAnalysisCompleted = false
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    PackDependencyResolver.resolve(repo.projectDir(project.id), repo.allFiles(project.id))
                }
            }
            if (requestId != dependencyRequestId) return@launch
            isLoadingDependencies = false
            if (current?.id != project.id) return@launch
            result.onSuccess {
                dependencies = it
                dependencyAnalysisCompleted = true
            }.onFailure {
                message = it.message ?: text(R.string.validation_failed)
            }
        }
    }

    fun clearBatchReplacePreview() {
        batchReplaceRequestId++
        batchReplacePreview = emptyList()
        isPreviewingBatchReplace = false
        batchReplacePreviewReady = false
        batchReplaceTruncated = false
        batchReplaceProjectId = null
        batchReplaceQuery = ""
        batchReplaceReplacement = ""
        batchReplaceFilter = SearchFileFilter.ALL
    }

    fun previewBatchReplace(query: String, replacement: String, filter: SearchFileFilter) {
        val project = current ?: return
        if (query.isBlank()) {
            message = text(R.string.batch_replace_empty_query)
            clearBatchReplacePreview()
            return
        }
        if (isFileOperationRunning || isApplyingBatchReplace) return
        if (!saveCurrent(showMessage = false)) return
        clearBatchReplacePreview()
        val requestId = ++batchReplaceRequestId
        val projectId = project.id
        isPreviewingBatchReplace = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val eligible = repo.allFiles(projectId).asSequence().filter { file ->
                        file.length() <= 1024L * 1024L &&
                            file.extension.lowercase(java.util.Locale.ROOT) in com.blockline.app.data.TEXT_EXT &&
                            filter.matches(file, repo.relative(projectId, file))
                    }.toList()
                    val found = mutableListOf<BatchReplaceFilePreview>()
                    var scannedBytes = 0L
                    var scannedFiles = 0
                    var truncated = false
                    for (file in eligible) {
                        val size = file.length()
                        if (scannedFiles >= 5000 || scannedBytes + size > 50L * 1024L * 1024L) {
                            truncated = true
                            break
                        }
                        scannedFiles++
                        scannedBytes += size
                        val source = file.readText(Charsets.UTF_8)
                        val occurrences = BatchReplaceEngine.countOccurrences(source, query)
                        if (occurrences > 0) {
                            found += BatchReplaceFilePreview(
                                path = repo.relative(projectId, file),
                                occurrences = occurrences,
                                sample = BatchReplaceEngine.sampleLine(source, query),
                                fingerprint = BatchReplaceEngine.fingerprint(source)
                            )
                        }
                    }
                    BatchReplaceScanResult(found, truncated)
                }
            }
            if (requestId != batchReplaceRequestId) return@launch
            isPreviewingBatchReplace = false
            if (current?.id != projectId) return@launch
            result.onSuccess { scan ->
                batchReplacePreview = scan.files
                batchReplaceTruncated = scan.truncated
                batchReplacePreviewReady = true
                batchReplaceProjectId = projectId
                batchReplaceQuery = query
                batchReplaceReplacement = replacement
                batchReplaceFilter = filter
                message = text(R.string.batch_replace_preview_found, scan.files.size)
            }.onFailure {
                message = it.message ?: text(R.string.batch_replace_preview_failed)
            }
        }
    }

    fun applyBatchReplace() {
        val project = current ?: return
        if (!batchReplacePreviewReady || batchReplacePreview.isEmpty() || batchReplaceTruncated) return
        if (batchReplaceProjectId != project.id || batchReplaceQuery.isBlank()) {
            clearBatchReplacePreview()
            message = text(R.string.batch_replace_preview_stale)
            return
        }
        if (isFileOperationRunning || isApplyingBatchReplace) return
        if (!saveCurrent(showMessage = false)) return

        val projectId = project.id
        val query = batchReplaceQuery
        val replacement = batchReplaceReplacement
        val preview = batchReplacePreview.toList()
        isApplyingBatchReplace = true
        isFileOperationRunning = true
        autoSaveJob?.cancel()

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    repo.backup(projectId)
                } catch (error: Exception) {
                    return@withContext com.blockline.app.data.BatchReplaceOutcome(
                        failure = BatchReplaceFailure.BACKUP_FAILED,
                        details = error.message
                    )
                }

                data class Prepared(val path: String, val file: File, val content: String, val occurrences: Int)
                val prepared = try {
                    preview.map { item ->
                        val file = repo.resolve(projectId, item.path)
                        check(file.isFile) { "A previewed file no longer exists: ${item.path}" }
                        check(file.length() <= 1024L * 1024L) { "A previewed file exceeded the 1 MB limit: ${item.path}" }
                        check(file.extension.lowercase(java.util.Locale.ROOT) in com.blockline.app.data.TEXT_EXT) {
                            "A previewed file is no longer a supported text file: ${item.path}"
                        }
                        val original = file.readText(Charsets.UTF_8)
                        val actualCount = BatchReplaceEngine.countOccurrences(original, query)
                        check(
                            actualCount == item.occurrences &&
                                actualCount > 0 &&
                                BatchReplaceEngine.fingerprint(original) == item.fingerprint
                        ) {
                            "A previewed file changed since preview: ${item.path}"
                        }
                        Prepared(item.path, file, BatchReplaceEngine.replaceLiteral(original, query, replacement), actualCount)
                    }
                } catch (error: Exception) {
                    return@withContext com.blockline.app.data.BatchReplaceOutcome(
                        failure = BatchReplaceFailure.PREVIEW_STALE,
                        details = error.message
                    )
                }

                var changedCount = 0
                try {
                    prepared.forEach { item ->
                        BatchReplaceEngine.writeAtomically(item.file, item.content)
                        changedCount++
                    }
                    com.blockline.app.data.BatchReplaceOutcome(
                        changedFiles = changedCount,
                        occurrences = prepared.sumOf { it.occurrences },
                        changedContents = prepared.associate { it.path to it.content }
                    )
                } catch (error: Exception) {
                    val restored = runCatching { repo.restoreLatest(projectId) }.getOrDefault(false)
                    com.blockline.app.data.BatchReplaceOutcome(
                        failure = if (restored) BatchReplaceFailure.WRITE_ROLLED_BACK else BatchReplaceFailure.WRITE_ROLLBACK_FAILED,
                        details = error.message
                    )
                }
            }

            isApplyingBatchReplace = false
            isFileOperationRunning = false
            if (current?.id != projectId) return@launch

            if (outcome.failure == null) {
                outcome.changedContents.forEach { (path, value) ->
                    if (openTabs.containsKey(path)) openTabs[path] = value
                    undo.remove(path)
                    redo.remove(path)
                    lastEditAt.remove(path)
                    if (activeFile == path) content = value
                }
                autoSaveJob?.cancel()
                refreshProjectEntries(projectId)
                message = text(R.string.batch_replace_done, outcome.occurrences, outcome.changedFiles)
                clearBatchReplacePreview()
            } else {
                message = when (outcome.failure) {
                    BatchReplaceFailure.BACKUP_FAILED -> text(R.string.batch_replace_backup_failed, outcome.details.orEmpty())
                    BatchReplaceFailure.PREVIEW_STALE -> text(R.string.batch_replace_preview_stale, outcome.details.orEmpty())
                    BatchReplaceFailure.WRITE_ROLLED_BACK -> text(R.string.batch_replace_rolled_back, outcome.details.orEmpty())
                    BatchReplaceFailure.WRITE_ROLLBACK_FAILED -> text(R.string.batch_replace_rollback_failed, outcome.details.orEmpty())
                }
                if (
                    outcome.failure == BatchReplaceFailure.WRITE_ROLLED_BACK ||
                    outcome.failure == BatchReplaceFailure.WRITE_ROLLBACK_FAILED
                ) {
                    autoSaveJob?.cancel()
                    openTabs.clear()
                    undo.clear()
                    redo.clear()
                    lastEditAt.clear()
                    activeFile = null
                    content = ""
                    readOnly = false
                    pendingEditorLine = null
                    refreshProjectEntries(projectId)
                }
            }
        }
    }

    fun applyBatchRename(candidates: List<BatchRenameCandidate>) {
        val project = current ?: return
        if (candidates.isEmpty() || isFileOperationRunning) return
        if (!saveCurrent(showMessage = false)) return

        val projectId = project.id
        isFileOperationRunning = true
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    repo.backup(projectId)
                } catch (error: Exception) {
                    return@withContext BatchRenameOutcome(backupFailed = true, details = error.message)
                }

                val renamed = mutableListOf<Pair<String, String>>()
                try {
                    candidates.forEach { candidate ->
                        check(candidate.oldPath != candidate.newPath) { "A rename target is unchanged." }
                        val source = repo.resolve(projectId, candidate.oldPath)
                        check(source.isFile) { "A previewed file no longer exists: ${candidate.oldPath}" }
                        val target = repo.resolve(projectId, candidate.newPath)
                        check(!target.exists()) { "A rename target now exists: ${candidate.newPath}" }
                        val newFile = com.blockline.app.data.FileOps.rename(source, candidate.newPath.substringAfterLast('/'))
                            ?: error("Could not rename ${candidate.oldPath}")
                        val newPath = repo.relative(projectId, newFile)
                        renamed += candidate.oldPath to newPath
                        repo.remapPathMetadata(projectId, candidate.oldPath, newPath)
                    }
                    BatchRenameOutcome(changedCount = renamed.size)
                } catch (error: Exception) {
                    renamed.asReversed().forEach { (oldPath, newPath) ->
                        runCatching { repo.remapPathMetadata(projectId, newPath, oldPath) }
                    }
                    val restored = runCatching { repo.restoreLatest(projectId) }.getOrDefault(false)
                    BatchRenameOutcome(restored = restored, details = error.message)
                }
            }

            isFileOperationRunning = false
            if (current?.id != projectId) return@launch
            if (outcome.changedCount > 0) {
                candidates.forEach { remapOpenPaths(it.oldPath, it.newPath) }
                refreshProjectEntries(projectId)
                message = text(R.string.batch_rename_done, outcome.changedCount)
            } else if (outcome.backupFailed) {
                message = text(R.string.batch_rename_backup_failed, outcome.details.orEmpty())
            } else {
                autoSaveJob?.cancel()
                openTabs.clear()
                undo.clear()
                redo.clear()
                lastEditAt.clear()
                activeFile = null
                content = ""
                readOnly = false
                pendingEditorLine = null
                refreshProjectEntries(projectId)
                message = if (outcome.restored) {
                    text(R.string.batch_rename_restored, outcome.details.orEmpty())
                } else {
                    text(R.string.batch_rename_restore_failed, outcome.details.orEmpty())
                }
            }
        }
    }

    fun validateCurrentProject() {
        val project = current
        if (project == null) {
            message = text(R.string.select_project_before_validating)
            return
        }
        if (isValidating) return
        val requestId = ++validationRequestId
        isValidating = true
        validationIssues = emptyList()
        validationCompleted = false
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { ProjectValidator.validate(repo.projectDir(project.id)) }
            }
            if (requestId != validationRequestId) return@launch
            isValidating = false
            if (current?.id != project.id) return@launch
            result.onSuccess { issues ->
                validationIssues = issues
                validationCompleted = true
                val errors = issues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR }
                val warnings = issues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING }
                message = text(R.string.validation_finished, errors, warnings, issues.count { it.severity == com.blockline.app.data.IssueSeverity.INFO })
            }.onFailure { message = it.message ?: text(R.string.validation_failed) }
        }
    }

    fun loadResourcePreview(path: String) {
        val project = current ?: return
        previewPath = path
        resourcePreview = null
        previewError = null
        isLoadingPreview = true
        val projectId = project.id
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = repo.resolve(projectId, path)
                    require(file.isFile) { text(R.string.preview_file_missing) }
                    require(file.length() <= 512L * 1024L) { text(R.string.resource_preview_too_large) }
                    ResourcePreviewBuilder.build(path, file.readText(Charsets.UTF_8))
                }
            }
            if (current?.id != projectId || previewPath != path) return@launch
            isLoadingPreview = false
            result.onSuccess { resourcePreview = it }
                .onFailure { previewError = it.message ?: text(R.string.resource_preview_failed) }
        }
    }

    fun clearResourcePreview() {
        previewPath = null
        resourcePreview = null
        previewError = null
        isLoadingPreview = false
    }

    fun searchProjectContentAsync(query: String, filter: SearchFileFilter = SearchFileFilter.ALL) {
        val project = current
        if (project == null || query.isBlank()) return
        val requestId = ++contentSearchRequestId
        isSearchingContent = true
        contentSearchResults = emptyList()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { searchProjectContent(project.id, query, filter) }
            }
            if (requestId != contentSearchRequestId) return@launch
            isSearchingContent = false
            if (current?.id == project.id) {
                result.onSuccess { results ->
                    contentSearchResults = results
                    message = text(R.string.content_search_found, results.size, if (results.size >= 500) text(R.string.first_500_shown) else "")
                }.onFailure { error ->
                    message = error.message ?: text(R.string.search_failed)
                }
            }
        }
    }

    fun clearContentSearch() {
        contentSearchRequestId++
        isSearchingContent = false
        contentSearchResults = emptyList()
    }

    fun searchProjectContent(query: String, filter: SearchFileFilter = SearchFileFilter.ALL): List<ContentSearchResult> {
        val project = current ?: return emptyList()
        return searchProjectContent(project.id, query, filter)
    }

    private fun searchProjectContent(projectId: String, query: String, filter: SearchFileFilter): List<ContentSearchResult> {
        if (query.isBlank()) return emptyList()
        return repo.allFiles(projectId).asSequence()
            .filter { file ->
                file.length() <= 1024L * 1024L &&
                    file.extension.lowercase(java.util.Locale.ROOT) in com.blockline.app.data.TEXT_EXT &&
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
    fun selectProject(p: Project) {
        openFileRequestId++
        isOpeningFile = false
        clearResourcePreview()
        if (current?.id == p.id) {
            repo.touch(p.id)
            refresh()
            refreshProjectEntries(p.id)
            return
        }
        if (!saveCurrent(showMessage = false)) return
        current = p
        clearBatchReplacePreview()
        dependencyRequestId++
        dependencies = emptyList()
        isLoadingDependencies = false
        dependencyAnalysisCompleted = false
        clearContentSearch()
        validationRequestId++
        isValidating = false
        validationIssues = emptyList()
        validationCompleted = false
        repo.touch(p.id)
        refresh()
        refreshProjectEntries(p.id)
        activeFile = null
        content = ""
        openTabs.clear()
        undo.clear()
        redo.clear()
        lastEditAt.clear()
        validationIssues = emptyList()
        validationCompleted = false
        contentSearchResults = emptyList()
    }
    fun createSample() = runCatching { repo.createSample() }.onSuccess {
        refresh(); selectProject(it); message = text(R.string.sample_created)
    }.onFailure { message = it.message ?: text(R.string.sample_create_failed) }

    fun importPack(uri: android.net.Uri) {
        viewModelScope.launch {
            isLoading = true
            try {
                val p = withContext(Dispatchers.IO) { repo.importPack(uri, repo.displayName(uri)) }
                refresh(); selectProject(p); message = text(R.string.imported_project, p.name)
            } catch (e: Exception) { message = e.message ?: text(R.string.import_failed) }
            finally { isLoading = false }
        }
    }
    fun renameProject(p: Project, name: String) = runCatching { repo.rename(p.id, name); refresh(); if (current?.id == p.id) current = repo.list().firstOrNull { it.id == p.id } }.onFailure { message = it.message }
    fun duplicateProject(p: Project) {
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.duplicate(p.id) } }
            isFileOperationRunning = false
            result.onSuccess { refresh(); message = text(R.string.project_duplicated) }
                .onFailure { message = it.message ?: text(R.string.project_duplicated) }
        }
    }
    fun deleteProject(p: Project) {
        if (isFileOperationRunning) return
        openFileRequestId++
        isOpeningFile = false
        if (current?.id == p.id && !saveCurrent(showMessage = false)) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.delete(p.id) } }
            isFileOperationRunning = false
            result.onSuccess {
                if (current?.id == p.id) {
                    autoSaveJob?.cancel()
                    clearResourcePreview()
                    current = null
                    activeFile = null
                    content = ""
                    openTabs.clear()
                    undo.clear()
                    redo.clear()
                    lastEditAt.clear()
                    validationIssues = emptyList()
                    validationCompleted = false
                    contentSearchResults = emptyList()
                    clearBatchReplacePreview()
                    dependencyRequestId++
                    dependencies = emptyList()
                    isLoadingDependencies = false
                    dependencyAnalysisCompleted = false
                    isIndexingProjectFiles = false
                    fileIndexRequestId++
                }
                projectEntries.remove(p.id)
                refresh()
                message = text(R.string.project_deleted)
            }.onFailure { message = it.message ?: text(R.string.project_deleted) }
        }
    }
    private data class OpenedFile(val size: Long, val text: String)
    private var openFileRequestId = 0
    var isOpeningFile by mutableStateOf(false)
        private set

    fun openFile(path: String) = openFile(path, null)

    fun openFile(path: String, line: Int?) {
        val project = current ?: return
        if (activeFile == path && !isOpeningFile) {
            pendingEditorLine = line?.coerceAtLeast(1)
            if (line == null) pendingEditorLine = null
            return
        }

        val requestId = ++openFileRequestId
        val previousPath = activeFile
        val previousContent = content
        val previousReadOnly = readOnly
        if (previousPath != null && !previousReadOnly) openTabs[previousPath] = previousContent
        val buffered = openTabs[path]
        isOpeningFile = true

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (previousPath != null && previousPath != path && !previousReadOnly) {
                        writeSnapshot(project.id, previousPath, previousContent)
                    }
                    val file = repo.resolve(project.id, path)
                    require(file.isFile) { text(R.string.file_not_found) }
                    val size = file.length()
                    require(size <= 5L * 1024L * 1024L) { text(R.string.file_too_large) }
                    val fileText = buffered ?: file.readText(Charsets.UTF_8)
                    repo.addRecentFile(project.id, path)
                    OpenedFile(size, fileText)
                }
            }

            if (requestId != openFileRequestId || current?.id != project.id) return@launch
            isOpeningFile = false
            result.onSuccess { opened ->
                activeFile = path
                readOnly = opened.size > 1024L * 1024L
                content = opened.text
                openTabs[path] = opened.text
                if (buffered == null) {
                    undo[path] = mutableListOf()
                    redo[path] = mutableListOf()
                    lastEditAt.remove(path)
                }
                pendingEditorLine = line?.coerceAtLeast(1)
            }.onFailure {
                pendingEditorLine = null
                activeFile = previousPath
                content = previousContent
                readOnly = previousReadOnly
                message = it.message ?: text(R.string.cannot_open_file)
            }
        }
    }

    private fun writeAtomically(target: File, value: String, suffix: String) {
        val parent = target.parentFile ?: error("Target file has no parent directory.")
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the target directory." }
        val temporary = File(parent, target.name + suffix)
        temporary.writeText(value, Charsets.UTF_8)
        try {
            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun writeSnapshot(projectId: String, path: String, value: String) {
        val projectRoot = repo.projectDir(projectId).canonicalFile
        val target = repo.resolve(projectId, path).canonicalFile
        require(target.toPath().startsWith(projectRoot.toPath())) { "Unsafe file path." }
        check(target.isFile) { text(R.string.file_not_found) }
        writeAtomically(target, value, ".saving")
    }
    fun consumePendingEditorLine() {
        pendingEditorLine = null
    }

    fun updateContent(value: String) {
        val path = activeFile ?: return
        if (readOnly) return
        if (value != content) {
            val now = System.currentTimeMillis()
            val history = undo.getOrPut(path) { mutableListOf() }
            val lastEdit = lastEditAt[path] ?: 0L
            if (lastEdit == 0L || now - lastEdit > 700L) {
                history.add(content)
                if (history.size > 80) history.removeAt(0)
            }
            redo.getOrPut(path) { mutableListOf() }.clear()
            content = value
            openTabs[path] = value
            lastEditAt[path] = now
            scheduleAutoSave(current?.id, path, value)
        }
    }
    private fun scheduleAutoSave(projectId: String?, path: String, value: String) {
        if (projectId == null || value.length > 1_000_000) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(850)
            val paths = runCatching {
                repo.projectDir(projectId).canonicalFile to repo.resolve(projectId, path).canonicalFile
            }.getOrNull() ?: return@launch
            val (projectRoot, target) = paths
            if (current?.id != projectId || activeFile != path || readOnly || openTabs[path] != value) return@launch
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    require(target.toPath().startsWith(projectRoot.toPath())) { "Unsafe file path." }
                    check(target.isFile) { "File no longer exists." }
                    writeAtomically(target, value, ".autosaving")
                }
            }
            if (result.isFailure && current?.id == projectId && activeFile == path) {
                message = result.exceptionOrNull()?.message ?: text(R.string.save_failed)
            }
        }
    }
    fun saveCurrent(showMessage: Boolean = true): Boolean {
        val p = current ?: return true
        val path = activeFile ?: return true
        if (readOnly) {
            if (showMessage) message = text(R.string.save_readonly)
            return true
        }
        autoSaveJob?.cancel()
        return runCatching {
            val f = repo.resolve(p.id, path)
            writeAtomically(f, content, ".saving")
            if (showMessage) message = text(R.string.saved_path, path)
        }.onFailure { message = it.message ?: text(R.string.save_failed) }.isSuccess
    }
    fun undo() {
        val path = activeFile ?: return
        val stack = undo[path] ?: return
        if (stack.isNotEmpty()) {
            redo.getOrPut(path) { mutableListOf() }.add(content)
            content = stack.removeAt(stack.lastIndex)
            openTabs[path] = content
            lastEditAt[path] = 0L
            scheduleAutoSave(current?.id, path, content)
        }
    }
    fun redo() {
        val path = activeFile ?: return
        val stack = redo[path] ?: return
        if (stack.isNotEmpty()) {
            undo.getOrPut(path) { mutableListOf() }.add(content)
            val next = stack.removeAt(stack.lastIndex)
            content = next
            openTabs[path] = content
            lastEditAt[path] = 0L
            scheduleAutoSave(current?.id, path, content)
        }
    }
    fun closeTab(path: String) {
        val wasActive = activeFile == path
        if (wasActive && !saveCurrent(showMessage = false)) return
        openTabs.remove(path)
        undo.remove(path)
        redo.remove(path)
        lastEditAt.remove(path)
        if (wasActive) {
            activeFile = null
            content = ""
            readOnly = false
            val nextTab = openTabs.keys.lastOrNull()
            if (nextTab != null) openFile(nextTab)
        }
    }
    fun consumeGeneratedFileToOpen() {
        generatedFileToOpen = null
    }

    fun createFileFromTemplate(path: String, value: String) {
        val project = current ?: return
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val target = repo.resolve(project.id, path)
                    check(!target.exists()) { "File already exists; refusing to overwrite it." }
                    val parent = target.parentFile ?: error("Target file has no parent directory.")
                    check(parent.isDirectory || parent.mkdirs()) { "Could not create the target directory." }
                    val temporary = File(parent, "${target.name}.creating.${java.util.UUID.randomUUID()}")
                    try {
                        temporary.writeText(value, Charsets.UTF_8)
                        try {
                            java.nio.file.Files.move(
                                temporary.toPath(),
                                target.toPath(),
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE
                            )
                        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                            java.nio.file.Files.move(temporary.toPath(), target.toPath())
                        }
                        check(target.isFile) { "Generated file could not be created." }
                    } finally {
                        temporary.delete()
                    }
                }
            }
            isFileOperationRunning = false
            if (current?.id != project.id) return@launch
            result.onSuccess {
                refreshProjectEntries(project.id)
                generatedFileToOpen = path
                message = text(R.string.generated_manifest_created, path)
            }.onFailure { message = it.message ?: text(R.string.create_failed) }
        }
    }

    fun createFile(path: String, directory: Boolean) {
        val p = current ?: return
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val f = repo.resolve(p.id, path)
                    if (directory) check(f.mkdirs()) { "Folder already exists or could not be created." }
                    else {
                        check(!f.exists()) { "File already exists." }
                        check(f.parentFile?.mkdirs() != false) { "Could not create parent folder." }
                        check(f.createNewFile()) { "Could not create file." }
                    }
                }
            }
            isFileOperationRunning = false
            result.onSuccess {
                refreshProjectEntries(p.id)
                message = if (directory) text(R.string.folder_created) else text(R.string.file_created)
            }.onFailure { message = it.message ?: text(R.string.create_failed) }
        }
    }
    fun renameFile(path: String, name: String) {
        val p = current ?: return
        if (isFileOperationRunning) return
        if ((activeFile == path || activeFile?.startsWith("$path/") == true) && !saveCurrent(showMessage = false)) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = repo.resolve(p.id, path)
                    val renamed = com.blockline.app.data.FileOps.rename(file, name)
                        ?: error(text(R.string.invalid_name_target))
                    val newPath = repo.relative(p.id, renamed)
                    repo.remapPathMetadata(p.id, path, newPath)
                    newPath
                }
            }
            isFileOperationRunning = false
            result.onSuccess { newPath ->
                remapOpenPaths(path, newPath)
                refreshProjectEntries(p.id)
                message = text(R.string.renamed)
            }.onFailure { message = it.message ?: text(R.string.rename_failed) }
        }
    }
    fun deleteFile(path: String) {
        val p = current ?: return
        if (isFileOperationRunning) return
        if ((activeFile == path || activeFile?.startsWith("$path/") == true) && !saveCurrent(showMessage = false)) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = repo.resolve(p.id, path)
                    check(file.deleteRecursively()) { text(R.string.delete_failed) }
                    repo.removePathMetadata(p.id, path)
                }
            }
            isFileOperationRunning = false
            result.onSuccess {
                val affectedTabs = openTabs.keys.filter { it == path || it.startsWith("$path/") }
                affectedTabs.forEach { tab ->
                    openTabs.remove(tab)
                    undo.remove(tab)
                    redo.remove(tab)
                    lastEditAt.remove(tab)
                    if (activeFile == tab) { activeFile = null; content = ""; autoSaveJob?.cancel() }
                }
                refreshProjectEntries(p.id)
                message = text(R.string.deleted_path, path)
            }.onFailure { message = it.message ?: text(R.string.delete_failed) }
        }
    }
    fun moveFile(path: String, destination: String) {
        val p = current ?: return
        if (isFileOperationRunning) return
        if ((activeFile == path || activeFile?.startsWith("$path/") == true) && !saveCurrent(showMessage = false)) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = repo.resolve(p.id, path)
                    val destDir = if (destination.isBlank() || destination == ".") repo.projectDir(p.id) else repo.resolve(p.id, destination)
                    require(destDir.isDirectory) { text(R.string.destination_must_exist) }
                    val moved = com.blockline.app.data.FileOps.move(source, destDir) ?: error(text(R.string.move_unsafe))
                    val newPath = repo.relative(p.id, moved)
                    repo.remapPathMetadata(p.id, path, newPath)
                    newPath
                }
            }
            isFileOperationRunning = false
            result.onSuccess { newPath ->
                remapOpenPaths(path, newPath)
                refreshProjectEntries(p.id)
                message = text(R.string.moved_to, newPath)
            }.onFailure { message = it.message ?: text(R.string.move_unsafe) }
        }
    }
    private fun remapOpenPaths(oldPath: String, newPath: String) {
        val affected = openTabs.keys.filter { it == oldPath || it.startsWith("$oldPath/") }
        affected.forEach { oldTab ->
            val newTab = newPath + oldTab.removePrefix(oldPath)
            openTabs.remove(oldTab)?.let { openTabs[newTab] = it }
            undo.remove(oldTab)?.let { undo[newTab] = it }
            redo.remove(oldTab)?.let { redo[newTab] = it }
            lastEditAt.remove(oldTab)?.let { lastEditAt[newTab] = it }
            if (activeFile == oldTab) activeFile = newTab
        }
    }

    fun toggleFavorite(path: String) { current?.let { repo.toggleFavorite(it.id, path) } }
    fun isFavorite(path: String) = current?.let { repo.isFavorite(it.id, path) } ?: false
    fun exportProject(uri: android.net.Uri, mode: ExportMode) {
        val project = current ?: return
        if (isExporting) return
        if (!saveCurrent(showMessage = false)) return
        val projectId = project.id
        isExporting = true
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    repo.backup(projectId)
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error(text(R.string.export_destination_failed))
                    output.use { repo.export(projectId, mode, it) }
                }
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                isExporting = false
            }
            message = result.fold(
                { text(R.string.pack_exported) },
                { it.message ?: text(R.string.export_failed) }
            )
        }
    }

    fun backup() {
        val p = current ?: return
        if (!saveCurrent(showMessage = false)) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.backup(p.id) }
            withContext(Dispatchers.Main) { message = result.fold({ text(R.string.backup_saved) }, { it.message ?: text(R.string.backup_failed) }) }
        }
    }
    fun restore() {
        val p = current ?: return
        if (!saveCurrent(showMessage = false)) return
        val activePathBeforeRestore = activeFile
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.restoreLatest(p.id) } }
            val restoredFileStillExists = if (result.getOrDefault(false) && activePathBeforeRestore != null) {
                withContext(Dispatchers.IO) {
                    runCatching { repo.resolve(p.id, activePathBeforeRestore).isFile }.getOrDefault(false)
                }
            } else false

            withContext(Dispatchers.Main) {
                val restored = result.getOrDefault(false)
                message = if (restored) text(R.string.latest_backup_restored) else result.exceptionOrNull()?.message ?: text(R.string.no_backup_found)
                if (restored && current?.id == p.id) {
                    autoSaveJob?.cancel()
                    clearResourcePreview()
                    clearContentSearch()
                    activeFile = null
                    content = ""
                    readOnly = false
                    pendingEditorLine = null
                    openTabs.clear()
                    undo.clear()
                    redo.clear()
                    lastEditAt.clear()
                    refreshProjectEntries(p.id)
                    if (activePathBeforeRestore != null && restoredFileStillExists) {
                        openFile(activePathBeforeRestore)
                    }
                }
                refresh()
            }
        }
    }
}
