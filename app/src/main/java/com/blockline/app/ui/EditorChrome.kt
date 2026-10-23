package com.blockline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockline.app.ui.theme.EditorTokens

@Composable
fun EditorActivityBar(
    selected: String,
    onExplorer: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    tokens: EditorTokens,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.width(50.dp).fillMaxHeight()
            .background(tokens.activityBarBackground)
            .border(1.dp, tokens.divider, RectangleShape),
        verticalArrangement = Arrangement.Top
    ) {
        ActivityBarButton("Explorer", Icons.Default.FolderOpen, selected == "explorer", onExplorer, tokens)
        ActivityBarButton("Search", Icons.Default.Search, selected == "search", onSearch, tokens)
        Spacer(Modifier.weight(1f))
        ActivityBarButton("Quick Settings", Icons.Default.Settings, selected == "settings", onSettings, tokens)
    }
}

@Composable
private fun ActivityBarButton(
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tokens: EditorTokens
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(28.dp).background(if (selected) tokens.statusBar else Color.Transparent))
        Box(
            Modifier.weight(1f).height(52.dp).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = description, tint = if (selected) tokens.foreground else tokens.muted, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
fun EditorSidebar(
    title: String,
    items: List<String>,
    selectedPath: String?,
    tokens: EditorTokens,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.width(210.dp).fillMaxHeight().background(tokens.sidebarBackground)
            .border(1.dp, tokens.divider, RectangleShape).then(modifier)
    ) {
        Text(
            title.uppercase(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            color = tokens.foreground,
            fontSize = 11.sp,
            letterSpacing = 0.7.sp
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.divider))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(items, key = { it }) { path ->
                val active = path == selectedPath
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        .background(if (active) tokens.selection else Color.Transparent)
                        .clickable { onSelect(path) }
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                        .semantics { contentDescription = path },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = fileTint(path, tokens),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        path,
                        color = tokens.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun fileTint(path: String, tokens: EditorTokens): Color = when {
    path.substringAfterLast('/', path).equals("manifest.json", true) -> tokens.tokenFunction
    path.endsWith(".json", true) -> tokens.tokenKey
    path.endsWith(".js", true) || path.endsWith(".ts", true) -> tokens.tokenKeyword
    path.endsWith(".mcfunction", true) -> tokens.gameGreen
    path.endsWith(".png", true) -> tokens.tokenString
    else -> tokens.muted
}

@Composable
fun EditorShortcutBar(
    enabled: Boolean,
    onInsert: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onSave: () -> Unit,
    onFind: () -> Unit,
    onHideKeyboard: () -> Unit,
    tokens: EditorTokens
) {
    val keys = listOf("Tab", "{", "}", "[", "]", "(", ")", "\"", ":", ";", ",", "§")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(tokens.sidebarBackground).padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            PixelKey(key, enabled, tokens) {
                onInsert(if (key == "Tab") "    " else key)
            }
        }
        PixelKey("↶", enabled, tokens, "Undo") { onUndo() }
        PixelKey("↷", enabled, tokens, "Redo") { onRedo() }
        PixelKey("FMT", enabled, tokens, "Format") { onFormat() }
        PixelKey("SAVE", enabled, tokens, "Save") { onSave() }
        PixelKey("FIND", true, tokens, "Find") { onFind() }
        PixelKey("⌄", true, tokens, "Hide keyboard") { onHideKeyboard() }
    }
}

@Composable
private fun RowScope.PixelKey(
    text: String,
    enabled: Boolean,
    tokens: EditorTokens,
    description: String = text,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.heightIn(min = 48.dp).width(if (text.length > 4) 56.dp else 48.dp)
            .border(1.dp, tokens.inventoryLight, RectangleShape)
            .border(2.dp, tokens.inventoryDark, RectangleShape)
            .clip(RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        color = if (enabled) tokens.tabInactive else tokens.sidebarBackground,
        shape = RectangleShape
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp)) {
            Text(
                text,
                color = if (enabled) tokens.foreground else tokens.muted,
                fontSize = if (text.length > 4) 10.sp else 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EditorStatusBar(
    line: Int,
    column: Int,
    language: String,
    saved: Boolean,
    tokens: EditorTokens
) {
    Row(
        Modifier.fillMaxWidth().background(tokens.statusBar)
            .horizontalScroll(rememberScrollState())
            .heightIn(min = 25.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Ln $line, Col $column", color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text("UTF-8", color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(language, color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text("Spaces: 4", color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(if (saved) "Saved" else "● Unsaved", color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun EditorCommandPalette(
    query: String,
    onQueryChange: (String) -> Unit,
    candidates: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Command Palette", fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text("Search commands and files") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(candidates.filter { it.contains(query, ignoreCase = true) }.take(40), key = { it }) { candidate ->
                        Text(
                            candidate,
                            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .clickable { onSelect(candidate) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
