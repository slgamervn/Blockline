package com.blockline.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel
import com.blockline.app.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class IndexedExplorerEntry(
    val file: File,
    val path: String,
    val ancestors: List<String>,
    val depth: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(vm: AppViewModel, onOpenEditor: () -> Unit) {
    val project = vm.current
    var expanded by remember(project?.id) { mutableStateOf(setOf<String>()) }
    var filter by remember(project?.id) { mutableStateOf("All") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var targetPath by remember { mutableStateOf("") }
    val importTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importTextDocument(uri) }
    val allEntries = project?.let { vm.projectEntries[it.id] }.orEmpty()
    var indexedEntries by remember(project?.id) { mutableStateOf(emptyList<IndexedExplorerEntry>()) }
    LaunchedEffect(allEntries, project?.id) {
        val projectId = project?.id
        indexedEntries = if (projectId == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                allEntries.map { file ->
                    val path = vm.repo.relative(projectId, file)
                    val segments = path.split('/')
                    IndexedExplorerEntry(
                        file = file,
                        path = path,
                        ancestors = (1 until segments.size).map { segments.take(it).joinToString("/") },
                        depth = path.count { it == '/' }
                    )
                }
            }
        }
    }
    val recentPaths = project?.let { vm.repo.recentFiles(it.id) }.orEmpty()
    var visibleEntries by remember(project?.id) { mutableStateOf(emptyList<IndexedExplorerEntry>()) }
    LaunchedEffect(indexedEntries, filter, recentPaths, expanded, project?.id) {
        val projectId = project?.id
        visibleEntries = if (projectId == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                val filtered = indexedEntries.filter { entry ->
                    when (filter) {
                        "Favorites" -> entry.file.isFile && vm.repo.isFavorite(projectId, entry.path)
                        "Recent" -> entry.file.isFile && entry.path in recentPaths
                        else -> true
                    }
                }
                val ordered = if (filter == "Recent") {
                    val recentOrder = recentPaths.withIndex().associate { it.value to it.index }
                    filtered.sortedBy { entry -> recentOrder[entry.path] ?: Int.MAX_VALUE }
                } else filtered
                if (filter != "All") ordered
                else ordered.filter { entry -> entry.ancestors.all { it in expanded } }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(project?.name ?: stringResource(R.string.nav_explorer)) }, actions = {
            IconButton(onClick = { dialog = "folder"; name = "" }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder_cd)) }
            IconButton(onClick = { dialog = "file"; name = "" }) { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, stringResource(R.string.new_file_cd)) }
            IconButton(
                onClick = { importTextLauncher.launch(arrayOf("text/*", "application/json", "application/javascript", "application/xml")) },
                enabled = project != null && !vm.isFileOperationRunning
            ) { Icon(Icons.Default.FileOpen, "Import text file") }
        })
    }) { pad ->
        if (project == null) {
            EmptyState(Icons.Default.Folder, stringResource(R.string.no_project_selected), stringResource(R.string.explorer_empty_hint), Modifier.padding(pad))
        } else {
            Column(Modifier.padding(pad).fillMaxSize()) {
                Row {
                    listOf("All", "Favorites", "Recent").forEach { choice ->
                        FilterChip(
                            selected = filter == choice,
                            onClick = { filter = choice },
                            label = { Text(stringResource(when (choice) { "Favorites" -> R.string.filter_favorites; "Recent" -> R.string.filter_recent; else -> R.string.filter_all })) },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                }
                if (vm.isIndexingProjectFiles || vm.isFileOperationRunning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(if (vm.isFileOperationRunning) R.string.file_operation_running else R.string.scanning_files), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                if (!vm.isIndexingProjectFiles && visibleEntries.isEmpty() &&
                    (indexedEntries.isNotEmpty() || allEntries.isEmpty())
                ) {
                    EmptyState(Icons.Default.Folder, stringResource(R.string.no_matching_files), stringResource(R.string.explorer_no_results_hint), Modifier.fillMaxSize())
                } else if (visibleEntries.isNotEmpty()) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(
                            items = visibleEntries,
                            key = { it.path },
                            contentType = { if (it.file.isDirectory) "folder" else "file" }
                        ) { entry ->
                            val file = entry.file
                            val path = entry.path
                            val depth = entry.depth
                            var menu by remember(path) { mutableStateOf(false) }
                            ListItem(
                                leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                                    headlineContent = { Text((if (file.isDirectory && expanded.contains(path)) "▾ " else if (file.isDirectory) "▸ " else "") + file.name) },
                                    supportingContent = { Text(path, maxLines = 1) },
                                    trailingContent = {
                                        Row {
                                            if (vm.isFavorite(path)) Icon(Icons.Default.Favorite, "Favorite")
                                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.file_actions_cd)) }
                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                if (file.isFile && isPreviewSupported(file.name)) DropdownMenuItem(text = { Text(stringResource(R.string.preview_resource)) }, onClick = { vm.loadResourcePreview(path); menu = false })
                                                if (file.isFile) DropdownMenuItem(text = { Text(stringResource(if (vm.isFavorite(path)) R.string.remove_favorite else R.string.add_favorite)) }, onClick = { vm.toggleFavorite(path); menu = false })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { targetPath = path; name = file.name; dialog = "rename"; menu = false })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.move)) }, onClick = {
                                                    targetPath = path
                                                    name = file.parentFile?.let { vm.repo.relative(project.id, it) }?.takeUnless { it == "." } ?: ""
                                                    dialog = "move"
                                                    menu = false
                                                })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { targetPath = path; dialog = "delete"; menu = false })
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(start = (depth * 12).coerceAtMost(72).dp).clickable {
                                        if (file.isDirectory) expanded = if (expanded.contains(path)) expanded - path else expanded + path
                                        else { vm.openFile(path); onOpenEditor() }
                                    }
                                )
                        }
                    }
                }
            }
        }
    }

    if (dialog == "file" || dialog == "folder" || dialog == "rename" || dialog == "move") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(when (dialog) { "rename" -> R.string.rename_item; "folder" -> R.string.create_folder; "move" -> R.string.move_item; else -> R.string.new_file })) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(if (dialog == "move") R.string.destination_folder_hint else R.string.name_or_path)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (dialog) {
                        "rename" -> vm.renameFile(targetPath, name)
                        "move" -> vm.moveFile(targetPath, name)
                        else -> vm.createFile(name.trim('/'), dialog == "folder")
                    }
                    dialog = null
                }) { Text(stringResource(if (dialog == "move") R.string.move else R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (vm.previewPath != null) {
        ModalBottomSheet(onDismissRequest = vm::clearResourcePreview) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.resource_preview_heading), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Text(vm.previewPath.orEmpty(), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                when {
                    vm.isLoadingPreview -> {
                        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                        Text(stringResource(R.string.resource_preview_loading))
                    }
                    vm.previewError != null -> Text(vm.previewError.orEmpty(), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    vm.resourcePreview != null -> {
                        val preview = vm.resourcePreview!!
                        Text(stringResource(resourceKindLabel(preview.kind)), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.resource_preview_size, preview.bytes))
                            Text(stringResource(R.string.resource_preview_lines, preview.lineCount))
                        }
                        if (preview.metadata.isNotEmpty()) {
                            Text(stringResource(R.string.resource_preview_metadata), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                            preview.metadata.forEach { field ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(field.first, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                        Text(field.second, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        } else {
                            Text(stringResource(R.string.resource_preview_no_metadata), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.resource_preview_source), style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                        Card(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                            Text(
                                preview.source,
                                Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                        if (preview.isTruncated) Text(stringResource(R.string.resource_preview_truncated), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = vm::clearResourcePreview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.resource_preview_close))
                }
            }
        }
    }
    if (dialog == "delete") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_item_title)) },
            text = { Text(stringResource(R.string.delete_item_confirm, targetPath)) },
            confirmButton = { TextButton(onClick = { vm.deleteFile(targetPath); dialog = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun isPreviewSupported(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("json", "mcmeta", "js", "mjs", "ts", "mcfunction", "lang", "properties")

private fun resourceKindLabel(kind: com.blockline.app.data.ResourceKind): Int = when (kind) {
    com.blockline.app.data.ResourceKind.MANIFEST -> R.string.resource_kind_manifest
    com.blockline.app.data.ResourceKind.ENTITY -> R.string.resource_kind_entity
    com.blockline.app.data.ResourceKind.ITEM -> R.string.resource_kind_item
    com.blockline.app.data.ResourceKind.BLOCK -> R.string.resource_kind_block
    com.blockline.app.data.ResourceKind.RECIPE -> R.string.resource_kind_recipe
    com.blockline.app.data.ResourceKind.LOOT_TABLE -> R.string.resource_kind_loot
    com.blockline.app.data.ResourceKind.FUNCTION -> R.string.resource_kind_function
    com.blockline.app.data.ResourceKind.SCRIPT -> R.string.resource_kind_script
    com.blockline.app.data.ResourceKind.LANGUAGE -> R.string.resource_kind_language
    com.blockline.app.data.ResourceKind.JSON -> R.string.resource_kind_json
}
