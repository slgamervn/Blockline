package com.blockline.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.blockline.app.ui.theme.EditorTokens
import com.blockline.app.R
import androidx.compose.ui.res.stringResource

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
        ActivityBarButton(stringResource(R.string.chrome_explorer), Icons.Default.FolderOpen, selected == "explorer", onExplorer, tokens)
        ActivityBarButton(stringResource(R.string.chrome_search), Icons.Default.Search, selected == "search", onSearch, tokens)
        Spacer(Modifier.weight(1f))
        ActivityBarButton(stringResource(R.string.chrome_quick_settings), Icons.Default.Settings, selected == "settings", onSettings, tokens)
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
                    PixelFileIcon(path, tokens)
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

@Composable
private fun PixelFileIcon(path: String, tokens: EditorTokens) {
    Canvas(Modifier.size(18.dp).semantics { contentDescription = path }) {
        val unit = size.width / 16f
        fun pixel(x: Int, y: Int, w: Int, h: Int, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(x * unit, y * unit),
                size = Size(w * unit, h * unit)
            )
        }
        val outline = Color(0xFF111111)
        when {
            path.substringAfterLast('/').equals("manifest.json", true) -> {
                pixel(3, 1, 10, 14, outline)
                pixel(4, 2, 8, 12, Color(0xFFB48E38))
                pixel(7, 3, 2, 8, tokens.tokenString)
                pixel(4, 6, 8, 2, tokens.tokenString)
                pixel(7, 6, 2, 2, Color.White)
            }
            path.endsWith(".json", true) -> {
                pixel(2, 1, 12, 14, outline)
                pixel(4, 2, 8, 12, Color(0xFF2D7DA8))
                pixel(5, 3, 6, 10, Color(0xFFE5F4FA))
                pixel(5, 5, 5, 1, tokens.tokenKey)
                pixel(5, 8, 4, 1, tokens.tokenKey)
                pixel(5, 11, 5, 1, tokens.tokenKey)
            }
            path.endsWith(".js", true) || path.endsWith(".ts", true) -> {
                pixel(2, 1, 12, 14, outline)
                pixel(4, 2, 8, 12, Color(0xFF5B3D8A))
                pixel(5, 3, 6, 10, Color(0xFFF3E8FF))
                pixel(6, 5, 4, 1, tokens.tokenKeyword)
                pixel(5, 8, 5, 1, tokens.tokenFunction)
                pixel(7, 10, 3, 1, tokens.tokenKeyword)
            }
            path.endsWith(".mcfunction", true) -> {
                pixel(1, 2, 14, 12, outline)
                pixel(3, 4, 10, 8, Color(0xFF52645A))
                pixel(4, 5, 8, 6, tokens.gameGreen)
                pixel(5, 6, 2, 2, Color(0xFFD5F8E4))
                pixel(9, 6, 2, 2, Color(0xFFD5F8E4))
                pixel(7, 8, 2, 2, outline)
            }
            path.endsWith(".png", true) || path.endsWith(".jpg", true) || path.endsWith(".webp", true) -> {
                pixel(1, 2, 14, 12, outline)
                pixel(3, 4, 10, 8, Color(0xFFEADFC7))
                pixel(4, 5, 3, 2, Color(0xFF52A3D9))
                pixel(9, 5, 2, 2, Color(0xFFF0C05A))
                pixel(4, 9, 7, 2, Color(0xFF3A8D57))
            }
            path.contains("behavior", true) || path.contains("resource", true) -> {
                val tint = if (path.contains("behavior", true)) tokens.gameGreen else tokens.tokenType
                pixel(1, 4, 14, 10, outline)
                pixel(2, 5, 12, 8, tint)
                pixel(3, 2, 10, 3, outline)
                pixel(4, 3, 8, 2, tint)
                pixel(7, 6, 2, 5, Color(0xFFB7A46B))
            }
            else -> {
                pixel(1, 4, 14, 10, outline)
                pixel(2, 5, 12, 8, Color(0xFF8F6A3B))
                pixel(3, 2, 10, 3, outline)
                pixel(4, 3, 8, 2, Color(0xFFB98B4F))
                pixel(7, 6, 2, 5, Color(0xFFDAC38C))
            }
        }
    }
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
        Text(stringResource(R.string.chrome_line_column, line, column), color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text("UTF-8", color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(language, color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(stringResource(R.string.chrome_spaces_four), color = Color.White, fontSize = 11.sp, maxLines = 1)
        Text(if (saved) stringResource(R.string.chrome_saved) else "● " + stringResource(R.string.chrome_unsaved), color = Color.White, fontSize = 11.sp, maxLines = 1)
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
    var matches by remember(candidates) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(query, candidates) {
        delay(80)
        val requested = query
        matches = withContext(Dispatchers.Default) {
            candidates.asSequence()
                .filter { it.contains(requested, ignoreCase = true) }
                .take(40)
                .toList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chrome_command_palette), fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.chrome_search_commands)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(matches, key = { it }) { candidate ->
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chrome_close)) } }
    )
}
