package com.blockline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel
import com.blockline.app.BuildConfig
import com.blockline.app.R
import com.blockline.app.data.ExportMode
import com.blockline.app.data.SearchFileFilter
import com.blockline.app.data.GeneratedPackType
import com.blockline.app.data.PackManifestGenerator
import com.blockline.app.data.DependencyKind
import com.blockline.app.data.BatchRenameCandidate
import com.blockline.app.data.BatchRenameEngine
import com.blockline.app.data.BatchRenamePreview
import com.blockline.app.data.LineDiffEngine
import com.blockline.app.data.DiffLineKind
import com.blockline.app.data.ScriptApiReferenceRepository
import com.blockline.app.data.ScriptApiLoadResult
import com.blockline.app.data.ScriptApiSource
import com.blockline.app.data.TEXT_EXT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.blockline.app.ui.theme.ThemeMode
import java.io.OutputStream

@Composable
fun SearchScreen(vm: AppViewModel, onOpenEditor: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var contentMode by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(SearchFileFilter.ALL) }
    var replaceMode by remember { mutableStateOf(false) }
    var replacementText by remember { mutableStateOf("") }
    var showApplyReplaceConfirm by remember { mutableStateOf(false) }
    val p = vm.current
    val indexedFiles = p?.let { vm.projectEntries[it.id] }.orEmpty()
    var searchCandidates by remember(p?.id) { mutableStateOf(emptyList<Pair<java.io.File, String>>()) }
    LaunchedEffect(p?.id, indexedFiles) {
        val projectId = p?.id
        searchCandidates = if (projectId == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                indexedFiles.asSequence()
                    .filter { it.isFile }
                    .map { file -> file to vm.repo.relative(projectId, file) }
                    .toList()
            }
        }
    }
    var pathMatches by remember(p?.id) { mutableStateOf(emptyList<java.io.File>()) }
    LaunchedEffect(searchCandidates, filter, query) {
        val snapshotQuery = query
        val snapshotFilter = filter
        delay(80)
        pathMatches = withContext(Dispatchers.Default) {
            searchCandidates.asSequence()
                .filter { (file, path) ->
                    snapshotFilter.matches(file, path) && path.contains(snapshotQuery, true)
                }
                .map { it.first }
                .toList()
        }
    }
    val filterOptions = listOf(
        SearchFileFilter.ALL to R.string.filter_all,
        SearchFileFilter.JSON to R.string.filter_json,
        SearchFileFilter.SCRIPT to R.string.filter_script,
        SearchFileFilter.FUNCTION to R.string.filter_function,
        SearchFileFilter.MANIFEST to R.string.filter_manifest
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.search_project), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.clearContentSearch(); vm.clearBatchReplacePreview() },
            label = { Text(stringResource(if (contentMode) R.string.search_inside_text else R.string.file_name_or_path)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = !contentMode, onClick = { contentMode = false }, label = { Text(stringResource(R.string.file_paths)) })
            androidx.compose.material3.FilterChip(selected = contentMode, onClick = {
                contentMode = true
                if (query.isNotBlank()) vm.searchProjectContentAsync(query, filter)
            }, label = { Text(stringResource(R.string.file_contents)) })
            if (contentMode) Button(
                onClick = { vm.searchProjectContentAsync(query, filter) },
                enabled = query.isNotBlank() && !vm.isSearchingContent
            ) { Text(stringResource(if (vm.isSearchingContent) R.string.searching else R.string.nav_search)) }
        }
        if (contentMode) {
            androidx.compose.material3.FilterChip(
                selected = replaceMode,
                onClick = {
                    replaceMode = !replaceMode
                    showApplyReplaceConfirm = false
                    vm.clearBatchReplacePreview()
                },
                label = { Text(stringResource(R.string.batch_replace_toggle)) }
            )
        }
        if (contentMode && replaceMode) {
            OutlinedTextField(
                value = replacementText,
                onValueChange = { replacementText = it; vm.clearBatchReplacePreview() },
                label = { Text(stringResource(R.string.batch_replace_with)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 1,
                maxLines = 3
            )
            Text(
                stringResource(R.string.batch_replace_safety_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { vm.previewBatchReplace(query, replacementText, filter) },
                enabled = query.isNotBlank() && !vm.isPreviewingBatchReplace && !vm.isApplyingBatchReplace,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (vm.isPreviewingBatchReplace) R.string.batch_replace_previewing else R.string.batch_replace_preview))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { (option, label) ->
                androidx.compose.material3.FilterChip(
                    selected = filter == option,
                    onClick = {
                        filter = option
                        vm.clearContentSearch()
                        if (contentMode && query.isNotBlank()) vm.searchProjectContentAsync(query, option)
                    },
                    label = { Text(stringResource(label)) }
                )
            }
        }
        if (p == null) {
            Text(stringResource(R.string.select_project_first))
        } else if (!contentMode) {
            if (vm.isIndexingProjectFiles) Text(stringResource(R.string.scanning_files))
            else if (pathMatches.isEmpty()) Text(stringResource(if (query.isBlank()) R.string.enter_file_name else R.string.no_matching_files))
            else LazyColumn(Modifier.weight(1f)) {
                items(pathMatches, key = { it.path }) { file ->
                    val path = vm.repo.relative(p.id, file)
                    ListItem(headlineContent = { Text(file.name) }, supportingContent = { Text(path) }, modifier = Modifier.clickable { vm.openFile(path); onOpenEditor() })
                }
            }
        } else if (replaceMode) {
            if (vm.isPreviewingBatchReplace) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.batch_replace_previewing))
            } else if (!vm.batchReplacePreviewReady) {
                Text(stringResource(R.string.batch_replace_preview_hint))
            } else {
                if (vm.batchReplaceTruncated) {
                    Text(stringResource(R.string.batch_replace_scan_limited), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.batch_replace_preview_found, vm.batchReplacePreview.size))
                if (vm.batchReplacePreview.isEmpty()) {
                    Text(stringResource(R.string.batch_replace_no_matches))
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(vm.batchReplacePreview, key = { it.path }) { result ->
                            ListItem(
                                headlineContent = { Text(result.path.substringAfterLast('/')) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(R.string.batch_replace_file_matches, result.occurrences, result.path))
                                        if (result.sample.isNotBlank()) Text(result.sample, maxLines = 2, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }
                    }
                    Button(
                        onClick = { showApplyReplaceConfirm = true },
                        enabled = !vm.batchReplaceTruncated && !vm.isApplyingBatchReplace,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (vm.isApplyingBatchReplace) R.string.batch_replace_applying else R.string.batch_replace_apply))
                    }
                }
            }
        } else {
            if (vm.isSearchingContent) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (vm.contentSearchResults.isEmpty()) {
                Text(stringResource(if (query.isBlank()) R.string.enter_text_search else R.string.no_search_results))
            } else LazyColumn(Modifier.weight(1f)) {
                items(vm.contentSearchResults, key = { "${it.path}:${it.line}:${it.snippet}" }) { result ->
                    ListItem(
                        headlineContent = { Text(result.path.substringAfterLast('/')) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.line_result, result.path, result.line))
                                Text(result.snippet, maxLines = 2, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.clickable { vm.openFile(result.path, result.line); onOpenEditor() }
                    )
                }
            }
        }
    }
    if (showApplyReplaceConfirm) AlertDialog(
        onDismissRequest = { showApplyReplaceConfirm = false },
        title = { Text(stringResource(R.string.batch_replace_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.batch_replace_confirm_body,
                    vm.batchReplacePreview.sumOf { it.occurrences },
                    vm.batchReplacePreview.size
                )
            )
        },
        confirmButton = {
            TextButton(onClick = {
                showApplyReplaceConfirm = false
                vm.applyBatchReplace()
            }) { Text(stringResource(R.string.batch_replace_apply)) }
        },
        dismissButton = {
            TextButton(onClick = { showApplyReplaceConfirm = false }) { Text(stringResource(R.string.cancel)) }
        }
    )
}
