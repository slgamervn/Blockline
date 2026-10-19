package com.blockline.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(vm: AppViewModel, onOpenEditor: () -> Unit) {
    val project = vm.current
    var expanded by remember(project?.id) { mutableStateOf(setOf<String>()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var targetPath by remember { mutableStateOf("") }
    val entries = project?.let { vm.repo.allEntries(it.id) }.orEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text(project?.name ?: "Explorer") }, actions = {
        IconButton(onClick = { dialog = "folder"; name = "" }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
        IconButton(onClick = { dialog = "file"; name = "" }) { Icon(Icons.Default.InsertDriveFile, "New file") }
    }) }) { pad ->
        if (project == null) EmptyState(Icons.Default.Folder, "No project selected", "Open a project from Projects first", Modifier.padding(pad))
        else if (entries.isEmpty()) EmptyState(Icons.Default.Folder, "This project is empty", "Use the toolbar to create a file or folder", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(entries, key = { vm.repo.relative(project.id, it) }) { file ->
                val path = vm.repo.relative(project.id, file)
                val parentPath = file.parentFile?.let { vm.repo.relative(project.id, it) }
                val visible = parentPath == null || parentPath == "." || parentPath.isEmpty() || expanded.contains(parentPath)
                if (visible) {
                    var menu by remember(path) { mutableStateOf(false) }
                    val depth = path.count { it == '/' }
                    ListItem(
                        leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) },
                        headlineContent = { Text((if (file.isDirectory && expanded.contains(path)) "▾ " else if (file.isDirectory) "▸ " else "") + file.name) },
                        supportingContent = { Text(path, maxLines = 1) },
                        trailingContent = {
                            Row {
                                if (vm.isFavorite(path)) Icon(Icons.Default.Favorite, "Favorite")
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Actions") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (file.isFile) DropdownMenuItem(text = { Text(if (vm.isFavorite(path)) "Remove favorite" else "Add favorite") }, onClick = { vm.toggleFavorite(path); menu = false })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { targetPath = path; name = file.name; dialog = "rename"; menu = false })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { targetPath = path; dialog = "delete"; menu = false })
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
    if (dialog == "file" || dialog == "folder" || dialog == "rename") AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(if (dialog == "rename") "Rename item" else if (dialog == "folder") "Create folder" else "Create file") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name or relative path") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            if (dialog == "rename") vm.renameFile(targetPath, name)
            else vm.createFile(name.trim('/'), dialog == "folder")
            dialog = null
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
    )
    if (dialog == "delete") AlertDialog(
        onDismissRequest = { dialog = null }, title = { Text("Delete item?") },
        text = { Text("This permanently deletes $targetPath from this project copy.") },
        confirmButton = { TextButton(onClick = { vm.deleteFile(targetPath); dialog = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
    )
}
