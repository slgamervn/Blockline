package com.blockline.app.ui

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
                            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
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
                val json = runCatching { org.json.JSONObject(com.blockline.app.data.JsonTools.stripComments(file.readText())) }.getOrNull()
                val header = json?.optJSONObject("header")
                val modules = json?.optJSONArray("modules")
                val dependencies = json?.optJSONArray("dependencies")
                val capabilities = json?.optJSONArray("capabilities")
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.manifest_title, file.parentFile?.name ?: stringResource(R.string.pack)), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(header?.optString("name", stringResource(R.string.unknown_pack)) ?: stringResource(R.string.manifest_parse_failed), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(header?.optString("description", stringResource(R.string.no_description)) ?: stringResource(R.string.check_manifest_json))
                    Text(stringResource(R.string.uuid_value, header?.optString("uuid", stringResource(R.string.missing_value)) ?: stringResource(R.string.invalid_value)))
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
            onValueChange = { query = it; vm.clearContentSearch() },
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
