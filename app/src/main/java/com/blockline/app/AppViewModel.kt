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
import com.blockline.app.data.ProjectValidator
import com.blockline.app.data.ValidationIssue
import com.blockline.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
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
    var isValidating by mutableStateOf(false)
        private set
    var validationIssues by mutableStateOf<List<ValidationIssue>>(emptyList())
        private set
    var validationCompleted by mutableStateOf(false)
        private set
    var isSearchingContent by mutableStateOf(false)
        private set
    var contentSearchResults by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    val openTabs = mutableStateMapOf<String, String>()
    private val undo = mutableStateMapOf<String, MutableList<String>>()
    private val redo = mutableStateMapOf<String, MutableList<String>>()

    init { refresh() }

    fun refresh() { projects = repo.list() }

    fun validateCurrentProject() {
        val project = current
        if (project == null) {
            message = text(R.string.select_project_before_validating)
            return
        }
        if (isValidating) return
        isValidating = true
        validationIssues = emptyList()
        validationCompleted = false
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { ProjectValidator.validate(repo.projectDir(project.id)) }
            }
            isValidating = false
            result.onSuccess { issues ->
                if (current?.id == project.id) {
                    validationIssues = issues
                    validationCompleted = true
                }
                val errors = issues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR }
                val warnings = issues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING }
                message = text(R.string.validation_finished, errors, warnings, issues.count { it.severity == com.blockline.app.data.IssueSeverity.INFO })
            }.onFailure { message = it.message ?: text(R.string.validation_failed) }
        }
    }

    fun searchProjectContentAsync(query: String) {
        val project = current
        if (project == null || query.isBlank() || isSearchingContent) return
        isSearchingContent = true
        contentSearchResults = emptyList()
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { searchProjectContent(query) }
            if (current?.id == project.id) contentSearchResults = results
            isSearchingContent = false
            message = text(R.string.content_search_found, results.size, if (results.size >= 500) text(R.string.first_500_shown) else "")
        }
    }

    fun clearContentSearch() { contentSearchResults = emptyList() }

    fun searchProjectContent(query: String): List<Pair<String, Int>> {
        val project = current ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return runCatching {
            repo.allFiles(project.id).asSequence()
                .filter { it.length() <= 1024L * 1024L && it.extension.lowercase() in com.blockline.app.data.TEXT_EXT }
                .flatMap { file ->
                    val path = repo.relative(project.id, file)
                    file.readLines().asSequence().mapIndexedNotNull { index, line ->
                        if (line.contains(query, ignoreCase = true)) path to (index + 1) else null
                    }
                }
                .take(500)
                .toList()
        }.getOrDefault(emptyList())
    }
    fun selectProject(p: Project) {
        if (current?.id == p.id) {
            repo.touch(p.id)
            refresh()
            return
        }
        saveCurrent(showMessage = false)
        current = p
        repo.touch(p.id)
        refresh()
        activeFile = null
        content = ""
        openTabs.clear()
        undo.clear()
        redo.clear()
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
    fun duplicateProject(p: Project) = runCatching { repo.duplicate(p.id) }.onSuccess { refresh(); message = text(R.string.project_duplicated) }.onFailure { message = it.message }
    fun deleteProject(p: Project) {
        repo.delete(p.id)
        if (current?.id == p.id) {
            current = null
            activeFile = null
            content = ""
            openTabs.clear()
            undo.clear()
            redo.clear()
            validationIssues = emptyList()
            validationCompleted = false
            contentSearchResults = emptyList()
        }
        refresh(); message = text(R.string.project_deleted)
    }
    fun openFile(path: String) = openFile(path, null)

    fun openFile(path: String, line: Int?) {
        pendingEditorLine = line?.coerceAtLeast(1)
        if (activeFile != null && activeFile != path) saveCurrent(showMessage = false)
        val p = current ?: return
        runCatching {
            val file = repo.resolve(p.id, path)
            require(file.isFile) { text(R.string.file_not_found) }
            val size = file.length()
            readOnly = size > 1024L * 1024L
            activeFile = path
            content = if (size <= 2L * 1024L * 1024L) file.readText() else ""
            openTabs[path] = content
            undo[path] = mutableListOf(content)
            redo[path] = mutableListOf()
            repo.addRecentFile(p.id, path)
        }.onFailure { message = it.message ?: text(R.string.cannot_open_file) }
    }
    fun consumePendingEditorLine() {
        pendingEditorLine = null
    }

    fun updateContent(value: String) {
        val path = activeFile ?: return
        if (readOnly) return
        if (value != content) {
            undo.getOrPut(path) { mutableListOf(content) }.add(content)
            if (undo[path]!!.size > 80) undo[path]!!.removeAt(0)
            redo.getOrPut(path) { mutableListOf() }.clear()
            content = value
            openTabs[path] = value
        }
    }
    fun saveCurrent(showMessage: Boolean = true) {
        val p = current ?: return
        val path = activeFile ?: return
        if (readOnly) { if (showMessage) message = text(R.string.save_readonly); return }
        runCatching {
            val f = repo.resolve(p.id, path)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".saving")
            tmp.writeText(content)
            if (!tmp.renameTo(f)) { f.writeText(content); tmp.delete() }
            if (showMessage) message = text(R.string.saved_path, path)
        }.onFailure { message = it.message ?: text(R.string.save_failed) }
    }
    fun undo() {
        val path = activeFile ?: return
        val stack = undo[path] ?: return
        if (stack.size > 1) {
            redo.getOrPut(path) { mutableListOf() }.add(content)
            stack.removeAt(stack.lastIndex)
            content = stack.last()
            openTabs[path] = content
        }
    }
    fun redo() {
        val path = activeFile ?: return
        val stack = redo[path] ?: return
        if (stack.isNotEmpty()) {
            val next = stack.removeAt(stack.lastIndex)
            undo.getOrPut(path) { mutableListOf(content) }.add(next)
            content = next
            openTabs[path] = content
        }
    }
    fun closeTab(path: String) {
        if (activeFile == path) saveCurrent(showMessage = false)
        openTabs.remove(path)
        undo.remove(path)
        redo.remove(path)
        if (activeFile == path) { activeFile = null; content = "" }
    }
    fun createFile(path: String, directory: Boolean) {
        val p = current ?: return
        runCatching {
            val f = repo.resolve(p.id, path)
            if (directory) check(f.mkdirs()) else { f.parentFile?.mkdirs(); check(f.createNewFile()) }
            message = if (directory) text(R.string.folder_created) else text(R.string.file_created)
        }.onFailure { message = it.message ?: text(R.string.create_failed) }
    }
    fun renameFile(path: String, name: String) {
        val p = current ?: return
        runCatching {
            val f = repo.resolve(p.id, path)
            val new = com.blockline.app.data.FileOps.rename(f, name) ?: error(text(R.string.invalid_name_target))
            val newPath = repo.relative(p.id, new)
            repo.remapPathMetadata(p.id, path, newPath)
            remapOpenPaths(path, newPath)
            refresh()
            message = text(R.string.renamed)
        }.onFailure { message = it.message ?: text(R.string.rename_failed) }
    }
    fun deleteFile(path: String) {
        val p = current ?: return
        runCatching {
            val f = repo.resolve(p.id, path)
            check(f.deleteRecursively()) { text(R.string.delete_failed) }
            repo.removePathMetadata(p.id, path)
            val affectedTabs = openTabs.keys.filter { it == path || it.startsWith("$path/") }
            affectedTabs.forEach { tab ->
                openTabs.remove(tab)
                undo.remove(tab)
                redo.remove(tab)
                if (activeFile == tab) { activeFile = null; content = "" }
            }
            message = text(R.string.deleted_path, path)
        }.onFailure { message = it.message ?: text(R.string.delete_failed) }
    }
    fun moveFile(path: String, destination: String) {
        val p = current ?: return
        runCatching {
            val source = repo.resolve(p.id, path)
            val destDir = if (destination.isBlank() || destination == ".") repo.projectDir(p.id) else repo.resolve(p.id, destination)
            require(destDir.isDirectory) { text(R.string.destination_must_exist) }
            val moved = com.blockline.app.data.FileOps.move(source, destDir) ?: error(text(R.string.move_unsafe))
            val newPath = repo.relative(p.id, moved)
            repo.remapPathMetadata(p.id, path, newPath)
            remapOpenPaths(path, newPath)
            refresh()
            message = text(R.string.moved_to, newPath)
        }.onFailure { message = it.message ?: text(R.string.move_unsafe) }
    }
    private fun remapOpenPaths(oldPath: String, newPath: String) {
        val affected = openTabs.keys.filter { it == oldPath || it.startsWith("$oldPath/") }
        affected.forEach { oldTab ->
            val newTab = newPath + oldTab.removePrefix(oldPath)
            openTabs.remove(oldTab)?.let { openTabs[newTab] = it }
            undo.remove(oldTab)?.let { undo[newTab] = it }
            redo.remove(oldTab)?.let { redo[newTab] = it }
            if (activeFile == oldTab) activeFile = newTab
        }
    }

    fun toggleFavorite(path: String) { current?.let { repo.toggleFavorite(it.id, path) } }
    fun isFavorite(path: String) = current?.let { repo.isFavorite(it.id, path) } ?: false
    fun backup() {
        val p = current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.backup(p.id) }
            withContext(Dispatchers.Main) { message = result.fold({ text(R.string.backup_saved) }, { it.message ?: text(R.string.backup_failed) }) }
        }
    }
    fun restore() {
        val p = current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.restoreLatest(p.id) }
            withContext(Dispatchers.Main) {
                message = if (result.getOrDefault(false)) text(R.string.latest_backup_restored) else text(R.string.no_backup_found)
                activeFile?.let { openFile(it) }
            }
        }
    }
}
