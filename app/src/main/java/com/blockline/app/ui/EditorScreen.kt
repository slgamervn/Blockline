package com.blockline.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel
import com.blockline.app.R
import com.blockline.app.data.JsonTools
import com.blockline.app.data.AddonCompletionEngine
import com.blockline.app.data.ProjectValidator
import com.blockline.app.data.ValidationIssue
import com.blockline.app.ui.theme.Emerald
import com.blockline.app.ui.theme.OnSurface

private const val LARGE_FILE_PAGE_CHARS = 12_000
private const val LIVE_TEXT_ANALYSIS_MAX_CHARS = 12_000

private fun largeFilePageOffsets(text: String): List<Int> {
    if (text.isEmpty()) return listOf(0)
    val offsets = ArrayList<Int>((text.length / LARGE_FILE_PAGE_CHARS) + 1)
    offsets += 0
    var offset = LARGE_FILE_PAGE_CHARS
    while (offset < text.length) {
        if (offset > 0 && Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])) {
            offset++
        }
        if (offset >= text.length) break
        offsets += offset
        offset += LARGE_FILE_PAGE_CHARS
    }
    return offsets
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val path = vm.activeFile
    var findText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    var showHtmlPreview by remember(path) { mutableStateOf(false) }
    var editorValue by remember(path) { mutableStateOf(TextFieldValue(if (vm.readOnly) "" else vm.content)) }
    var largeFilePage by remember(path) { mutableStateOf(0) }
    var matchCount by remember(path) { mutableStateOf(0) }
    val lang = path?.substringAfterLast('.', "")?.let { langFor(it) }
    var highlightSource by remember(path) { mutableStateOf("") }
    var syntaxIssue by remember(path) { mutableStateOf<ValidationIssue?>(null) }
    var checkingJson by remember(path) { mutableStateOf(false) }
    val completionEngine = remember(context) { runCatching { AddonCompletionEngine.load(context) }.getOrNull() }
    var completionItems by remember(path) { mutableStateOf<List<AddonCompletionEngine.Suggestion>>(emptyList()) }
    var completionRange by remember(path) { mutableStateOf(0 to 0) }
    val largeOffsets = remember(vm.content, vm.readOnly) {
        if (vm.readOnly) largeFilePageOffsets(vm.content) else listOf(0)
    }
    val pageIndex = largeFilePage.coerceIn(0, (largeOffsets.size - 1).coerceAtLeast(0))
    val pageStart = largeOffsets[pageIndex]
    val pageEnd = largeOffsets.getOrNull(pageIndex + 1) ?: vm.content.length
    val pageText = remember(vm.content, pageStart, pageEnd, vm.readOnly) {
        if (vm.readOnly) {
            vm.content.substring(pageStart.coerceAtMost(vm.content.length), pageEnd.coerceAtMost(vm.content.length))
        } else {
            ""
        }
    }
    LaunchedEffect(showFind, findText, path, vm.content) {
        if (!showFind || findText.isBlank()) {
            matchCount = 0
        } else {
            delay(250)
            val query = findText
            val source = vm.content
            val count = withContext(Dispatchers.Default) {
                Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(source).count()
            }
            if (query == findText) matchCount = count
        }
    }
    LaunchedEffect(path, editorValue.text, editorValue.selection, vm.readOnly, completionEngine) {
        val engine = completionEngine
        val activePath = path
        if (engine == null || activePath == null || vm.readOnly || vm.isOpeningFile) {
            completionItems = emptyList()
            return@LaunchedEffect
        }
        val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
        val beforeCursor = editorValue.text.substring(0, cursor)
        val currentToken = Regex("[A-Za-z0-9_$:.-]*$").find(beforeCursor)?.value.orEmpty()
        val isManifest = activePath.substringAfterLast('/').equals("manifest.json", true)
        val isManifestUuidStart = isManifest && Regex("\\"uuid\\"\\s*:\\s*\\"$").containsMatchIn(beforeCursor)
        val isManifestFormatVersionStart = isManifest &&
            Regex("\\"format_version\\"\\s*:\\s*\\d*$").containsMatchIn(beforeCursor)
        val shouldSuggest = currentToken.isNotEmpty() || beforeCursor.endsWith(".") ||
            isManifestUuidStart || isManifestFormatVersionStart ||
            (isManifest && beforeCursor.trimEnd().endsWith("{"))
        if (!shouldSuggest) {
            completionItems = emptyList()
            return@LaunchedEffect
        }
        delay(140)
        val snapshotText = editorValue.text
        val snapshotCursor = cursor
        val result = withContext(Dispatchers.Default) {
            engine.suggest(AddonCompletionEngine.Request(activePath, snapshotText, snapshotCursor))
        }
        if (activePath == vm.activeFile && snapshotText == editorValue.text &&
            snapshotCursor == editorValue.selection.start
        ) {
            completionRange = result.start to result.end
            completionItems = result.items
        }
    }
    LaunchedEffect(path, editorValue.text, lang) {
        if (lang == null || editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS) {
            highlightSource = ""
        } else {
            delay(240)
            if (editorValue.text.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS) highlightSource = editorValue.text
        }
    }
    LaunchedEffect(path, editorValue.text, vm.readOnly, vm.isOpeningFile) {
        if (path == null || vm.readOnly || vm.isOpeningFile || editorValue.text == vm.content) return@LaunchedEffect
        val snapshot = editorValue.text
        delay(260)
        if (path == vm.activeFile && !vm.readOnly && !vm.isOpeningFile &&
            editorValue.text == snapshot && vm.content != snapshot
        ) {
            vm.updateContent(snapshot)
        }
    }
    LaunchedEffect(path, editorValue.text, lang, vm.readOnly) {
        if (lang != Lang.JSON || vm.readOnly || editorValue.text.isBlank() ||
            editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS
        ) {
            syntaxIssue = null
            checkingJson = false
        } else {
            checkingJson = true
            delay(320)
            val source = editorValue.text
            val issue = withContext(Dispatchers.Default) { ProjectValidator.inspectJsonSyntax(source) }
            if (source == editorValue.text) {
                syntaxIssue = issue
                checkingJson = false
            }
        }
    }
    val jsonErrorOffset = remember(editorValue.text, syntaxIssue) {
        syntaxIssue?.line?.let { targetLine ->
            var currentLine = 1
            var offset = 0
            while (offset < editorValue.text.length && currentLine < targetLine) {
                if (editorValue.text[offset] == '\n') currentLine++
                offset++
            }
            (offset + ((syntaxIssue?.column ?: 1) - 1)).coerceIn(0, editorValue.text.length)
        }
    }
    LaunchedEffect(path, vm.content, vm.readOnly) {
        if (vm.readOnly) {
            if (editorValue.text.isNotEmpty()) editorValue = TextFieldValue()
        } else if (editorValue.text != vm.content) {
            val cursor = editorValue.selection.start.coerceAtMost(vm.content.length)
            editorValue = TextFieldValue(vm.content, TextRange(cursor))
        }
    }
    LaunchedEffect(path, vm.pendingEditorLine, vm.content, vm.readOnly, largeOffsets) {
        val targetLine = vm.pendingEditorLine ?: return@LaunchedEffect
        if (path == null) return@LaunchedEffect
        val offset = if (targetLine <= 1) 0 else {
            var line = 1
            var position = 0
            while (position < vm.content.length && line < targetLine) {
                if (vm.content[position] == '\n') line++
                position++
            }
            position
        }
        if (vm.readOnly) {
            largeFilePage = largeOffsets.indexOfLast { it <= offset }.coerceAtLeast(0)
        } else {
            editorValue = TextFieldValue(vm.content, TextRange(offset.coerceIn(0, vm.content.length)))
        }
        vm.consumePendingEditorLine()
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Column { Text(path?.substringAfterLast('/') ?: stringResource(R.string.editor)); if (path != null) Text(path, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) } },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.saveCurrent(showMessage = false)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_cd)) }
                },
                actions = {
                    IconButton(onClick = {
                        if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.undo()
                    }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo_cd)) }
                    IconButton(onClick = {
                        if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        vm.redo()
                    }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo_cd)) }
                    if (path?.substringAfterLast('.', "")?.lowercase() in setOf("html", "htm")) {
                        IconButton(onClick = { showHtmlPreview = true }, enabled = !vm.isOpeningFile) {
                            Icon(Icons.Default.Visibility, "Preview HTML")
                        }
                    }
                    IconButton(onClick = {
                        if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                        showFind = true
                    }, enabled = !vm.isOpeningFile) { Icon(Icons.Default.FindInPage, stringResource(R.string.editor_find_cd)) }
                    IconButton(onClick = {
                        if (!vm.readOnly && !vm.isOpeningFile) {
                            val next = if (lang == Lang.JSON) JsonTools.format(editorValue.text) else editorValue.text
                            editorValue = TextFieldValue(next, TextRange(next.length))
                            vm.updateContent(next)
                        }
                        vm.saveCurrent()
                    }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.Default.Save, stringResource(R.string.editor_save_cd), tint = Emerald) }
                }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = {
                    if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                    vm.undo()
                }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Undo, null); Text(stringResource(R.string.undo)) }
                TextButton(onClick = {
                    if (editorValue.text != vm.content) vm.updateContent(editorValue.text)
                    vm.redo()
                }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Redo, null); Text(stringResource(R.string.redo)) }
                TextButton(onClick = {
                    if (lang == Lang.JSON) {
                        val next = JsonTools.format(editorValue.text)
                        editorValue = TextFieldValue(next, TextRange(next.length))
                        vm.updateContent(next)
                    } else vm.message = context.getString(R.string.format_json_only)
                }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, null); Text(stringResource(R.string.format)) }
                TextButton(onClick = {
                    if (!vm.isOpeningFile && !vm.readOnly && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                    vm.saveCurrent()
                }, enabled = !vm.isOpeningFile && !vm.readOnly) { Icon(Icons.Default.Save, null); Text(stringResource(R.string.save)) }
                TextButton(onClick = { showFind = true }, enabled = !vm.isOpeningFile) { Icon(Icons.Default.FindInPage, null); Text(stringResource(R.string.find)) }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
            if (vm.isOpeningFile) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.opening_file), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
            if (path == null) {
                EmptyState(Icons.Default.ContentCopy, stringResource(R.string.no_file_open), stringResource(R.string.no_file_hint))
            } else {
                if (vm.openTabs.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Start) {
                        vm.openTabs.keys.toList().forEach { tab ->
                            TextButton(onClick = {
                                if (!vm.readOnly && !vm.isOpeningFile && editorValue.text != vm.content) vm.updateContent(editorValue.text)
                                vm.openFile(tab)
                            }) { Text(tab.substringAfterLast('/'), maxLines = 1) }
                            IconButton(onClick = { vm.closeTab(tab) }) { Icon(Icons.Default.Close, stringResource(R.string.close_tab)) }
                        }
                    }
                }
                if (lang == Lang.JSON && !vm.readOnly && editorValue.text.isNotBlank() &&
                    editorValue.text.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS
                ) {
                    val issue = syntaxIssue
                    val colors = MaterialTheme.colorScheme
                    val hasJsonError = issue?.severity == com.blockline.app.data.IssueSeverity.ERROR
                    val hasJsonWarning = issue?.severity == com.blockline.app.data.IssueSeverity.WARNING
                    val statusText = when {
                        checkingJson -> stringResource(R.string.json_validation_checking)
                        hasJsonError -> stringResource(R.string.json_validation_invalid, issue?.line ?: 1, issue?.column ?: 1, issue?.message.orEmpty())
                        hasJsonWarning -> stringResource(R.string.json_validation_warning, issue?.line ?: 1, issue?.column ?: 1, issue?.message.orEmpty())
                        else -> stringResource(R.string.json_validation_valid)
                    }
                    Surface(
                        color = when {
                            checkingJson -> colors.surfaceVariant
                            hasJsonError -> colors.errorContainer
                            else -> colors.secondaryContainer
                        },
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            statusText,
                            color = when {
                                checkingJson -> colors.onSurfaceVariant
                                hasJsonError -> colors.onErrorContainer
                                else -> colors.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (!vm.readOnly && lang != null && editorValue.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS) {
                    Text(
                        stringResource(R.string.editor_live_analysis_paused),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (vm.readOnly) {
                    Text(
                        stringResource(R.string.large_file_readonly),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.large_file_page, pageIndex + 1, largeOffsets.size, pageStart + 1, pageEnd, vm.content.length),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { largeFilePage = (pageIndex - 1).coerceAtLeast(0) },
                            enabled = pageIndex > 0
                        ) { Text(stringResource(R.string.large_file_previous)) }
                        TextButton(
                            onClick = { largeFilePage = (pageIndex + 1).coerceAtMost(largeOffsets.lastIndex) },
                            enabled = pageIndex < largeOffsets.lastIndex
                        ) { Text(stringResource(R.string.large_file_next)) }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            pageText,
                            style = TextStyle(
                                color = OnSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = vm.fontSize.sp,
                                lineHeight = (vm.fontSize + 5).sp
                            )
                        )
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        BasicTextField(
                            value = editorValue,
                            onValueChange = {
                                editorValue = it
                                if (it.text.length > LIVE_TEXT_ANALYSIS_MAX_CHARS) highlightSource = ""
                            },
                            readOnly = vm.isOpeningFile,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(color = OnSurface, fontFamily = FontFamily.Monospace, fontSize = vm.fontSize.sp, lineHeight = (vm.fontSize + 5).sp),
                            visualTransformation = if (lang != null && highlightSource == editorValue.text && editorValue.text.length <= LIVE_TEXT_ANALYSIS_MAX_CHARS) HighlightTransformation(lang, Palette(
                                Color(0xFF8FC9FF),
                                Color(0xFFB5E7A0),
                                Color(0xFFE6B35A),
                                Emerald,
                                Color(0xFF82958A),
                                if (syntaxIssue?.severity == com.blockline.app.data.IssueSeverity.WARNING) Color(0x55E6B35A) else Color(0x55F44336)
                            ), if (lang == Lang.JSON) jsonErrorOffset else null) else VisualTransformation.None
                        )
                        DropdownMenu(
                            expanded = completionItems.isNotEmpty(),
                            onDismissRequest = { completionItems = emptyList() }
                        ) {
                            completionItems.take(12).forEach { suggestion ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(suggestion.label, style = MaterialTheme.typography.bodyMedium)
                                            if (suggestion.detail.isNotBlank()) {
                                                Text(suggestion.detail, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = {
                                        val start = completionRange.first.coerceIn(0, editorValue.text.length)
                                        val end = completionRange.second.coerceIn(start, editorValue.text.length)
                                        val updated = editorValue.text.replaceRange(start, end, suggestion.insertText)
                                        val cursor = start + suggestion.insertText.length
                                        editorValue = TextFieldValue(updated, TextRange(cursor))
                                        completionItems = emptyList()
                                        vm.updateContent(updated)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showHtmlPreview) AlertDialog(
        onDismissRequest = { showHtmlPreview = false },
        title = { Text("Xem trước HTML") },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 560.dp),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                },
                update = { webView ->
                    val html = editorValue.text
                    if (webView.tag != html) {
                        webView.tag = html
                        webView.loadDataWithBaseURL(
                            "https://blockline.local/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { showHtmlPreview = false }) { Text(stringResource(R.string.close)) }
        }
    )
    if (showFind) AlertDialog(
        onDismissRequest = { showFind = false },
        title = { Text(stringResource(R.string.find_in_file)) },
        text = { Column {
            OutlinedTextField(value = findText, onValueChange = { findText = it }, label = { Text(stringResource(R.string.text_to_find)) }, singleLine = true)
            Text(if (findText.isBlank()) stringResource(R.string.enter_text_to_count) else stringResource(R.string.matches_count, matchCount))
        } },
        confirmButton = {
            TextButton(onClick = {
                val query = findText
                if (query.isNotBlank()) {
                    val source = vm.content
                    val startAt = if (vm.readOnly) pageStart.coerceIn(0, source.length) else editorValue.selection.end.coerceIn(0, source.length)
                    val found = findWrappedMatch(source, query, startAt)
                    if (found >= 0) {
                        if (vm.readOnly) {
                            largeFilePage = largeOffsets.indexOfLast { it <= found }.coerceAtLeast(0)
                        } else {
                            editorValue = editorValue.copy(selection = TextRange(found, found + query.length))
                        }
                        vm.message = context.getString(R.string.matches_count, 1)
                    } else {
                        vm.message = context.getString(R.string.find_not_found)
                    }
                }
                showFind = false
            }) { Text(stringResource(R.string.find)) }
        },
        dismissButton = { TextButton(onClick = { showFind = false }) { Text(stringResource(R.string.close)) } }
    )
}
