package com.blockline.app

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blockline.app.data.ExportMode
import com.blockline.app.data.SearchFileFilter
import com.blockline.app.data.ContentSearchResult
import com.blockline.app.data.ProjectContentSearch
import com.blockline.app.data.Project
import com.blockline.app.data.ProjectRepository
import com.blockline.app.data.OperationLogEntry
import com.blockline.app.data.OperationLogRepository
import com.blockline.app.data.AppUpdate
import com.blockline.app.data.AppUpdateRepository
import com.blockline.app.data.ResourcePreview
import com.blockline.app.data.ResourcePreviewBuilder
import com.blockline.app.data.ProjectValidator
import com.blockline.app.data.VanillaResourceIndex
import com.blockline.app.data.PackDependency
import com.blockline.app.data.PackDependencyResolver
import com.blockline.app.data.BatchReplaceEngine
import com.blockline.app.data.BatchReplaceFailure
import com.blockline.app.data.BatchReplaceFilePreview
import com.blockline.app.data.BatchReplaceScanResult
import com.blockline.app.data.BatchRenameCandidate
import com.blockline.app.data.EditorSaveCoordinator
import com.blockline.app.data.BatchRenameEngine
import com.blockline.app.data.EditorHistory
import com.blockline.app.data.BatchRenameOutcome
import com.blockline.app.data.ValidationIssue
import com.blockline.app.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private fun text(id: Int, vararg args: Any): String {
        val app = getApplication<Application>()
        val language = app.getSharedPreferences("blockline_settings", Application.MODE_PRIVATE)
            .getString("app_language", "system") ?: "system"
        val localizedContext = if (language == "en" || language == "vi") {
            val locale = Locale.forLanguageTag(language)
            val configuration = Configuration(app.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            app.createConfigurationContext(configuration)
        } else {
            app
        }
        return localizedContext.getString(id, *args)
    }

    val repo = ProjectRepository(app)
    private val operationLogRepository = OperationLogRepository(File(app.filesDir, "operation-logs"))
    var operationLog by mutableStateOf<List<OperationLogEntry>>(emptyList())
        private set
    private var operationLogRequestId = 0
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var current by mutableStateOf<Project?>(null)
        private set
    val projectEntries = mutableStateMapOf<String, List<File>>()
    var isIndexingProjectFiles by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var advancementEvent by mutableStateOf(0)
        private set
    var advancementEventIsCreation by mutableStateOf(false)
        private set

    private fun postAdvancement(isCreation: Boolean) {
        advancementEventIsCreation = isCreation
        advancementEvent++
    }

    private val settingsPreferences = app.getSharedPreferences("blockline_settings", Application.MODE_PRIVATE)
    private val updateRepository = AppUpdateRepository()
    private val themeState = mutableStateOf(
        runCatching { ThemeMode.valueOf(settingsPreferences.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK)
    )
    var themeMode: ThemeMode
        get() = themeState.value
        set(value) {
            themeState.value = value
            settingsPreferences.edit().putString("theme_mode", value.name).apply()
        }

    private val gameEffectsState = mutableStateOf(settingsPreferences.getBoolean("game_effects_enabled", true))
    var gameEffectsEnabled: Boolean
        get() = gameEffectsState.value
        set(value) {
            gameEffectsState.value = value
            settingsPreferences.edit().putBoolean("game_effects_enabled", value).apply()
        }

    private val clickSoundsState = mutableStateOf(settingsPreferences.getBoolean("click_sounds_enabled", false))
    var clickSoundsEnabled: Boolean
        get() = clickSoundsState.value
        set(value) {
            clickSoundsState.value = value
            settingsPreferences.edit().putBoolean("click_sounds_enabled", value).apply()
        }
    var appLanguage by mutableStateOf(settingsPreferences.getString("app_language", "system") ?: "system")
        private set

    fun changeAppLanguage(language: String) {
        require(language in setOf("system", "en", "vi")) { "Unsupported app language: $language" }
        if (appLanguage == language) return
        appLanguage = language
        settingsPreferences.edit().putString("app_language", language).apply()
    }
    private val fontSizeState = mutableStateOf(settingsPreferences.getInt("editor_font_size", 14).coerceIn(10, 24))
    var fontSize: Int
        get() = fontSizeState.value
        set(value) {
            fontSizeState.value = value.coerceIn(10, 24)
            settingsPreferences.edit().putInt("editor_font_size", fontSizeState.value).apply()
        }
    var autoCheckUpdates by mutableStateOf(settingsPreferences.getBoolean("auto_check_updates", true))
        private set
    var isCheckingUpdates by mutableStateOf(false)
        private set
    var updateInfo by mutableStateOf<AppUpdate?>(null)
        private set
    var latestReleaseInfo by mutableStateOf(readSavedReleaseInfo())
        private set

    private fun readSavedReleaseInfo(): AppUpdate? {
        val version = settingsPreferences.getString("latest_release_version", null) ?: return null
        val releaseUrl = settingsPreferences.getString("latest_release_url", null) ?: return null
        val apkUrl = settingsPreferences.getString("latest_release_apk_url", null) ?: return null
        return AppUpdate(
            version = version,
            releaseName = settingsPreferences.getString("latest_release_name", "Blockline v$version") ?: "Blockline v$version",
            releaseNotes = settingsPreferences.getString("latest_release_notes", "") ?: "",
            apkUrl = apkUrl,
            apkSizeBytes = settingsPreferences.getLong("latest_release_size", 0L).coerceAtLeast(0L),
            releaseUrl = releaseUrl,
            publishedAt = settingsPreferences.getString("latest_release_published_at", "") ?: ""
        )
    }

    private fun rememberReleaseInfo(info: AppUpdate) {
        latestReleaseInfo = info
        settingsPreferences.edit()
            .putString("latest_release_version", info.version)
            .putString("latest_release_name", info.releaseName)
            .putString("latest_release_notes", info.releaseNotes.take(12_000))
            .putString("latest_release_apk_url", info.apkUrl)
            .putLong("latest_release_size", info.apkSizeBytes)
            .putString("latest_release_url", info.releaseUrl)
            .putString("latest_release_published_at", info.publishedAt)
            .apply()
    }
    var lastUpdateCheck by mutableStateOf(settingsPreferences.getString("last_update_check", null))
        private set
    var updateCheckResult by mutableStateOf<String?>(null)
        private set
    private var updateCheckRequestId = 0
    var activeFile by mutableStateOf<String?>(null)
        private set
    private val persistedEditorContents = mutableStateMapOf<String, String>()
    val activeFilePersistedContent: String?
        get() = current?.id?.let { projectId ->
            activeFile?.let { path -> persistedEditorContents[editorSaveKey(projectId, path)] }
        }

    private fun editorSaveKey(projectId: String, path: String): String = "$projectId::$path"
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
    private val editorHistory = EditorHistory()
    private var contentSearchRequestId = 0
    private var validationRequestId = 0
    private var dependencyRequestId = 0
    private var batchReplaceRequestId = 0
    private var batchReplaceProjectId: String? = null
    private var batchReplaceQuery = ""
    private var batchReplaceReplacement = ""
    private var batchReplaceFilter = SearchFileFilter.ALL
    private var fileIndexRequestId = 0
    private var projectListRequestId = 0
    private var autoSaveJob: Job? = null
    private val editorSaveCoordinator = EditorSaveCoordinator()
    private var projectSelectionRequestId = 0

    init {
        refresh()
        if (autoCheckUpdates) {
            viewModelScope.launch {
                delay(1_500)
                checkForUpdates(showUpToDateMessage = false, automatic = true)
            }
        }
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        autoCheckUpdates = enabled
        settingsPreferences.edit().putBoolean("auto_check_updates", enabled).apply()
        if (enabled) checkForUpdates(showUpToDateMessage = false, automatic = true)
    }

    fun checkForUpdates(showUpToDateMessage: Boolean = true, automatic: Boolean = false) {
        if (isCheckingUpdates) return
        val requestId = ++updateCheckRequestId
        isCheckingUpdates = true
        updateCheckResult = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    updateRepository.checkLatest(BuildConfig.VERSION_NAME)
                }
            }
            if (requestId != updateCheckRequestId) return@launch
            isCheckingUpdates = false
            lastUpdateCheck = java.time.Instant.now().toString()
            settingsPreferences.edit().putString("last_update_check", lastUpdateCheck).apply()
            result.onSuccess { available ->
                if (available != null) rememberReleaseInfo(available)
                val ignoredVersion = settingsPreferences.getString("dismissed_update_version", null)
                updateInfo = if (available != null && (ignoredVersion != available.version || !automatic)) available else null
                updateCheckResult = when {
                    available != null -> text(R.string.update_available_status, available.version)
                    else -> text(R.string.update_up_to_date)
                }
                if (available == null && showUpToDateMessage) message = text(R.string.update_up_to_date)
            }.onFailure { error ->
                updateInfo = null
                updateCheckResult = text(R.string.update_check_failed)
                if (showUpToDateMessage) message = text(R.string.update_check_failed_detail, error.message ?: text(R.string.unknown_value))
            }
        }
    }

    fun dismissUpdate(version: String) {
        val visibleUpdate = updateInfo?.takeIf { it.version == version }
        if (visibleUpdate != null) rememberReleaseInfo(visibleUpdate)
        settingsPreferences.edit().putString("dismissed_update_version", version).apply()
        updateInfo = null
    }

    fun openUpdateReleasePage(context: android.content.Context) {
        val info = updateInfo ?: return
        openReleasePage(context, info)
    }

    fun openReleasePage(context: android.content.Context, info: AppUpdate) {
        runCatching {
            val url = info.releaseUrl
            require(url.startsWith("https://github.com/slgamervn/Blockline/releases/tag/")) {
                "Unexpected release URL"
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onSuccess {
            settingsPreferences.edit().putString("dismissed_update_version", info.version).apply()
            if (updateInfo?.version == info.version) updateInfo = null
        }.onFailure {
            message = text(R.string.update_open_failed)
        }
    }


    fun refresh() {
        val requestId = ++projectListRequestId
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repo.list() } }
            if (requestId != projectListRequestId) return@launch
            result.onSuccess { projects = it }
                .onFailure { message = it.message ?: "Could not load projects." }
        }
    }
    fun refreshOperationLog() {
        val projectId = current?.id
        if (projectId == null) {
            operationLog = emptyList()
            operationLogRequestId++
            return
        }
        val requestId = ++operationLogRequestId
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { operationLogRepository.list(projectId) } }
            if (requestId != operationLogRequestId || current?.id != projectId) return@launch
            result.onSuccess { operationLog = it }
                .onFailure { message = it.message ?: text(R.string.operation_log_load_failed) }
        }
    }

    fun clearOperationLog() {
        val projectId = current?.id ?: return
        val requestId = ++operationLogRequestId
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    operationLogRepository.clear(projectId)
                    operationLogRepository.list(projectId)
                }
            }
            if (requestId != operationLogRequestId || current?.id != projectId) return@launch
            result.onSuccess {
                operationLog = it
                message = text(R.string.operation_log_cleared)
            }.onFailure { message = it.message ?: text(R.string.operation_log_load_failed) }
        }
    }

    private fun recordOperation(action: String, detail: String) {
        val projectId = current?.id ?: return
        val requestId = ++operationLogRequestId
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    operationLogRepository.append(projectId, action, detail)
                    operationLogRepository.list(projectId)
                }
            }
            if (requestId != operationLogRequestId || current?.id != projectId) return@launch
            result.onSuccess { operationLog = it }
        }
    }


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
        clearBatchReplacePreview()
        val requestId = ++batchReplaceRequestId
        val projectId = project.id
        isPreviewingBatchReplace = true
        viewModelScope.launch {
            if (!saveCurrentAndWait(showMessage = false)) {
                isPreviewingBatchReplace = false
                return@launch
            }
            if (requestId != batchReplaceRequestId || current?.id != projectId) {
                isPreviewingBatchReplace = false
                return@launch
            }
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

        val projectId = project.id
        val query = batchReplaceQuery
        val replacement = batchReplaceReplacement
        val preview = batchReplacePreview.toList()
        isApplyingBatchReplace = true
        isFileOperationRunning = true
        autoSaveJob?.cancel()

        viewModelScope.launch {
            if (!saveCurrentAndWait(showMessage = false)) {
                isApplyingBatchReplace = false
                isFileOperationRunning = false
                return@launch
            }
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
                    editorHistory.reset(path)
                    if (activeFile == path) content = value
                }
                autoSaveJob?.cancel()
                refreshProjectEntries(projectId)
                message = text(R.string.batch_replace_done, outcome.occurrences, outcome.changedFiles)
                recordOperation(text(R.string.operation_action_batch_replaced), text(R.string.operation_detail_replace_count, outcome.occurrences, outcome.changedFiles))
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
                    editorHistory.clear()
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

        val projectId = project.id
        isFileOperationRunning = true
        autoSaveJob?.cancel()
        viewModelScope.launch {
            if (!saveCurrentAndWait(showMessage = false)) {
                isFileOperationRunning = false
                return@launch
            }
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
                recordOperation(text(R.string.operation_action_batch_renamed), text(R.string.operation_detail_file_count, outcome.changedCount))
            } else if (outcome.backupFailed) {
                message = text(R.string.batch_rename_backup_failed, outcome.details.orEmpty())
            } else {
                autoSaveJob?.cancel()
                openTabs.clear()
                editorHistory.clear()
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
                withContext(Dispatchers.IO) {
                    ProjectValidator.validate(
                        repo.projectDir(project.id),
                        VanillaResourceIndex.read(VanillaResourceIndex.cacheFile(getApplication<Application>().filesDir))
                    )
                }
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
                recordOperation(text(R.string.operation_action_validated), text(R.string.operation_detail_validation_counts, errors, warnings))
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

    private fun searchProjectContent(projectId: String, query: String, filter: SearchFileFilter): List<ContentSearchResult> =
        ProjectContentSearch.search(repo, projectId, query, filter)
    fun selectProject(p: Project) {
        val requestId = ++projectSelectionRequestId
        openFileRequestId++
        isOpeningFile = false
        clearResourcePreview()
        if (current?.id == p.id) {
            viewModelScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { repo.touch(p.id) } }
                if (current?.id != p.id) return@launch
                result.onFailure { message = it.message ?: "Could not update project activity." }
                refresh()
                refreshProjectEntries(p.id)
            }
            return
        }
        viewModelScope.launch {
            if (!saveCurrentAndWait(showMessage = false)) return@launch
            if (requestId != projectSelectionRequestId) return@launch
            current = p
            refreshOperationLog()
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
            withContext(Dispatchers.IO) { repo.touch(p.id) }
            refresh()
            refreshProjectEntries(p.id)
            activeFile = null
            content = ""
            openTabs.clear()
            editorHistory.clear()
            validationIssues = emptyList()
            validationCompleted = false
            contentSearchResults = emptyList()
        }
    }
    fun createSample(onCreated: (() -> Unit)? = null) {
        if (isLoading || isFileOperationRunning) return
        viewModelScope.launch {
            isLoading = true
            val result = runCatching { withContext(Dispatchers.IO) { repo.createSample() } }
            isLoading = false
            result.onSuccess {
                refresh()
                selectProject(it)
                message = text(R.string.sample_created)
                onCreated?.invoke()
            }.onFailure { message = it.message ?: text(R.string.sample_create_failed) }
        }
    }

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
    fun renameProject(p: Project, name: String) {
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repo.rename(p.id, name)
                    repo.list()
                }
            }
            isFileOperationRunning = false
            result.onSuccess { updatedProjects ->
                projectListRequestId++
                projects = updatedProjects
                if (current?.id == p.id) current = updatedProjects.firstOrNull { it.id == p.id }
            }.onFailure { message = it.message ?: "Could not rename project." }
        }
    }
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
        isFileOperationRunning = true
        viewModelScope.launch {
            if (current?.id == p.id && !saveCurrentAndWait(showMessage = false)) {
                isFileOperationRunning = false
                return@launch
            }
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
                    editorHistory.clear()
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

    fun importTextDocument(uri: Uri) {
        val project = current ?: run {
            message = text(R.string.no_project_selected)
            return
        }
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { repo.importTextDocument(project.id, uri) }
            }
            isFileOperationRunning = false
            result.onSuccess { importedPath ->
                refreshProjectEntries(project.id)
                openFile(importedPath)
                message = "Imported $importedPath"
            }.onFailure { error ->
                message = error.message ?: "Could not import text file."
            }
        }
    }

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
        val previousReadOnly = readOnly
        val buffered = openTabs[path]
        isOpeningFile = true

        viewModelScope.launch {
            if (requestId != openFileRequestId || current?.id != project.id) return@launch
            if (previousPath != null && previousPath != path && !previousReadOnly) {
                if (!saveCurrentAndWait(showMessage = false)) {
                    if (requestId == openFileRequestId && current?.id == project.id) {
                        isOpeningFile = false
                        pendingEditorLine = null
                    }
                    return@launch
                }
            }
            if (requestId != openFileRequestId || current?.id != project.id) return@launch
            val result = runCatching {
                withContext(Dispatchers.IO) {
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
                    editorHistory.reset(path)
                    persistedEditorContents[editorSaveKey(project.id, path)] = opened.text
                }
                pendingEditorLine = line?.coerceAtLeast(1)
            }.onFailure {
                pendingEditorLine = null
                message = it.message ?: text(R.string.cannot_open_file)
            }
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
            editorHistory.recordEdit(path, content, now)
            content = value
            openTabs[path] = value
            scheduleAutoSave(current?.id, path, value)
        }
    }
    private fun scheduleAutoSave(projectId: String?, path: String, value: String) {
        if (projectId == null || value.length > 1_000_000) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(850)
            if (current?.id != projectId || activeFile != path || readOnly || openTabs[path] != value) return@launch
            val result = try {
                withContext(Dispatchers.IO) {
                    editorSaveCoordinator.write(
                        projectRoot = repo.projectDir(projectId),
                        target = repo.resolve(projectId, path),
                        value = value,
                        suffix = ".autosaving"
                    )
                }
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (result.isSuccess) {
                persistedEditorContents[editorSaveKey(projectId, path)] = value
            } else if (current?.id == projectId && activeFile == path) {
                message = result.exceptionOrNull()?.message ?: text(R.string.save_failed)
            }
        }
    }
    fun saveCurrent(showMessage: Boolean = true) {
        viewModelScope.launch { saveCurrentAndWait(showMessage) }
    }

    private suspend fun saveCurrentAndWait(showMessage: Boolean = true): Boolean {
        val project = current ?: return true
        val path = activeFile ?: return true
        if (readOnly) {
            if (showMessage) message = text(R.string.save_readonly)
            return true
        }
        autoSaveJob?.cancelAndJoin()
        var snapshot = content
        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    editorSaveCoordinator.write(
                        projectRoot = repo.projectDir(project.id),
                        target = repo.resolve(project.id, path),
                        value = snapshot
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: text(R.string.save_failed)
                return false
            }
            persistedEditorContents[editorSaveKey(project.id, path)] = snapshot
            val latest = when {
                current?.id != project.id -> null
                activeFile == path -> content
                else -> openTabs[path]
            }
            if (latest == null || latest == snapshot) {
                if (current?.id == project.id && activeFile == path) {
                    if (showMessage) {
                        message = text(R.string.saved_path, path)
                        postAdvancement(isCreation = false)
                    }
                }
                return true
            }
            snapshot = latest
            autoSaveJob?.cancelAndJoin()
        }
    }

    private suspend fun saveCurrentIfPathAffected(projectId: String, path: String): Boolean {
        val affected = current?.id == projectId &&
            (activeFile == path || activeFile?.startsWith("$path/") == true)
        return !affected || saveCurrentAndWait(showMessage = false)
    }
    fun undo() {
        val path = activeFile ?: return
        val previous = editorHistory.undo(path, content) ?: return
        content = previous
        openTabs[path] = previous
        scheduleAutoSave(current?.id, path, previous)
    }
    fun redo() {
        val path = activeFile ?: return
        val next = editorHistory.redo(path, content) ?: return
        content = next
        openTabs[path] = next
        scheduleAutoSave(current?.id, path, next)
    }
    fun closeTab(path: String) {
        viewModelScope.launch {
            if (activeFile == path && !saveCurrentAndWait(showMessage = false)) return@launch
            openTabs.remove(path)
            editorHistory.reset(path)
            if (activeFile == path) {
                activeFile = null
                content = ""
                readOnly = false
                val nextTab = openTabs.keys.lastOrNull()
                if (nextTab != null) openFile(nextTab)
            }
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
                postAdvancement(isCreation = true)
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
                postAdvancement(isCreation = true)
                message = if (directory) text(R.string.folder_created) else text(R.string.file_created)
            }.onFailure { message = it.message ?: text(R.string.create_failed) }
        }
    }
    fun renameFile(path: String, name: String) {
        val p = current ?: return
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            if (!saveCurrentIfPathAffected(p.id, path)) {
                isFileOperationRunning = false
                return@launch
            }
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
                recordOperation(text(R.string.operation_action_renamed), "$path → $newPath")
            }.onFailure { message = it.message ?: text(R.string.rename_failed) }
        }
    }
    fun deleteFile(path: String) {
        val p = current ?: return
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            if (!saveCurrentIfPathAffected(p.id, path)) {
                isFileOperationRunning = false
                return@launch
            }
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
                    editorHistory.reset(tab)
                    if (activeFile == tab) { activeFile = null; content = ""; autoSaveJob?.cancel() }
                }
                refreshProjectEntries(p.id)
                message = text(R.string.deleted_path, path)
                recordOperation(text(R.string.operation_action_deleted), path)
            }.onFailure { message = it.message ?: text(R.string.delete_failed) }
        }
    }
    fun moveFile(path: String, destination: String) {
        val p = current ?: return
        if (isFileOperationRunning) return
        isFileOperationRunning = true
        viewModelScope.launch {
            if (!saveCurrentIfPathAffected(p.id, path)) {
                isFileOperationRunning = false
                return@launch
            }
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
                recordOperation(text(R.string.operation_action_moved), "$path → $newPath")
            }.onFailure { message = it.message ?: text(R.string.move_unsafe) }
        }
    }
    private fun remapOpenPaths(oldPath: String, newPath: String) {
        val affected = openTabs.keys.filter { it == oldPath || it.startsWith("$oldPath/") }
        affected.forEach { oldTab ->
            val newTab = newPath + oldTab.removePrefix(oldPath)
            openTabs.remove(oldTab)?.let { openTabs[newTab] = it }
            editorHistory.remap(oldTab, newTab)
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
            if (!saveCurrentAndWait(showMessage = false)) {
                isExporting = false
                return@launch
            }
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
            if (result.isSuccess) recordOperation(text(R.string.operation_action_exported), text(R.string.operation_detail_export_mode, mode.name))
        }
    }

    fun backup() {
        val project = current ?: return
        viewModelScope.launch {
            if (!saveCurrentAndWait(showMessage = false)) return@launch
            if (current?.id != project.id) return@launch
            val result = runCatching { withContext(Dispatchers.IO) { repo.backup(project.id) } }
            message = result.fold({ text(R.string.backup_saved) }, { it.message ?: text(R.string.backup_failed) })
            if (result.isSuccess) recordOperation(text(R.string.operation_action_backup), "")
        }
    }
    fun restore() {
        val p = current ?: return
        viewModelScope.launch {
            if (!saveCurrentAndWait(showMessage = false)) return@launch
            if (current?.id != p.id) return@launch
            val activePathBeforeRestore = activeFile
            val result = runCatching { withContext(Dispatchers.IO) { repo.restoreLatest(p.id) } }
            val restoredFileStillExists = if (result.getOrDefault(false) && activePathBeforeRestore != null) {
                withContext(Dispatchers.IO) {
                    runCatching { repo.resolve(p.id, activePathBeforeRestore).isFile }.getOrDefault(false)
                }
            } else false

            withContext(Dispatchers.Main) {
                val restored = result.getOrDefault(false)
                message = if (restored) text(R.string.latest_backup_restored) else result.exceptionOrNull()?.message ?: text(R.string.no_backup_found)
                if (restored) recordOperation(text(R.string.operation_action_restore), "")
                if (restored && current?.id == p.id) {
                    autoSaveJob?.cancel()
                    clearResourcePreview()
                    clearContentSearch()
                    activeFile = null
                    content = ""
                    readOnly = false
                    pendingEditorLine = null
                    openTabs.clear()
                    editorHistory.clear()
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
