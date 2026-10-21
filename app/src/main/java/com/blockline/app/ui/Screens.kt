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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.blockline.app.ui.theme.ThemeMode
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: AppViewModel, onOpen: () -> Unit) {
    var actionProject by remember { mutableStateOf<com.blockline.app.data.Project?>(null) }
    var renameProject by remember { mutableStateOf<com.blockline.app.data.Project?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::importPack) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
        IconButton(onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) }) { Icon(Icons.Default.Add, stringResource(R.string.import_pack_cd)) }
    }) }, floatingActionButton = {
        FloatingActionButton(onClick = { vm.createSample(); onOpen() }) { Icon(Icons.Default.Add, stringResource(R.string.create_sample_cd)) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(stringResource(R.string.nav_projects), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.projects_subtitle)) }
                Button(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.import_pack)) }
            }
            if (vm.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.projects.isEmpty()) EmptyState(Icons.Default.FolderOpen, stringResource(R.string.no_projects), stringResource(R.string.no_projects_hint), Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f)) {
                items(vm.projects, key = { it.id }) { p ->
                    var menu by remember(p.id) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(if (p.id == vm.current?.id) stringResource(R.string.current_open) else stringResource(R.string.last_opened_date, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.lastOpened)))) },
                            leadingContent = { Icon(Icons.Default.Archive, null) },
                            trailingContent = {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.project_actions)) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { vm.selectProject(p); menu = false; onOpen() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { renameProject = p; renameValue = p.name; menu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { vm.duplicateProject(p); menu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { actionProject = p; menu = false })
                                }
                            },
                            modifier = Modifier.clickable { vm.selectProject(p); onOpen() }
                        )
                    }
                }
            }
        }
    }
    renameProject?.let { p ->
        AlertDialog(onDismissRequest = { renameProject = null }, title = { Text(stringResource(R.string.rename_project_title)) },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, label = { Text(stringResource(R.string.project_name)) }) },
            confirmButton = { TextButton(onClick = { vm.renameProject(p, renameValue); renameProject = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameProject = null }) { Text(stringResource(R.string.cancel)) } })
    }
    actionProject?.let { p ->
        AlertDialog(onDismissRequest = { actionProject = null }, title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_confirm, p.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p); actionProject = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { actionProject = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(vm: AppViewModel, onBack: () -> Unit, onExplorer: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val p = vm.current
    var showSwitcher by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(ExportMode.BOTH) }
    var chooseExport by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && p != null) vm.exportProject(uri, exportMode)
    }
    val projectFiles = p?.let { vm.projectEntries[it.id].orEmpty() }.orEmpty().filter { it.isFile }
    val manifestFiles = projectFiles.filter { it.name.equals("manifest.json", true) || it.name.equals("pack_manifest.json", true) }
    Scaffold(topBar = { TopAppBar(title = { Text(p?.name ?: stringResource(R.string.overview)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.FolderOpen, stringResource(R.string.back_cd)) } }, actions = { IconButton(onClick = { showSwitcher = true }) { Icon(Icons.Default.SwapHoriz, stringResource(R.string.switch_project_cd)) } }) }) { pad ->
        if (p == null) EmptyState(Icons.Default.FolderOpen, stringResource(R.string.no_project_selected), stringResource(R.string.choose_project_hint), Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.project_overview), color = com.blockline.app.ui.theme.Emerald, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        Text(p.name, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.project_local_notice), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                                Text(if (vm.isIndexingProjectFiles) stringResource(R.string.scanning_files) else stringResource(R.string.files_count, projectFiles.size), Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            }
                            androidx.compose.material3.Surface(
                                modifier = Modifier.clickable {
                                    clipboard.setText(AnnotatedString(p.id))
                                    vm.message = context.getString(R.string.project_id_copied)
                                },
                                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                            ) {
                                Text("ID · ${p.id.take(8)}", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item {
                val score = vm.packHealthScore
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.pack_health_score), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text(
                                score?.let { stringResource(R.string.pack_health_score_value, it) } ?: "— / 100",
                                color = if (score == null) androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant else com.blockline.app.ui.theme.Emerald,
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                            )
                        }
                        if (score == null) {
                            Text(stringResource(R.string.pack_health_score_waiting), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LinearProgressIndicator(progress = score / 100f, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.validation_counts,
                                    vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR },
                                    vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING },
                                    vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.INFO }
                                ),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { vm.validateCurrentProject() },
                            enabled = !vm.isValidating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(if (vm.isValidating) R.string.validating_project else R.string.validate_pack))
                        }
                    }
                }
            }
            items(manifestFiles, key = { it.absolutePath }) { file ->
                val json by produceState<org.json.JSONObject?>(
                    initialValue = null,
                    key1 = file.absolutePath,
                    key2 = file.lastModified(),
                    key3 = file.length()
                ) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            org.json.JSONObject(com.blockline.app.data.JsonTools.stripComments(file.readText(Charsets.UTF_8)))
                        }.getOrNull()
                    }
                }
                val header = json?.optJSONObject("header")
                val modules = json?.optJSONArray("modules")
                val dependencies = json?.optJSONArray("dependencies")
                val capabilities = json?.optJSONArray("capabilities")
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.manifest_title, file.parentFile?.name ?: stringResource(R.string.pack)), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(header?.optString("name", stringResource(R.string.unknown_pack)) ?: stringResource(R.string.manifest_parse_failed), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(header?.optString("description", stringResource(R.string.no_description)) ?: stringResource(R.string.check_manifest_json))
                    val uuidValue = header?.optString("uuid", stringResource(R.string.missing_value)) ?: stringResource(R.string.invalid_value)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.uuid_value, uuidValue), Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(uuidValue))
                                vm.message = context.getString(R.string.uuid_copied)
                            },
                            enabled = uuidValue.isNotBlank() && uuidValue != context.getString(R.string.missing_value) && uuidValue != context.getString(R.string.invalid_value)
                        ) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_uuid_cd)) }
                    }
                    Text(stringResource(R.string.version_value, header?.opt("version")?.let(::manifestValueText) ?: stringResource(R.string.unknown_value)))
                    Text(stringResource(R.string.manifest_format_value, json?.opt("format_version")?.let(::manifestValueText) ?: stringResource(R.string.unknown_value)))
                    Text(stringResource(R.string.manifest_engine_value, json?.opt("min_engine_version")?.let(::manifestValueText) ?: stringResource(R.string.unknown_value)))
                    Text(stringResource(R.string.modules_count, modules?.length() ?: 0), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    if (modules != null) for (index in 0 until modules.length()) {
                        val module = modules.optJSONObject(index) ?: continue
                        Text(stringResource(
                            R.string.manifest_module_line, index + 1,
                            module.optString("type", stringResource(R.string.unknown_value)),
                            module.optString("uuid", stringResource(R.string.missing_value)),
                            module.opt("version")?.let(::manifestValueText) ?: stringResource(R.string.unknown_value)
                        ), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.manifest_dependencies), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    if (dependencies == null || dependencies.length() == 0) {
                        Text(stringResource(R.string.manifest_no_dependencies), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else for (index in 0 until dependencies.length()) {
                        val dependency = dependencies.optJSONObject(index) ?: continue
                        val name = dependency.optString("uuid", dependency.optString("module_name", stringResource(R.string.unknown_value)))
                        val version = dependency.opt("version")?.let(::manifestValueText) ?: stringResource(R.string.unknown_value)
                        Text(stringResource(R.string.manifest_dependency_line, name, version), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    if (capabilities != null && capabilities.length() > 0) {
                        Text(stringResource(R.string.manifest_capabilities, (0 until capabilities.length()).joinToString(", ") { capabilities.optString(it) }), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.openFile(vm.repo.relative(p.id, file)); onExplorer() }) { Text(stringResource(R.string.open_manifest)) }
                } }
            }
            item { Text(stringResource(R.string.quick_actions), style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
            item { Button(onClick = onExplorer, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Text(stringResource(R.string.open_explorer)) } }
            item { Button(onClick = { vm.backup() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Text(stringResource(R.string.create_backup)) } }
            item { Button(onClick = { vm.restore() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Text(stringResource(R.string.restore_latest_backup)) } }
            item { Button(onClick = { chooseExport = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Text(stringResource(R.string.export_pack)) } }
        }
    }
    if (showSwitcher) ModalBottomSheet(onDismissRequest = { showSwitcher = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.switch_project_cd), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            vm.projects.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(stringResource(if (item.id == vm.current?.id) R.string.current_project else R.string.local_project)) },
                    leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                    modifier = Modifier.clickable { vm.selectProject(item); showSwitcher = false }
                )
            }
        }
    }
    if (chooseExport) AlertDialog(
        onDismissRequest = { chooseExport = false },
        title = { Text(stringResource(R.string.export_pack)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { exportMode = ExportMode.BP; chooseExport = false; exporter.launch("${p?.name ?: "pack"}.mcpack") }) { Text(stringResource(R.string.export_bp)) }
            TextButton(onClick = { exportMode = ExportMode.RP; chooseExport = false; exporter.launch("${p?.name ?: "pack"}.mcpack") }) { Text(stringResource(R.string.export_rp)) }
            TextButton(onClick = { exportMode = ExportMode.BOTH; chooseExport = false; exporter.launch("${p?.name ?: "pack"}.mcaddon") }) { Text(stringResource(R.string.export_both)) }
        } },
        confirmButton = { TextButton(onClick = { chooseExport = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun SearchScreen(vm: AppViewModel, onOpenEditor: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var contentMode by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(SearchFileFilter.ALL) }
    var replaceMode by remember { mutableStateOf(false) }
    var replacementText by remember { mutableStateOf("") }
    var showApplyReplaceConfirm by remember { mutableStateOf(false) }
    val p = vm.current
    val pathMatches = p?.let { project ->
        vm.projectEntries[project.id].orEmpty().filter {
            it.isFile && filter.matches(it, vm.repo.relative(project.id, it)) &&
                vm.repo.relative(project.id, it).contains(query, true)
        }
    }.orEmpty()
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

@Composable
fun ToolsScreen(vm: AppViewModel, onOpenEditor: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var generatedPackName by remember { mutableStateOf("") }
    var generatedPackDescription by remember { mutableStateOf("") }
    var generatedPackType by remember { mutableStateOf(GeneratedPackType.BEHAVIOR) }
    LaunchedEffect(vm.generatedFileToOpen) {
        val generatedPath = vm.generatedFileToOpen ?: return@LaunchedEffect
        vm.openFile(generatedPath)
        onOpenEditor()
        vm.consumeGeneratedFileToOpen()
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.project_tools), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall) }
        item { Text(stringResource(R.string.local_project_tools_hint)) }
        item { BatchRenameToolCard(vm) }
        item { DiffToolCard(vm) }
        item { ScriptApiReferenceCard(vm, onOpenEditor) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.pack_manifest_generator_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.pack_manifest_generator_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = generatedPackName,
                        onValueChange = { generatedPackName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.generated_pack_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = generatedPackDescription,
                        onValueChange = { generatedPackDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.generated_pack_description)) },
                        minLines = 2,
                        maxLines = 3
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = generatedPackType == GeneratedPackType.BEHAVIOR,
                            onClick = { generatedPackType = GeneratedPackType.BEHAVIOR },
                            label = { Text(stringResource(R.string.generated_behavior_pack)) }
                        )
                        androidx.compose.material3.FilterChip(
                            selected = generatedPackType == GeneratedPackType.RESOURCE,
                            onClick = { generatedPackType = GeneratedPackType.RESOURCE },
                            label = { Text(stringResource(R.string.generated_resource_pack)) }
                        )
                    }
                    Button(
                        onClick = {
                            runCatching {
                                PackManifestGenerator.generate(generatedPackName, generatedPackDescription, generatedPackType)
                            }.onSuccess { json ->
                                vm.createFileFromTemplate("${generatedPackType.folder}/manifest.json", json)
                            }.onFailure { error ->
                                vm.message = error.message ?: context.getString(R.string.create_failed)
                            }
                        },
                        enabled = vm.current != null && generatedPackName.isNotBlank() && !vm.isFileOperationRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.generate_manifest))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.snippet_library_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.snippet_library_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    builtInSnippets.forEach { snippet ->
                        androidx.compose.material3.HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(snippet.titleRes), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                Text(stringResource(snippet.languageRes), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(snippet.code))
                                vm.message = context.getString(R.string.snippet_copied)
                            }) {
                                Icon(Icons.Default.ContentCopy, null)
                                Text(stringResource(R.string.snippet_copy))
                            }
                        }
                        Text(stringResource(snippet.descriptionRes), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(snippet.code, fontFamily = FontFamily.Monospace, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.dependency_viewer_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.dependency_viewer_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { vm.loadCurrentProjectDependencies() },
                        enabled = vm.current != null && !vm.isLoadingDependencies,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (vm.isLoadingDependencies) R.string.dependency_analyzing else R.string.dependency_analyze))
                    }
                    if (vm.isLoadingDependencies) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (vm.dependencyAnalysisCompleted && vm.dependencies.isEmpty()) {
                        Text(stringResource(R.string.dependency_none))
                    } else if (vm.dependencies.isNotEmpty()) {
                        Text(stringResource(R.string.dependency_count, vm.dependencies.size), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (vm.dependencies.isNotEmpty()) {
            items(
                vm.dependencies,
                key = { "${it.sourcePath}:${it.target}:${it.kind}:${it.requestedVersion.orEmpty()}" }
            ) { dependency ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(
                                when (dependency.kind) {
                                    DependencyKind.INTERNAL_PACK -> R.string.dependency_internal_pack
                                    DependencyKind.EXTERNAL_MODULE -> R.string.dependency_external_module
                                    DependencyKind.EXTERNAL_UUID -> R.string.dependency_external_uuid
                                    DependencyKind.INVALID -> R.string.dependency_invalid
                                }
                            ),
                            color = when {
                                dependency.versionMismatch || dependency.kind == DependencyKind.INVALID -> androidx.compose.material3.MaterialTheme.colorScheme.error
                                dependency.kind == DependencyKind.INTERNAL_PACK -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                        )
                        Text(stringResource(R.string.dependency_source, dependency.sourceName, dependency.sourcePath))
                        Text(stringResource(R.string.dependency_target, dependency.target))
                        dependency.requestedVersion?.let { Text(stringResource(R.string.dependency_requested_version, it)) }
                        dependency.targetPath?.let { Text(stringResource(R.string.dependency_installed_path, it)) }
                        dependency.installedVersion?.let { Text(stringResource(R.string.dependency_installed_version, it)) }
                        if (dependency.versionMismatch) {
                            Text(stringResource(R.string.dependency_version_mismatch), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                        if (dependency.kind == DependencyKind.EXTERNAL_UUID) {
                            Text(stringResource(R.string.dependency_external_uuid_note), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        if (dependency.kind == DependencyKind.INVALID) {
                            Text(stringResource(R.string.dependency_invalid_note), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { vm.validateCurrentProject() }, enabled = vm.current != null && !vm.isValidating, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (vm.isValidating) R.string.validating_project else R.string.validate_pack))
            }
        }
        if (vm.validationIssues.isNotEmpty()) {
            item {
                val errors = vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR }
                val warnings = vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.validation_report), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.validation_counts, errors, warnings, vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.INFO }))
                        Text(stringResource(R.string.external_deps_note))
                    }
                }
            }
            items(vm.validationIssues) { issue ->
                Card(Modifier.fillMaxWidth().clickable(enabled = issue.path.isNotBlank()) {
                    vm.openFile(issue.path, issue.line)
                    onOpenEditor()
                }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(when (issue.severity) { com.blockline.app.data.IssueSeverity.ERROR -> R.string.severity_error; com.blockline.app.data.IssueSeverity.WARNING -> R.string.severity_warning; com.blockline.app.data.IssueSeverity.INFO -> R.string.severity_info }), color = when (issue.severity) {
                            com.blockline.app.data.IssueSeverity.ERROR -> androidx.compose.material3.MaterialTheme.colorScheme.error
                            com.blockline.app.data.IssueSeverity.WARNING -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                            com.blockline.app.data.IssueSeverity.INFO -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                        }, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        Text(issue.path.ifBlank { stringResource(R.string.project_label) }, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        val localizedMessage = localizeValidationMessage(context, issue.message)
                        Text(if (issue.line != null) stringResource(R.string.issue_line_column, issue.line, issue.column?.let { ":$it" } ?: "", localizedMessage) else localizedMessage)
                    }
                }
            }
        } else if (vm.validationCompleted) {
            item { Text(stringResource(R.string.no_issues_found)) }
        } else {
            item { Text(stringResource(if (vm.isValidating) R.string.scanning_files else R.string.validation_hint)) }
        }
        item { Button(onClick = { vm.backup() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Text(stringResource(R.string.create_backup)) } }
        item { Button(onClick = { vm.restore() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Text(stringResource(R.string.restore_latest_backup)) } }
        item { Button(onClick = { vm.current?.let { vm.message = context.getString(R.string.project_contains_files, vm.projectEntries[it.id].orEmpty().count { file -> file.isFile }) } ?: run { vm.message = context.getString(R.string.select_project_first) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Text(stringResource(R.string.count_project_files)) } }
    }
}

@Composable
fun MoreScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(vm.current?.id) { vm.refreshOperationLog() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.nav_more), style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.more_subtitle), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Surface(color = com.blockline.app.ui.theme.Emerald.copy(alpha = .16f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Text("◐", Modifier.padding(12.dp), color = com.blockline.app.ui.theme.Emerald, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    }
                    Column {
                        Text(stringResource(R.string.appearance), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.appearance_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = vm.themeMode == ThemeMode.DARK, onClick = { vm.themeMode = ThemeMode.DARK }, label = { Text(stringResource(R.string.dark)) })
                    androidx.compose.material3.FilterChip(selected = vm.themeMode == ThemeMode.LIGHT, onClick = { vm.themeMode = ThemeMode.LIGHT }, label = { Text(stringResource(R.string.light)) })
                    androidx.compose.material3.FilterChip(selected = vm.themeMode == ThemeMode.SYSTEM, onClick = { vm.themeMode = ThemeMode.SYSTEM }, label = { Text(stringResource(R.string.system)) })
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Text("Aa", Modifier.padding(10.dp), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text(stringResource(R.string.editor_typography), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.editor_font_size, vm.fontSize), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { vm.fontSize = (vm.fontSize - 1).coerceAtLeast(10) }, enabled = vm.fontSize > 10) { Text(stringResource(R.string.smaller_text)) }
                    Button(onClick = { vm.fontSize = (vm.fontSize + 1).coerceAtMost(24) }, enabled = vm.fontSize < 24) { Text(stringResource(R.string.larger_text)) }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.operation_log_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.operation_log_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { vm.clearOperationLog() },
                        enabled = vm.current != null && vm.operationLog.isNotEmpty()
                    ) { Text(stringResource(R.string.operation_log_clear)) }
                    TextButton(
                        onClick = {
                            val copiedText = vm.operationLog.takeLast(100).joinToString("\n") { entry ->
                                val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(entry.timestamp))
                                "$time · ${entry.action}${if (entry.detail.isBlank()) "" else " — ${entry.detail}"}"
                            }
                            clipboard.setText(AnnotatedString(copiedText))
                            vm.message = context.getString(R.string.operation_log_copied)
                        },
                        enabled = vm.operationLog.isNotEmpty()
                    ) { Text(stringResource(R.string.operation_log_copy)) }
                }
                if (vm.current == null) {
                    Text(stringResource(R.string.operation_log_select_project))
                } else if (vm.operationLog.isEmpty()) {
                    Text(stringResource(R.string.operation_log_empty), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    vm.operationLog.takeLast(20).asReversed().forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.action, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            if (entry.detail.isNotBlank()) Text(entry.detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            Text(
                                android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(entry.timestamp)),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                    if (vm.operationLog.size > 20) {
                        Text(stringResource(R.string.operation_log_more, vm.operationLog.size - 20), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_blockline), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_blurb), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.version), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.version_phase, BuildConfig.VERSION_NAME))
                }
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.privacy), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.privacy_detail), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.early_notice), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun manifestValueText(value: Any): String = when (value) {
    is org.json.JSONArray -> (0 until value.length()).joinToString(".") { value.optString(it) }
    is org.json.JSONObject -> "Object (${value.length()} fields)"
    else -> value.toString()
}

@Composable
private fun BatchRenameToolCard(vm: AppViewModel) {
    val project = vm.current
    val entries = project?.let { vm.projectEntries[it.id] }.orEmpty()
    val allPaths = remember(project?.id, entries) {
        project?.let { selected -> entries.map { vm.repo.relative(selected.id, it) } }.orEmpty()
    }
    val filePaths = remember(project?.id, entries) {
        project?.let { selected -> entries.filter { it.isFile }.map { vm.repo.relative(selected.id, it) } }.orEmpty()
    }
    var query by remember(project?.id) { mutableStateOf("") }
    var replacement by remember(project?.id) { mutableStateOf("") }
    var preview by remember(project?.id) { mutableStateOf<BatchRenamePreview?>(null) }
    var error by remember(project?.id) { mutableStateOf<String?>(null) }
    val previewFailedText = stringResource(R.string.batch_rename_preview_failed)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.batch_rename_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.batch_rename_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; preview = null; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.batch_rename_find)) },
                singleLine = true
            )
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it; preview = null; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.batch_rename_replace)) },
                singleLine = true
            )
            Button(
                onClick = {
                    runCatching {
                        BatchRenameEngine.preview(filePaths, allPaths.toSet(), query, replacement)
                    }.onSuccess {
                        preview = it
                        error = null
                    }.onFailure {
                        preview = null
                        error = previewFailedText
                    }
                },
                enabled = project != null && query.isNotBlank() && !vm.isFileOperationRunning,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.batch_rename_preview)) }
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            AnimatedVisibility(
                visible = preview != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 10 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 10 }
            ) {
                preview?.let { plan ->
                Text(stringResource(R.string.batch_rename_preview_count, plan.candidates.size, plan.conflicts.size))
                plan.candidates.take(30).forEach { candidate ->
                    Text(
                        "${candidate.oldPath}  →  ${candidate.newPath}",
                        fontFamily = FontFamily.Monospace,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                if (plan.candidates.size > 30) {
                    Text(stringResource(R.string.batch_rename_more, plan.candidates.size - 30), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                if (plan.conflicts.isNotEmpty()) {
                    plan.conflicts.take(5).forEach { conflict ->
                        val message = if (conflict.targetExists) {
                            stringResource(R.string.batch_rename_conflict_exists, conflict.sourcePaths.joinToString(), conflict.targetPath)
                        } else {
                            stringResource(R.string.batch_rename_conflict_duplicate, conflict.sourcePaths.joinToString(), conflict.targetPath)
                        }
                        Text(message, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    if (plan.conflicts.size > 5) Text(stringResource(R.string.batch_rename_more_conflicts, plan.conflicts.size - 5), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                } else if (plan.candidates.isEmpty()) {
                    Text(stringResource(R.string.batch_rename_no_matches))
                }
                Button(
                    onClick = { vm.applyBatchRename(plan.candidates); preview = null },
                    enabled = plan.candidates.isNotEmpty() && plan.conflicts.isEmpty() && !vm.isFileOperationRunning,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(if (vm.isFileOperationRunning) R.string.batch_rename_applying else R.string.batch_rename_apply)) }
                }
            }
        }
    }
}

@Composable
private fun DiffToolCard(vm: AppViewModel) {
    val project = vm.current
    val entries = project?.let { vm.projectEntries[it.id] }.orEmpty()
    val files = remember(project?.id, entries) {
        project?.let { selected ->
            entries.asSequence()
                .filter { it.isFile && it.length() <= 1024L * 1024L && it.extension.lowercase(java.util.Locale.ROOT) in TEXT_EXT }
                .map { vm.repo.relative(selected.id, it) }
                .toList()
        }.orEmpty()
    }
    var beforePath by remember(project?.id) { mutableStateOf("") }
    var afterPath by remember(project?.id) { mutableStateOf("") }
    var beforeMenu by remember { mutableStateOf(false) }
    var afterMenu by remember { mutableStateOf(false) }
    var diff by remember { mutableStateOf<com.blockline.app.data.LineDiffResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(project?.id, files) {
        val firstPath = files.firstOrNull().orEmpty()
        val secondPath = files.firstOrNull { it != firstPath }.orEmpty()
        if (beforePath !in files) beforePath = firstPath
        if (afterPath !in files || afterPath == firstPath) afterPath = secondPath
        diff = null
        error = null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.diff_tool_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.diff_tool_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    TextButton(onClick = { beforeMenu = true }, enabled = files.isNotEmpty()) {
                        Text(beforePath.ifBlank { stringResource(R.string.diff_choose_before) }, maxLines = 2)
                    }
                    DropdownMenu(expanded = beforeMenu, onDismissRequest = { beforeMenu = false }) {
                        files.take(250).forEach { path ->
                            DropdownMenuItem(text = { Text(path, maxLines = 1) }, onClick = {
                                beforePath = path
                                beforeMenu = false
                                diff = null
                            })
                        }
                    }
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    TextButton(onClick = { afterMenu = true }, enabled = files.isNotEmpty()) {
                        Text(afterPath.ifBlank { stringResource(R.string.diff_choose_after) }, maxLines = 2)
                    }
                    DropdownMenu(expanded = afterMenu, onDismissRequest = { afterMenu = false }) {
                        files.take(250).forEach { path ->
                            DropdownMenuItem(text = { Text(path, maxLines = 1) }, onClick = {
                                afterPath = path
                                afterMenu = false
                                diff = null
                            })
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val selectedProject = project ?: return@Button
                    loading = true
                    error = null
                    diff = null
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                require(beforePath in files && afterPath in files) { "Choose two readable text files." }
                                require(beforePath != afterPath) { "Choose two different files to compare." }
                                val before = vm.repo.resolve(selectedProject.id, beforePath)
                                val after = vm.repo.resolve(selectedProject.id, afterPath)
                                require(before.isFile && after.isFile) { "A selected file no longer exists." }
                                require(before.length() <= 1024L * 1024L && after.length() <= 1024L * 1024L) {
                                    "A selected file exceeded the 1 MB comparison limit."
                                }
                                LineDiffEngine.compare(before.readText(Charsets.UTF_8), after.readText(Charsets.UTF_8))
                            }
                        }
                        loading = false
                        result.onSuccess { diff = it }.onFailure { error = it.message ?: "Could not compare these files." }
                    }
                },
                enabled = project != null && beforePath.isNotBlank() && afterPath.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (loading) R.string.diff_comparing else R.string.diff_compare))
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            AnimatedVisibility(
                visible = diff != null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 10 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 10 }
            ) {
            diff?.let { result ->
                if (result.limited) {
                    Text(stringResource(R.string.diff_limit_reached), color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary)
                } else {
                    Text(stringResource(R.string.diff_summary, result.additions, result.removals))
                    result.lines.take(120).forEach { line ->
                        val prefix = when (line.kind) {
                            DiffLineKind.ADDED -> "+"
                            DiffLineKind.REMOVED -> "−"
                            DiffLineKind.UNCHANGED -> " "
                        }
                        val tint = when (line.kind) {
                            DiffLineKind.ADDED -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                            DiffLineKind.REMOVED -> androidx.compose.material3.MaterialTheme.colorScheme.error
                            DiffLineKind.UNCHANGED -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            "$prefix ${line.oldLineNumber ?: "·"}:${line.newLineNumber ?: "·"}  ${line.text}",
                            color = tint,
                            fontFamily = FontFamily.Monospace,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 3
                        )
                    }
                    if (result.lines.size > 120) {
                        Text(stringResource(R.string.diff_more_lines, result.lines.size - 120), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ScriptApiReferenceCard(vm: AppViewModel, onOpenEditor: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repository = remember(context) { ScriptApiReferenceRepository(context) }
    var packageName by remember { mutableStateOf("@minecraft/server") }
    var version by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScriptApiLoadResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadReference(selectedPackage: String, selectedVersion: String?, refreshOnline: Boolean) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            val loaded = runCatching { repository.load(selectedPackage, selectedVersion, refreshOnline) }
            loading = false
            loaded.onSuccess {
                result = it
                packageName = selectedPackage
                version = it.version
                error = it.error
            }.onFailure {
                error = it.message ?: context.getString(R.string.api_reference_load_failed)
            }
        }
    }

    LaunchedEffect(repository) {
        loadReference(packageName, null, false)
    }

    val filteredSymbols = remember(result, query) {
        result?.symbols.orEmpty().filter {
            query.isBlank() || it.name.contains(query, true) || it.kind.contains(query, true) ||
                it.documentation.contains(query, true)
        }.take(20)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.api_reference_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.api_reference_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("@minecraft/server", "@minecraft/server-ui", "@minecraft/common").forEach { candidate ->
                    androidx.compose.material3.FilterChip(
                        selected = packageName == candidate,
                        enabled = !loading,
                        onClick = {
                            if (packageName != candidate) {
                                packageName = candidate
                                version = ""
                                result = null
                                loadReference(candidate, null, false)
                            }
                        },
                        label = { Text(candidate.substringAfterLast('/')) }
                    )
                }
            }
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_reference_version)) },
                placeholder = { Text(stringResource(R.string.api_reference_latest_hint)) },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { loadReference(packageName, version.takeIf { it.isNotBlank() }, false) },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.api_reference_load)) }
                TextButton(
                    onClick = { version = ""; loadReference(packageName, null, true) },
                    enabled = !loading
                ) { Text(stringResource(R.string.api_reference_refresh)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            result?.let { loaded ->
                val sourceLabel = when (loaded.source) {
                    ScriptApiSource.LIVE -> stringResource(R.string.api_source_live)
                    ScriptApiSource.CACHE -> stringResource(R.string.api_source_cache)
                    ScriptApiSource.BUNDLED -> stringResource(R.string.api_source_bundled)
                }
                Text(stringResource(R.string.api_reference_status, loaded.packageName, loaded.version, sourceLabel))
                if (loaded.availableVersions.isNotEmpty()) {
                    Text(stringResource(R.string.api_reference_versions_cached, loaded.availableVersions.size), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_reference_search)) },
                singleLine = true
            )
            if (result != null && filteredSymbols.isEmpty()) {
                Text(stringResource(R.string.api_reference_no_symbols))
            }
            filteredSymbols.forEach { symbol ->
                androidx.compose.material3.HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${symbol.kind}  ${symbol.name}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(symbol.signature, fontFamily = FontFamily.Monospace, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    if (symbol.documentation.isNotBlank()) {
                        Text(symbol.documentation, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(symbol.rawDeclaration))
                            vm.message = context.getString(R.string.api_reference_copied, symbol.name)
                        }) { Icon(Icons.Default.ContentCopy, null); Text(stringResource(R.string.api_reference_copy)) }
                        TextButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, symbol.name)
                                putExtra(Intent.EXTRA_TEXT, symbol.rawDeclaration)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.api_reference_share)))
                        }) { Text(stringResource(R.string.api_reference_share)) }
                        TextButton(
                            enabled = vm.activeFile?.substringAfterLast('.') == "ts" && !vm.readOnly,
                            onClick = {
                                val separator = if (vm.content.isBlank()) "" else "\n\n"
                                vm.updateContent(vm.content + separator + symbol.rawDeclaration)
                                onOpenEditor()
                            }
                        ) { Text(stringResource(R.string.api_reference_insert)) }
                    }
                }
            }
            if (result?.rawSource?.isNotBlank() == true) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(result!!.rawSource))
                    vm.message = context.getString(R.string.api_reference_raw_copied)
                }) { Text(stringResource(R.string.api_reference_copy_raw)) }
            }
        }
    }
}
