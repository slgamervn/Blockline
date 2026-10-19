package com.blockline.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel
import com.blockline.app.data.JsonTools
import com.blockline.app.ui.theme.Emerald
import com.blockline.app.ui.theme.OnSurface
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onBack: () -> Unit) {
    val path = vm.activeFile
    var findText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    val lang = path?.substringAfterLast('.', "")?.let { langFor(it) }
    LaunchedEffect(path, vm.content) {
        if (path != null && !vm.readOnly) {
            delay(900)
            vm.saveCurrent()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(path?.substringAfterLast('/') ?: "Editor"); if (path != null) Text(path, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.undo() }) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(onClick = { vm.redo() }) { Icon(Icons.Default.Redo, "Redo") }
                    IconButton(onClick = { showFind = true }) { Icon(Icons.Default.FindInPage, "Find") }
                    IconButton(onClick = {
                        if (lang == Lang.JSON) vm.updateContent(JsonTools.format(vm.content))
                        vm.saveCurrent()
                    }) { Icon(Icons.Default.Save, "Save", tint = Emerald) }
                }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().imePadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { vm.undo() }) { Icon(Icons.Default.Undo, null); Text("Undo") }
                TextButton(onClick = { vm.redo() }) { Icon(Icons.Default.Redo, null); Text("Redo") }
                TextButton(onClick = { if (lang == Lang.JSON) vm.updateContent(JsonTools.format(vm.content)) else vm.message = "Formatting is available for JSON files" }) { Icon(Icons.Default.FormatAlignLeft, null); Text("Format") }
                TextButton(onClick = { vm.saveCurrent() }) { Icon(Icons.Default.Save, null); Text("Save") }
                TextButton(onClick = { showFind = true }) { Icon(Icons.Default.FindInPage, null); Text("Find") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().padding(12.dp)) {
            if (path == null) {
                EmptyState(Icons.Default.ContentCopy, "No file open", "Choose a file in Explorer to start editing")
            } else {
                if (vm.readOnly) Text("Large file: read-only mode (over 1 MB)", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                BasicTextField(
                    value = vm.content,
                    onValueChange = { vm.updateContent(it) },
                    readOnly = vm.readOnly,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(color = OnSurface, fontFamily = FontFamily.Monospace, fontSize = vm.fontSize.sp, lineHeight = (vm.fontSize + 5).sp),
                    visualTransformation = if (lang != null && vm.content.length < 200_000) HighlightTransformation(lang, Palette(
                        androidx.compose.ui.graphics.Color(0xFF8FC9FF),
                        androidx.compose.ui.graphics.Color(0xFFB5E7A0),
                        androidx.compose.ui.graphics.Color(0xFFE6B35A),
                        Emerald,
                        androidx.compose.ui.graphics.Color(0xFF82958A)
                    )) else VisualTransformation.None
                )
            }
        }
    }
    if (showFind) AlertDialog(
        onDismissRequest = { showFind = false },
        title = { Text("Find in file") },
        text = { Column {
            OutlinedTextField(value = findText, onValueChange = { findText = it }, label = { Text("Text to find") }, singleLine = true)
            Text(if (findText.isBlank()) "Enter text to count matches" else "${Regex(Regex.escape(findText), RegexOption.IGNORE_CASE).findAll(vm.content).count()} matches")
        } },
        confirmButton = { TextButton(onClick = { showFind = false }) { Text("Close") } }
    )
}
