package com.blockline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockline.app.AppViewModel
import com.blockline.app.ui.theme.EditorPalette

/**
 * VS Code-like frame around the existing editor. The editor body is supplied by the
 * current EditorScreen so its file operations, autosave, history and validation remain authoritative.
 */
@Composable
fun EditorWorkspace(vm: AppViewModel, onBack: () -> Unit, editor: @Composable () -> Unit) {
    var showExplorer by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var commandQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(EditorPalette.editor)) {
        CommandBar(
            projectName = vm.current?.name ?: "Blockline",
            onOpenPalette = { showCommandPalette = true },
            onBack = onBack
        )
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ActivityBar(
                explorerSelected = showExplorer,
                onExplorer = { showExplorer = !showExplorer },
                onSearch = { showCommandPalette = true },
                onSettings = { showCommandPalette = true }
            )
            if (showExplorer) {
                ExplorerRail(
                    vm = vm,
                    onOpenFile = { path ->
                        vm.openFile(path)
                        showExplorer = false
                    },
                    modifier = Modifier.width(220.dp).fillMaxHeight()
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                EditorTabStrip(vm)
                editor()
            }
        }
        EditorStatusBar(vm)
    }

    if (showCommandPalette) {
        AlertDialog(
            onDismissRequest = { showCommandPalette = false; commandQuery = "" },
            title = { Text("Command Palette", fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = commandQuery,
                        onValueChange = { commandQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().border(1.dp, EditorPalette.border, RectangleShape).padding(12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (commandQuery.isEmpty()) Text("Search commands or files…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        }
                    )
                    val commands = remember(commandQuery) {
                        listOf("Go to File", "Go to Line", "Find", "Format Document", "Save File")
                            .filter { it.contains(commandQuery, ignoreCase = true) }
                    }
                    commands.forEach { command ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                when (command) {
                                    "Save File" -> vm.saveCurrent()
                                    "Go to File" -> showExplorer = true
                                    "Find" -> Unit
                                    "Format Document" -> Unit
                                    "Go to Line" -> Unit
                                }
                                showCommandPalette = false
                                commandQuery = ""
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(command, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommandPalette = false; commandQuery = "" }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun CommandBar(projectName: String, onOpenPalette: () -> Unit, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(EditorPalette.sidebar)
            .border(width = 0.5.dp, color = EditorPalette.border, shape = RectangleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back to previous screen", tint = Color(0xFFCCCCCC))
        }
        Surface(
            modifier = Modifier.weight(1f).height(32.dp).clickable(onClick = onOpenPalette),
            color = EditorPalette.editor,
            shape = RectangleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, EditorPalette.border)
        ) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9D9D9D), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search commands and files", color = Color(0xFFBDBDBD), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(projectName, color = Color(0xFFCCCCCC), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActivityBar(
    explorerSelected: Boolean,
    onExplorer: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier.width(48.dp).fillMaxHeight().background(EditorPalette.activityBar)
            .border(width = 0.5.dp, color = EditorPalette.border, shape = RectangleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ActivityIcon(Icons.Default.FolderOpen, "Explorer", explorerSelected, onExplorer)
        ActivityIcon(Icons.Default.Search, "Search", false, onSearch)
        Spacer(Modifier.weight(1f))
        ActivityIcon(Icons.Default.Settings, "Quick settings", false, onSettings)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ActivityIcon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(34.dp).background(if (selected) Color(0xFFFFFFFF) else Color.Transparent))
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(image, contentDescription = label, tint = if (selected) Color.White else Color(0xFFBDBDBD), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ExplorerRail(vm: AppViewModel, onOpenFile: (String) -> Unit, modifier: Modifier = Modifier) {
    val project = vm.current
    val files = project?.let { vm.projectEntries[it.id] }.orEmpty()
    Column(
        modifier.background(EditorPalette.sidebar)
            .border(width = 0.5.dp, color = EditorPalette.border, shape = RectangleShape)
            .padding(vertical = 8.dp)
    ) {
        Text("EXPLORER", color = Color(0xFFBBBBBB), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        Text(project?.name ?: "No project", color = Color(0xFFE0E0E0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        androidx.compose.foundation.lazy.LazyColumn {
            items(count = files.size, key = { index -> files[index].absolutePath }) { index ->
                val file = files[index]
                val path = file.relativeToOrNull(vm.repo.projectDir(project?.id ?: return@items))?.invariantSeparatorsPath ?: file.name
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = file.isFile) { onOpenFile(path) }.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            file.isDirectory -> "▰"
                            file.name == "manifest.json" -> "◈"
                            file.extension.equals("json", true) -> "▤"
                            file.extension.equals("js", true) -> "✦"
                            file.extension.equals("mcfunction", true) -> "▦"
                            file.extension.equals("png", true) -> "▧"
                            else -> "·"
                        },
                        color = EditorPalette.minecraftGreen,
                        fontSize = 14.sp,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(file.name, color = if (file.absolutePath == vm.activeFile) Color.White else Color(0xFFCCCCCC), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EditorTabStrip(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).background(EditorPalette.tabInactive)
            .border(width = 0.5.dp, color = EditorPalette.border, shape = RectangleShape)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        vm.openTabs.keys.toList().forEach { path ->
            val selected = vm.activeFile == path
            Row(
                Modifier.width(150.dp).fillMaxHeight()
                    .background(if (selected) EditorPalette.editor else EditorPalette.tabInactive)
                    .border(width = if (selected) 1.dp else 0.dp, color = if (selected) EditorPalette.minecraftGreen else Color.Transparent, shape = RectangleShape)
                    .clickable { vm.openFile(path) }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (path == vm.activeFile) "●" else " ", color = EditorPalette.minecraftGreen, fontSize = 8.sp)
                Spacer(Modifier.width(6.dp))
                Text(path.substringAfterLast('/'), color = if (selected) Color(0xFFFFFFFF) else Color(0xFFBBBBBB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.closeTab(path) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close ${path.substringAfterLast('/')}", tint = Color(0xFFBBBBBB), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorStatusBar(vm: AppViewModel) {
    val text = vm.content
    val line = text.count { it == '\n' } + 1
    val column = text.substringAfterLast('\n').length + 1
    val language = when (vm.activeFile?.substringAfterLast('.', "")?.lowercase()) {
        "json" -> "JSON"
        "js" -> "JavaScript"
        "mcfunction" -> "mcfunction"
        "html", "htm" -> "HTML"
        "txt" -> "Plain Text"
        else -> "Text"
    }
    Row(
        Modifier.fillMaxWidth().height(24.dp).background(EditorPalette.statusBar).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ln $line, Col $column", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Text("UTF-8", color = Color.White, fontSize = 10.sp)
        Text("Spaces: 4", color = Color.White, fontSize = 10.sp)
        Text(language, color = Color.White, fontSize = 10.sp)
    }
}
