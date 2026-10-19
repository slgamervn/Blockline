package com.blockline.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blockline.app.data.ExportMode
import com.blockline.app.data.Project
import com.blockline.app.data.ProjectRepository
import com.blockline.app.data.ResourcePreview
import com.blockline.app.data.ResourcePreviewBuilder
import com.blockline.app.data.ProjectValidator
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
    var contentSearchResults by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    val openTabs = mutableStateMapOf<String, String>()
    private val undo = mutableStateMapOf<String, MutableList<String>>()
    private val redo = mutableStateMapOf<String, MutableList<String>>()
    private val lastEditAt = mutableMapOf<String, Long>()
    private var contentSearchRequestId = 0
    private var validationRequestId = 0
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

    fun searchProjectContentAsync(query: String) {
        val project = current
        if (project == null || query.isBlank()) return
        val requestId = ++contentSearchRequestId
        isSearchingContent = true
        contentSearchResults = emptyList()
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { searchProjectContent(project.id, query) }
            if (requestId != contentSearchRequestId) return@launch
            isSearchingContent = false
            if (current?.id == project.id) {
                contentSearchResults = results
                message = text(R.string.content_search_found, results.size, if (results.size >= 500) text(R.string.first_500_shown) else "")
            }
        }
    }

    fun clearContentSearch() {
        contentSearchRequestId++
        isSearchingContent = false
        contentSearchResults = emptyList()
    }

    fun searchProjectContent(query: String): List<Pair<String, Int>> {
        val project = current ?: return emptyList()
        return searchProjectContent(project.id, query)
    }

    private fun searchProjectContent(projectId: String, query: String): List<Pair<String, Int>> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            repo.allFiles(projectId).asSequence()
                .filter { it.length() <= 1024L * 1024L && it.extension.lowercase() in com.blockline.app.data.TEXT_EXT }
                .flatMap { file ->
                    val path = repo.relative(projectId, file)
                    file.readLines(Charsets.UTF_8).asSequence().mapIndexedNotNull { index, line ->
                        if (line.contains(query, ignoreCase = true)) path to (index + 1) else null
                    }
                }
                .take(500)
                .toList()
        }.getOrDefault(emptyList())
    }
    fun selectProject(p: Project) {
        clearResourcePreview()
        if (current?.id == p.id) {
            repo.touch(p.id)
            refresh()
            refreshProjectEntries(p.id)
            return
        }
        saveCurrent(showMessage = false)
        current = p
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
        if (current?.id == p.id) saveCurrent(showMessage = false)
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
                    isIndexingProjectFiles = false
                    fileIndexRequestId++
                }
                projectEntries.remove(p.id)
                refresh()
                message = text(R.string.project_deleted)
            }.onFailure { message = it.message ?: text(R.string.project_deleted) }
        }
    }
    fun openFile(path: String) = openFile(path, null)

    fun openFile(path: String, line: Int?) {
        pendingEditorLine = line?.coerceAtLeast(1)
        val p = current ?: return
        if (activeFile == path) {
            if (line == null) pendingEditorLine = null
            return
        }
        if (activeFile != null) saveCurrent(showMessage = false)
        runCatching {
            val file = repo.resolve(p.id, path)
            require(file.isFile) { text(R.string.file_not_found) }
            val size = file.length()
            readOnly = size > 1024L * 1024L
            activeFile = path
            val buffered = openTabs[path]
            content = buffered ?: if (size <= 2L * 1024L * 1024L) file.readText(Charsets.UTF_8) else ""
            openTabs[path] = content
            if (buffered == null) {
                undo[path] = mutableListOf()
                redo[path] = mutableListOf()
                lastEditAt.remove(path)
            }
            repo.addRecentFile(p.id, path)
        }.onFailure {
            pendingEditorLine = null
            message = it.message ?: text(R.string.cannot_open_file)
        }
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
                    val temp = File(target.parentFile, target.name + ".autosaving")
                    temp.writeText(value, Charsets.UTF_8)
                    if (!temp.renameTo(target)) {
                        target.writeText(value, Charsets.UTF_8)
                        temp.delete()
                    }
                }
            }
            if (result.isFailure && current?.id == projectId && activeFile == path) {
                message = result.exceptionOrNull()?.message ?: text(R.string.save_failed)
            }
        }
    }
    fun saveCurrent(showMessage: Boolean = true) {
        val p = current ?: return
        val path = activeFile ?: return
        if (readOnly) { if (showMessage) message = text(R.string.save_readonly); return }
        autoSaveJob?.cancel()
        runCatching {
            val f = repo.resolve(p.id, path)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".saving")
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(f)) { f.writeText(content, Charsets.UTF_8); tmp.delete() }
            if (showMessage) message = text(R.string.saved_path, path)
        }.onFailure { message = it.message ?: text(R.string.save_failed) }
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
        if (activeFile == path) saveCurrent(showMessage = false)
        openTabs.remove(path)
        undo.remove(path)
        redo.remove(path)
        lastEditAt.remove(path)
        if (activeFile == path) { activeFile = null; content = "" }
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
        if (activeFile == path || activeFile?.startsWith("$path/") == true) saveCurrent(showMessage = false)
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
        if (activeFile == path || activeFile?.startsWith("$path/") == true) saveCurrent(showMessage = false)
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
        if (activeFile == path || activeFile?.startsWith("$path/") == true) saveCurrent(showMessage = false)
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
        saveCurrent(showMessage = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.backup(p.id) }
            withContext(Dispatchers.Main) { message = result.fold({ text(R.string.backup_saved) }, { it.message ?: text(R.string.backup_failed) }) }
        }
    }
    fun restore() {
        val p = current ?: return
        saveCurrent(showMessage = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.restoreLatest(p.id) }
            withContext(Dispatchers.Main) {
                val restored = result.getOrDefault(false)
                message = if (restored) text(R.string.latest_backup_restored) else result.exceptionOrNull()?.message ?: text(R.string.no_backup_found)
                if (restored) activeFile?.let { path -> openFile(path) }
                refresh()
            }
        }
    }
}
