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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.blockline.app.data.ManifestTextResolver
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
fun OverviewScreen(vm: AppViewModel, onBack: () -> Unit, onExplorer: () -> Unit, onTools: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val p = vm.current
    val localeTag = context.resources.configuration.locales[0].toLanguageTag()
    var showSwitcher by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(ExportMode.BOTH) }
    var chooseExport by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && p != null) vm.exportProject(uri, exportMode)
    }
    val allProjectEntries = p?.let { vm.projectEntries[it.id].orEmpty() }.orEmpty()
    val projectFiles by produceState(
        initialValue = emptyList<java.io.File>(),
        key1 = p?.id,
        key2 = allProjectEntries
    ) {
        value = withContext(Dispatchers.Default) {
            allProjectEntries.filter { it.isFile }
        }
    }
    val manifestFiles = remember(projectFiles) {
        projectFiles.filter { it.name.equals("manifest.json", true) || it.name.equals("pack_manifest.json", true) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(p?.name ?: stringResource(R.string.overview)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.FolderOpen, stringResource(R.string.back_cd)) } }, actions = { IconButton(onClick = { showSwitcher = true }) { Icon(Icons.Default.SwapHoriz, stringResource(R.string.switch_project_cd)) } }) }) { pad ->
        if (p == null) EmptyState(Icons.Default.FolderOpen, stringResource(R.string.no_project_selected), stringResource(R.string.choose_project_hint), Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.validation_counts,
                                    vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR },
                                    vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING },
                                    vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.INFO }
                                ),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val previewIssues = vm.validationIssues
                                .filter { it.severity == com.blockline.app.data.IssueSeverity.ERROR }
                                .ifEmpty { vm.validationIssues.filter { it.severity == com.blockline.app.data.IssueSeverity.WARNING } }
                                .take(2)
                            previewIssues.forEach { issue ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        if (issue.line != null) stringResource(R.string.line_result, issue.path.ifBlank { stringResource(R.string.project_label) }, issue.line)
                                        else issue.path.ifBlank { stringResource(R.string.project_label) },
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        localizeValidationMessage(context, issue.message),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (vm.validationCompleted && vm.validationIssues.isNotEmpty()) {
                                TextButton(onClick = onTools) { Text(stringResource(R.string.view_validation_report)) }
                            }
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
                val rawManifestName = header?.optString("name").orEmpty()
                val rawManifestDescription = header?.optString("description").orEmpty()
                val localizedManifestText by produceState(
                    initialValue = rawManifestName to rawManifestDescription,
                    key1 = file.absolutePath,
                    key2 = file.lastModified() + file.length(),
                    key3 = localeTag
                ) {
                    val packDirectory = file.parentFile
                    value = if (packDirectory == null) {
                        rawManifestName to rawManifestDescription
                    } else {
                        withContext(Dispatchers.IO) {
                            ManifestTextResolver.resolve(packDirectory, rawManifestName, localeTag, "pack.name").orEmpty() to
                                ManifestTextResolver.resolve(packDirectory, rawManifestDescription, localeTag, "pack.description").orEmpty()
                        }
                    }
                }
                val modules = json?.optJSONArray("modules")
                val dependencies = json?.optJSONArray("dependencies")
                val capabilities = json?.optJSONArray("capabilities")
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                ) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.manifest_title, file.parentFile?.name ?: stringResource(R.string.pack)), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(
                        if (header == null) stringResource(R.string.manifest_parse_failed)
                        else localizedManifestText.first.ifBlank { stringResource(R.string.unknown_pack) },
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (header == null) androidx.compose.ui.text.AnnotatedString(stringResource(R.string.check_manifest_json))
                        else minecraftPreviewText(
                            localizedManifestText.second.ifBlank { stringResource(R.string.no_description) },
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
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
                    Text(stringResource(R.string.manifest_engine_value, header?.opt("min_engine_version")?.let(::manifestValueText) ?: stringResource(R.string.unknown_value)))
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

private fun manifestValueText(value: Any): String = when (value) {
    is org.json.JSONArray -> (0 until value.length()).joinToString(".") { value.optString(it) }
    is org.json.JSONObject -> "Object (${value.length()} fields)"
    else -> value.toString()
}
