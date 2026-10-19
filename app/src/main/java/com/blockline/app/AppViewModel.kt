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
import com.blockline.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
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
    var readOnly by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    val openTabs = mutableStateMapOf<String, String>()
    private val undo = mutableStateMapOf<String, MutableList<String>>()
    private val redo = mutableStateMapOf<String, MutableList<String>>()

    init { refresh() }

    fun refresh() { projects = repo.list() }
    fun selectProject(p: Project) {
        current = p
        repo.touch(p.id)
        refresh()
        activeFile = null
        content = ""
    }
    fun createSample() = runCatching { repo.createSample() }.onSuccess {
        refresh(); selectProject(it); message = "Sample project created"
    }.onFailure { message = it.message ?: "Could not create sample project" }

    fun importPack(uri: android.net.Uri) {
        viewModelScope.launch {
            isLoading = true
            try {
                val p = withContext(Dispatchers.IO) { repo.importPack(uri, repo.displayName(uri)) }
                refresh(); selectProject(p); message = "Imported ${p.name}"
            } catch (e: Exception) { message = e.message ?: "Import failed" }
            finally { isLoading = false }
        }
    }
    fun renameProject(p: Project, name: String) = runCatching { repo.rename(p.id, name); refresh(); if (current?.id == p.id) current = repo.list().firstOrNull { it.id == p.id } }.onFailure { message = it.message }
    fun duplicateProject(p: Project) = runCatching { repo.duplicate(p.id) }.onSuccess { refresh(); message = "Project duplicated" }.onFailure { message = it.message }
    fun deleteProject(p: Project) {
        repo.delete(p.id)
        if (current?.id == p.id) { current = null; activeFile = null; content = "" }
        refresh(); message = "Project deleted"
    }
    fun openFile(path: String) {
        val p = current ?: return
        runCatching {
            val file = repo.resolve(p.id, path)
            require(file.isFile) { "File not found" }
            val size = file.length()
            readOnly = size > 1024L * 1024L
            activeFile = path
            content = if (size <= 2L * 1024L * 1024L) file.readText() else ""
            openTabs[path] = content
            undo[path] = mutableListOf(content)
            redo[path] = mutableListOf()
            repo.addRecentFile(p.id, path)
        }.onFailure { message = it.message ?: "Cannot open file" }
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
    fun saveCurrent() {
        val p = current ?: return
        val path = activeFile ?: return
        if (readOnly) { message = "Files larger than 1 MB are read-only"; return }
        runCatching {
            val f = repo.resolve(p.id, path)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".saving")
            tmp.writeText(content)
            if (!tmp.renameTo(f)) { f.writeText(content); tmp.delete() }
            message = "Saved $path"
        }.onFailure { message = it.message ?: "Save failed" }
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
        openTabs.remove(path)
        if (activeFile == path) { activeFile = null; content = "" }
    }
    fun createFile(path: String, directory: Boolean) {
        val p = current ?: return
        runCatching {
            val f = repo.resolve(p.id, path)
            if (directory) check(f.mkdirs()) else { f.parentFile?.mkdirs(); check(f.createNewFile()) }
            message = if (directory) "Folder created" else "File created"
        }.onFailure { message = it.message ?: "Create failed" }
    }
    fun renameFile(path: String, name: String) {
        val p = current ?: return
        runCatching {
            val f = repo.resolve(p.id, path)
            val new = com.blockline.app.data.FileOps.rename(f, name) ?: error("Invalid name or target already exists")
            if (activeFile == path) activeFile = repo.relative(p.id, new)
            refresh(); message = "Renamed"
        }.onFailure { message = it.message ?: "Rename failed" }
    }
    fun deleteFile(path: String) {
        val p = current ?: return
        runCatching {
            val f = repo.resolve(p.id, path)
            check(f.deleteRecursively()) { "Could not delete file" }
            if (activeFile == path) { activeFile = null; content = "" }
            message = "Deleted $path"
        }.onFailure { message = it.message ?: "Delete failed" }
    }
    fun toggleFavorite(path: String) { current?.let { repo.toggleFavorite(it.id, path) } }
    fun isFavorite(path: String) = current?.let { repo.isFavorite(it.id, path) } ?: false
    fun backup() {
        val p = current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.backup(p.id) }
            withContext(Dispatchers.Main) { message = result.fold({ "Backup saved" }, { it.message ?: "Backup failed" }) }
        }
    }
    fun restore() {
        val p = current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repo.restoreLatest(p.id) }
            withContext(Dispatchers.Main) {
                message = if (result.getOrDefault(false)) "Latest backup restored" else "No backup found"
                activeFile?.let { openFile(it) }
            }
        }
    }
}
