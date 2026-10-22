package com.blockline.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blockline.app.R
import com.blockline.app.data.BrarchiveCodec
import com.blockline.app.data.BrarchiveEntry
import com.blockline.app.data.IssueSeverity
import com.blockline.app.data.ProjectValidator
import com.blockline.app.data.UnicodeEscapeTools
import com.blockline.app.data.VanillaResourceIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun VanillaReferenceCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheFile = remember { VanillaResourceIndex.cacheFile(context.filesDir) }
    var pathCount by remember { mutableStateOf(VanillaResourceIndex.read(cacheFile).size) }
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vanilla_reference_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.vanilla_reference_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Text(
                if (pathCount > 0) stringResource(R.string.vanilla_reference_cached, pathCount)
                else stringResource(R.string.vanilla_reference_none),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            status = null
                            val result = runCatching {
                                withContext(Dispatchers.IO) { VanillaResourceIndex.refresh(cacheFile) }
                            }
                            result.onSuccess {
                                pathCount = it
                                status = context.getString(R.string.vanilla_reference_ready, it)
                            }.onFailure {
                                pathCount = VanillaResourceIndex.read(cacheFile).size
                                status = context.getString(R.string.vanilla_reference_failed, it.message ?: "Network error")
                            }
                            refreshing = false
                        }
                    },
                    enabled = !refreshing
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Text(stringResource(R.string.vanilla_reference_refresh))
                }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bedrock.dev/packs")))
                    }.onFailure {
                        status = it.message ?: "Unable to open source page."
                    }
                }) { Text(stringResource(R.string.vanilla_reference_open_source)) }
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun UnicodeEscapeToolCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<String?>(null) }

    val openText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open selected file.")
                    val bytes = stream.use { BrarchiveCodec.readBounded(it, 8L * 1024L * 1024L) }
                    String(bytes, Charsets.UTF_8)
                }
            }
            result.onSuccess {
                input = it
                status = context.getString(R.string.unicode_tool_file_read, uri.lastPathSegment.orEmpty(), it.toByteArray(Charsets.UTF_8).size)
            }.onFailure {
                status = context.getString(R.string.unicode_tool_failed, it.message ?: "Read failed")
            }
            busy = false
        }
    }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val value = pendingSave
        pendingSave = null
        if (uri == null) return@rememberLauncherForActivityResult
        if (value != null) scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Unable to open output file.")
                    stream.use { it.write(value.toByteArray(Charsets.UTF_8)) }
                }
            }
            status = if (result.isSuccess) context.getString(R.string.unicode_tool_file_saved)
            else context.getString(R.string.unicode_tool_failed, result.exceptionOrNull()?.message ?: "Save failed")
            busy = false
        }
    }

    fun transform(operation: String) {
        if (input.isBlank()) {
            status = context.getString(R.string.unicode_tool_empty)
            return
        }
        val snapshot = input
        scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    when (operation) {
                        "decode" -> UnicodeEscapeTools.decode(snapshot)
                        "encode" -> UnicodeEscapeTools.encode(snapshot)
                        "autofix" -> UnicodeEscapeTools.autoFixJson(snapshot)
                        "format" -> UnicodeEscapeTools.formatJson(snapshot)
                        "minify" -> UnicodeEscapeTools.minifyJson(snapshot)
                        else -> snapshot
                    }
                }
            }
            result.onSuccess {
                output = it
                status = context.getString(R.string.unicode_tool_result_ready)
            }.onFailure {
                status = context.getString(R.string.unicode_tool_failed, it.message ?: "Operation failed")
            }
            busy = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.unicode_tool_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.unicode_tool_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.unicode_tool_input)) },
                minLines = 4,
                maxLines = 8,
                readOnly = busy,
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { openText.launch(arrayOf("text/plain", "application/json", "*/*")) }, enabled = !busy) {
                    Icon(Icons.Default.FolderOpen, null)
                    Text(stringResource(R.string.unicode_tool_open_file))
                }
                TextButton(onClick = { input = ""; output = ""; status = null }, enabled = !busy) { Text(stringResource(R.string.unicode_tool_clear)) }
                TextButton(onClick = { val old = input; input = output; output = old; status = null }, enabled = !busy) { Text(stringResource(R.string.unicode_tool_swap)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { transform("decode") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.unicode_tool_decode)) }
                Button(onClick = { transform("encode") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.unicode_tool_encode)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { transform("autofix") }, enabled = !busy) { Text(stringResource(R.string.unicode_tool_autofix)) }
                TextButton(onClick = { transform("format") }, enabled = !busy) { Text(stringResource(R.string.unicode_tool_format)) }
                TextButton(onClick = { transform("minify") }, enabled = !busy) { Text(stringResource(R.string.unicode_tool_minify)) }
                TextButton(onClick = {
                    val source = input
                    scope.launch {
                        status = withContext(Dispatchers.Default) {
                            val issue = ProjectValidator.inspectJsonSyntax(source)
                            when {
                                source.isBlank() -> context.getString(R.string.unicode_tool_empty)
                                issue == null -> context.getString(R.string.unicode_tool_json_valid)
                                issue.severity == IssueSeverity.WARNING -> context.getString(R.string.unicode_tool_json_warning, issue.line ?: 1, issue.column ?: 1, localizeValidationMessage(context, issue.message))
                                else -> context.getString(R.string.unicode_tool_json_error, issue.line ?: 1, issue.column ?: 1, localizeValidationMessage(context, issue.message))
                            }
                        }
                    }
                }, enabled = !busy) { Text(stringResource(R.string.brarchive_check_json)) }
            }
            OutlinedTextField(
                value = output,
                onValueChange = { output = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.unicode_tool_output)) },
                minLines = 4,
                maxLines = 8,
                readOnly = busy,
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    if (output.isBlank()) status = context.getString(R.string.unicode_tool_empty)
                    else {
                        clipboard.setText(AnnotatedString(output))
                        status = context.getString(R.string.copied)
                    }
                }, enabled = !busy) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text(stringResource(R.string.unicode_tool_copy))
                }
                TextButton(onClick = {
                    if (output.isBlank()) status = context.getString(R.string.unicode_tool_empty)
                    else {
                        pendingSave = output
                        saveText.launch("result.json")
                    }
                }, enabled = !busy) {
                    Icon(Icons.Default.Save, null)
                    Text(stringResource(R.string.unicode_tool_save))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun BrarchiveToolCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BrarchiveEntry>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var drafts by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var selectedText by remember { mutableStateOf("") }
    var selectedIssue by remember { mutableStateOf<com.blockline.app.data.ValidationIssue?>(null) }
    var archiveName by remember { mutableStateOf("output.brarchive") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }

    val openArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open the selected archive.")
                    val bytes = stream.use { BrarchiveCodec.readBounded(it) }
                    BrarchiveCodec.decode(bytes)
                }
            }
            result.onSuccess { loaded ->
                entries = loaded
                drafts = emptyMap()
                selectedIndex = loaded.indexOfFirst { it.isUtf8Text }.takeIf { it >= 0 }
                    ?: loaded.indices.firstOrNull()
                selectedText = selectedIndex?.let { BrarchiveCodec.textContent(loaded[it]) }.orEmpty()
                selectedIssue = null
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                archiveName = name?.takeIf { it.endsWith(".brarchive", true) } ?: "output.brarchive"
                val duplicates = loaded.groupBy { it.name }.values.count { it.size > 1 }
                status = context.getString(R.string.brarchive_loaded, loaded.size, duplicates)
            }.onFailure {
                status = context.getString(R.string.brarchive_failed, it.message ?: "Invalid archive")
            }
            busy = false
        }
    }
    val saveArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri == null) {
            status = context.getString(R.string.brarchive_cancelled)
            return@rememberLauncherForActivityResult
        }
        if (payload != null) scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Unable to open output file.")
                    stream.use { it.write(payload.first) }
                }
            }
            status = if (result.isSuccess) context.getString(R.string.brarchive_saved)
            else context.getString(R.string.brarchive_failed, result.exceptionOrNull()?.message ?: "Save failed")
            busy = false
        }
    }

    fun updateSelectedText(value: String) {
        selectedText = value
        val index = selectedIndex ?: return
        drafts = drafts + (index to value)
        selectedIssue = null
    }

    fun snapshotEntries(): List<BrarchiveEntry> = entries.mapIndexed { index, entry ->
        val edited = drafts[index]
        if (edited == null) entry else BrarchiveCodec.createEntry(entry.name, edited.toByteArray(Charsets.UTF_8))
    }

    fun exportArchive(asZip: Boolean) {
        if (entries.isEmpty()) {
            status = context.getString(R.string.brarchive_no_entries)
            return
        }
        scope.launch {
            busy = true
            status = context.getString(R.string.brarchive_export_ready)
            val snapshot = snapshotEntries()
            val base = archiveName.substringBeforeLast('.', archiveName).ifBlank { "output" }
            val filename = if (asZip) base + ".zip" else base + ".brarchive"
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    if (asZip) BrarchiveCodec.encodeZip(snapshot) else BrarchiveCodec.encode(snapshot)
                }
            }
            result.onSuccess {
                pendingExport = it to filename
                busy = false
                saveArchive.launch(filename)
            }.onFailure {
                status = context.getString(R.string.brarchive_failed, it.message ?: "Export failed")
                busy = false
            }
        }
    }

    fun transformSelected(operation: String) {
        val current = selectedIndex?.let(entries::getOrNull) ?: return
        if (!current.isUtf8Text || current.content.size > 2 * 1024 * 1024) return
        val snapshot = selectedText
        scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    when (operation) {
                        "autofix" -> UnicodeEscapeTools.autoFixJson(snapshot)
                        "format" -> UnicodeEscapeTools.formatJson(snapshot)
                        "minify" -> UnicodeEscapeTools.minifyJson(snapshot)
                        "unicode" -> {
                            if (Regex("""\\u[0-9a-fA-F]{4}""").containsMatchIn(snapshot)) UnicodeEscapeTools.decode(snapshot)
                            else UnicodeEscapeTools.encode(snapshot)
                        }
                        else -> snapshot
                    }
                }
            }
            result.onSuccess {
                updateSelectedText(it)
                status = context.getString(R.string.unicode_tool_result_ready)
            }.onFailure {
                status = context.getString(R.string.brarchive_failed, it.message ?: "Operation failed")
            }
            busy = false
        }
    }

    fun validateSelectedJson() {
        val snapshot = selectedText
        if (snapshot.isBlank()) {
            selectedIssue = null
            status = context.getString(R.string.unicode_tool_empty)
            return
        }
        scope.launch {
            val issue = withContext(Dispatchers.Default) { ProjectValidator.inspectJsonSyntax(snapshot) }
            if (snapshot != selectedText) return@launch
            selectedIssue = issue
            status = when {
                issue == null -> context.getString(R.string.brarchive_json_valid)
                issue.severity == IssueSeverity.WARNING -> context.getString(R.string.brarchive_json_warning, issue.line ?: 1, issue.column ?: 1, localizeValidationMessage(context, issue.message))
                else -> context.getString(R.string.brarchive_json_error, issue.line ?: 1, issue.column ?: 1, localizeValidationMessage(context, issue.message))
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.brarchive_tool_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.brarchive_tool_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Button(onClick = { openArchive.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy) {
                Icon(Icons.Default.FolderOpen, null)
                Text(stringResource(R.string.brarchive_open))
            }
            if (entries.isEmpty()) {
                Text(stringResource(R.string.brarchive_no_entries), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.brarchive_entries, entries.size), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    itemsIndexed(entries, key = { index, entry -> index.toString() + ":" + entry.name }) { index, entry ->
                        val selected = selectedIndex == index
                        TextButton(
                            onClick = {
                                selectedIndex = index
                                selectedText = drafts[index] ?: BrarchiveCodec.textContent(entry).orEmpty()
                                selectedIssue = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(entry.name.ifBlank { "(empty filename)" }, maxLines = 1)
                                Text(
                                    entry.content.size.toString() + " B · " + when {
                                        entry.isMcbBinary -> stringResource(R.string.brarchive_entry_mcb)
                                        entry.isUtf8Text -> stringResource(R.string.brarchive_entry_text)
                                        else -> stringResource(R.string.brarchive_entry_binary)
                                    },
                                    color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                val current = selectedIndex?.let(entries::getOrNull)
                if (current != null) {
                    androidx.compose.material3.HorizontalDivider()
                    Text(current.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    if (!current.isUtf8Text) {
                        Text(stringResource(R.string.brarchive_binary_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else if (current.content.size > 2 * 1024 * 1024) {
                        Text(stringResource(R.string.brarchive_large_entry_readonly), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = selectedText,
                            onValueChange = ::updateSelectedText,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.brarchive_entry_text)) },
                            minLines = 4,
                            maxLines = 8,
                            readOnly = busy,
                            textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = ::validateSelectedJson, enabled = !busy) { Text(stringResource(R.string.brarchive_check_json)) }
                            TextButton(onClick = { transformSelected("autofix") }, enabled = !busy) { Text(stringResource(R.string.brarchive_autofix_json)) }
                            TextButton(onClick = { transformSelected("format") }, enabled = !busy) { Text(stringResource(R.string.brarchive_format_json)) }
                            TextButton(onClick = { transformSelected("minify") }, enabled = !busy) { Text(stringResource(R.string.brarchive_minify_json)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { transformSelected("unicode") }, enabled = !busy) { Text(stringResource(R.string.brarchive_unicode_toggle)) }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(selectedText))
                                status = context.getString(R.string.copied)
                            }, enabled = !busy) {
                                Icon(Icons.Default.ContentCopy, null)
                                Text(stringResource(R.string.unicode_tool_copy))
                            }
                        }
                    }
                    selectedIssue?.let { issue ->
                        Text(
                            when (issue.severity) {
                                IssueSeverity.ERROR -> context.getString(R.string.brarchive_json_error, issue.line ?: 1, issue.column ?: 1, localizeValidationMessage(context, issue.message))
                                IssueSeverity.WARNING -> context.getString(R.string.brarchive_json_warning, issue.line ?: 1, issue.column ?: 1, localizeValidationMessage(context, issue.message))
                                IssueSeverity.INFO -> localizeValidationMessage(context, issue.message)
                            },
                            color = if (issue.severity == IssueSeverity.ERROR) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(stringResource(R.string.brarchive_duplicate_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.brarchive_output_name)) },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportArchive(false) }, enabled = !busy && entries.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.brarchive_export)) }
                    TextButton(onClick = { exportArchive(true) }, enabled = !busy && entries.isNotEmpty()) { Text(stringResource(R.string.brarchive_export_zip)) }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
    }
}

