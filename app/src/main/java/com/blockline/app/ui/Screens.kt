package com.blockline.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel
import com.blockline.app.data.ExportMode
import com.blockline.app.ui.theme.ThemeMode
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: AppViewModel, onOpen: () -> Unit) {
    var actionProject by remember { mutableStateOf<com.blockline.app.data.Project?>(null) }
    var renameProject by remember { mutableStateOf<com.blockline.app.data.Project?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::importPack) }
    Scaffold(topBar = { TopAppBar(title = { Text("Blockline") }, actions = {
        IconButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Icon(Icons.Default.Add, "Import pack") }
    }) }, floatingActionButton = {
        FloatingActionButton(onClick = { vm.createSample(); onOpen() }) { Icon(Icons.Default.Add, "Create sample project") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Projects", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); Text("Offline Bedrock pack workspace") }
                Button(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Import") }
            }
            if (vm.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.projects.isEmpty()) EmptyState(Icons.Default.FolderOpen, "No projects yet", "Import a .mcpack/.mcaddon or create a sample pack", Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f)) {
                items(vm.projects, key = { it.id }) { p ->
                    var menu by remember(p.id) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(if (p.id == vm.current?.id) "Currently open" else "Last opened ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.lastOpened))}") },
                            leadingContent = { Icon(Icons.Default.Archive, null) },
                            trailingContent = {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Project actions") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Open") }, onClick = { vm.selectProject(p); menu = false; onOpen() })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { renameProject = p; renameValue = p.name; menu = false })
                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { vm.duplicateProject(p); menu = false })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { actionProject = p; menu = false })
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
        AlertDialog(onDismissRequest = { renameProject = null }, title = { Text("Rename project") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, label = { Text("Project name") }) },
            confirmButton = { TextButton(onClick = { vm.renameProject(p, renameValue); renameProject = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameProject = null }) { Text("Cancel") } })
    }
    actionProject?.let { p ->
        AlertDialog(onDismissRequest = { actionProject = null }, title = { Text("Delete project?") },
            text = { Text("Delete ${p.name} and its local copy? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p); actionProject = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { actionProject = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(vm: AppViewModel, onBack: () -> Unit, onExplorer: () -> Unit) {
    val p = vm.current
    val context = LocalContext.current
    var showSwitcher by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(ExportMode.BOTH) }
    var chooseExport by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && p != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { vm.repo.export(p.id, exportMode, it) } ?: error("Could not open export destination")
                vm.message = "Pack exported"
            } catch (e: Exception) { vm.message = e.message ?: "Export failed" }
        }
    }
    val manifestFiles = p?.let { vm.repo.allFiles(it.id).filter { f -> f.name == "manifest.json" } }.orEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text(p?.name ?: "Overview") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.FolderOpen, "Back") } }, actions = { IconButton(onClick = { showSwitcher = true }) { Icon(Icons.Default.SwapHoriz, "Switch project") } }) }) { pad ->
        if (p == null) EmptyState(Icons.Default.FolderOpen, "No project selected", "Choose a project from Projects", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PROJECT OVERVIEW", color = com.blockline.app.ui.theme.Emerald, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        Text(p.name, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                        Text("A local copy on this device. Your pack contents are not uploaded by Blockline.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                                Text("${vm.repo.allFiles(p.id).size} files", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            }
                            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                                Text("ID · ${p.id.take(8)}", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            items(manifestFiles) { file ->
                val json = runCatching { org.json.JSONObject(com.blockline.app.data.JsonTools.stripComments(file.readText())) }.getOrNull()
                Card { Column(Modifier.padding(16.dp)) {
                    Text("${file.parentFile?.name ?: "Pack"} manifest", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(json?.optJSONObject("header")?.optString("name", "Unknown pack") ?: "Manifest could not be parsed")
                    Text(json?.optJSONObject("header")?.optString("description", "No description") ?: "Check manifest.json syntax")
                    Text("UUID: ${json?.optJSONObject("header")?.optString("uuid", "Missing") ?: "Invalid"}")
                    Text("Version: ${json?.optJSONObject("header")?.optJSONArray("version")?.let { a -> (0 until a.length()).joinToString(".") { a.optString(it) } } ?: "Unknown"}")
                    Text("Modules: ${json?.optJSONArray("modules")?.length() ?: 0}")
                    TextButton(onClick = { vm.openFile(vm.repo.relative(p.id, file)); onExplorer() }) { Text("Open manifest") }
                } }
            }
            item { Text("Quick actions", style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
            item { Button(onClick = onExplorer, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Text("  Open Explorer") } }
            item { Button(onClick = { vm.backup() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Text("  Create backup") } }
            item { Button(onClick = { vm.restore() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Text("  Restore latest backup") } }
            item { Button(onClick = { chooseExport = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Text("  Export pack") } }
        }
    }
    if (showSwitcher) ModalBottomSheet(onDismissRequest = { showSwitcher = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Switch project", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            vm.projects.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(if (item.id == vm.current?.id) "Current project" else "Local project") },
                    leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                    modifier = Modifier.clickable { vm.selectProject(item); showSwitcher = false }
                )
            }
        }
    }
    if (chooseExport) AlertDialog(
        onDismissRequest = { chooseExport = false },
        title = { Text("Export pack") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { exportMode = ExportMode.BP; chooseExport = false; exporter.launch("${p?.name ?: "pack"}.mcpack") }) { Text("Behavior pack (.mcpack)") }
            TextButton(onClick = { exportMode = ExportMode.RP; chooseExport = false; exporter.launch("${p?.name ?: "pack"}.mcpack") }) { Text("Resource pack (.mcpack)") }
            TextButton(onClick = { exportMode = ExportMode.BOTH; chooseExport = false; exporter.launch("${p?.name ?: "pack"}.mcaddon") }) { Text("Both packs (.mcaddon)") }
        } },
        confirmButton = { TextButton(onClick = { chooseExport = false }) { Text("Cancel") } }
    )
}

@Composable
fun SearchScreen(vm: AppViewModel, onOpenEditor: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var contentMode by remember { mutableStateOf(false) }
    val p = vm.current
    val pathMatches = p?.let { project ->
        vm.repo.allFiles(project.id).filter { vm.repo.relative(project.id, it).contains(query, true) }
    }.orEmpty()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search project", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.clearContentSearch() },
            label = { Text(if (contentMode) "Search inside text files" else "File name or path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = !contentMode, onClick = { contentMode = false }, label = { Text("File paths") })
            androidx.compose.material3.FilterChip(selected = contentMode, onClick = { contentMode = true }, label = { Text("File contents") })
            if (contentMode) Button(onClick = { vm.searchProjectContentAsync(query) }, enabled = query.isNotBlank() && !vm.isSearchingContent) { Text(if (vm.isSearchingContent) "Searching…" else "Search") }
        }
        if (p == null) {
            Text("Select a project first.")
        } else if (!contentMode) {
            if (pathMatches.isEmpty()) Text(if (query.isBlank()) "Enter a file name or path." else "No matching files")
            else LazyColumn(Modifier.weight(1f)) {
                items(pathMatches, key = { it.path }) { file ->
                    val path = vm.repo.relative(p.id, file)
                    ListItem(headlineContent = { Text(file.name) }, supportingContent = { Text(path) }, modifier = Modifier.clickable { vm.openFile(path); onOpenEditor() })
                }
            }
        } else {
            if (vm.contentSearchResults.isEmpty()) Text(if (query.isBlank()) "Enter text, then tap Search." else "No results yet. Tap Search to scan the project.")
            else LazyColumn(Modifier.weight(1f)) {
                items(vm.contentSearchResults, key = { "${it.first}:${it.second}" }) { result ->
                    ListItem(
                        headlineContent = { Text(result.first.substringAfterLast('/')) },
                        supportingContent = { Text("${result.first} · line ${result.second}") },
                        modifier = Modifier.clickable { vm.openFile(result.first); onOpenEditor() }
                    )
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Project tools", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall) }
        item { Text("Tools operate on the selected local project only.") }
        item {
            Button(onClick = { vm.validateCurrentProject() }, enabled = vm.current != null && !vm.isValidating, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.isValidating) "Validating project…" else "Validate Bedrock pack")
            }
        }
        if (vm.validationIssues.isNotEmpty()) {
            item {
                val errors = vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.ERROR }
                val warnings = vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.WARNING }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Validation report", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        Text("$errors errors · $warnings warnings · ${vm.validationIssues.count { it.severity == com.blockline.app.data.IssueSeverity.INFO }} info")
                        Text("External dependencies are informational when their UUID is not present in this project.")
                    }
                }
            }
            items(vm.validationIssues) { issue ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(issue.severity.name, color = when (issue.severity) {
                            com.blockline.app.data.IssueSeverity.ERROR -> androidx.compose.material3.MaterialTheme.colorScheme.error
                            com.blockline.app.data.IssueSeverity.WARNING -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                            com.blockline.app.data.IssueSeverity.INFO -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                        }, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        Text(issue.path.ifBlank { "Project" }, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        Text(if (issue.line != null) "Line ${issue.line}${issue.column?.let { ":$it" } ?: ""} · ${issue.message}" else issue.message)
                    }
                }
            }
        } else {
            item { Text(if (vm.isValidating) "Scanning JSON files and manifests…" else "Run validation to see JSON, manifest, UUID, and dependency findings.") }
        }
        item { Button(onClick = { vm.backup() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Backup, null); Text("  Create backup") } }
        item { Button(onClick = { vm.restore() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Text("  Restore latest backup") } }
        item { Button(onClick = { vm.current?.let { vm.message = "Project contains ${vm.repo.allFiles(it.id).size} files" } ?: run { vm.message = "Select a project first" } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Text("  Count project files") } }
    }
}

@Composable
fun MoreScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("More", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
            Text("Tune your workspace to fit the way you code.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Surface(color = com.blockline.app.ui.theme.Emerald.copy(alpha = .16f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Text("◐", Modifier.padding(12.dp), color = com.blockline.app.ui.theme.Emerald, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    }
                    Column {
                        Text("Appearance", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text("Choose the theme used throughout Blockline.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = vm.themeMode == ThemeMode.DARK, onClick = { vm.themeMode = ThemeMode.DARK }, label = { Text("Dark") })
                    androidx.compose.material3.FilterChip(selected = vm.themeMode == ThemeMode.LIGHT, onClick = { vm.themeMode = ThemeMode.LIGHT }, label = { Text("Light") })
                    androidx.compose.material3.FilterChip(selected = vm.themeMode == ThemeMode.SYSTEM, onClick = { vm.themeMode = ThemeMode.SYSTEM }, label = { Text("System") })
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
                        Text("Editor typography", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text("Monospace editor text · ${vm.fontSize} sp", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { vm.fontSize = (vm.fontSize - 1).coerceAtLeast(10) }, enabled = vm.fontSize > 10) { Text("A− Smaller") }
                    Button(onClick = { vm.fontSize = (vm.fontSize + 1).coerceAtMost(24) }, enabled = vm.fontSize < 24) { Text("A+ Larger") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About Blockline", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Mobile-first tools for Minecraft Bedrock add-on creators.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Version", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Text("0.1.0 · Phase 1")
                }
                androidx.compose.material3.HorizontalDivider()
                Text("Privacy", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text("Project files stay in app-private storage. Phase 1 does not require an internet connection and includes no analytics or tracking.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Blockline is in early development. Some advanced features are planned for later phases.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
