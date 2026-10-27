package com.blockline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blockline.app.AppViewModel
import com.blockline.app.data.UpdateDownloadState
import com.blockline.app.BuildConfig
import com.blockline.app.R
import com.blockline.app.data.ExportMode
import com.blockline.app.data.SearchFileFilter
import com.blockline.app.data.GeneratedPackType
import com.blockline.app.data.PackManifestGenerator
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
import com.blockline.app.ui.theme.AccentColor
import com.blockline.app.ui.theme.ThemeMode
import java.io.OutputStream

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun MoreScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(vm.current?.id) { vm.refreshOperationLog() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.nav_more), style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.more_subtitle), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.updates_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (BuildConfig.UPDATE_CHANNEL == "test") R.string.updates_hint_test else R.string.updates_hint),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_check_updates), style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.auto_check_updates_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(
                        checked = vm.autoCheckUpdates,
                        onCheckedChange = { vm.updateAutoCheckUpdates(it) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Button(onClick = { vm.checkForUpdates(showUpToDateMessage = true) }, enabled = !vm.isCheckingUpdates) {
                        if (vm.isCheckingUpdates) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.check_updates))
                    }
                    TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.blockline.app.data.AppUpdateRepository.RELEASES_URL))) } }) {
                        Text(stringResource(R.string.view_releases))
                    }
                }
                Text(stringResource(R.string.update_download_prompt), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                vm.updateCheckResult?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.current_version_value, BuildConfig.VERSION_NAME), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                vm.lastUpdateCheck?.let {
                    Text(stringResource(R.string.last_update_check, it.replace('T', ' ').substringBefore('.').removeSuffix("Z")), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
                vm.updateInfo?.let { info ->
                    androidx.compose.material3.Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.update_available_status, info.version), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                            if (info.releaseNotes.isNotBlank()) Text(info.releaseNotes.take(1_000), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.update_download_size, String.format(java.util.Locale.ROOT, "%.1f MB", info.apkSizeBytes / 1_048_576.0)), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when (vm.updateDownloadState) {
                                    UpdateDownloadState.IDLE -> stringResource(R.string.update_state_idle)
                                    UpdateDownloadState.DOWNLOADING -> stringResource(R.string.update_state_downloading, vm.updateDownloadProgress)
                                    UpdateDownloadState.VERIFYING -> stringResource(R.string.update_state_verifying)
                                    UpdateDownloadState.READY_TO_INSTALL -> stringResource(R.string.update_state_ready)
                                    UpdateDownloadState.WAITING_PERMISSION -> stringResource(R.string.update_state_waiting_permission)
                                    UpdateDownloadState.INSTALLING -> stringResource(R.string.update_state_installing)
                                    UpdateDownloadState.FAILED -> stringResource(R.string.update_state_failed)
                                    UpdateDownloadState.CANCELLED -> stringResource(R.string.update_state_cancelled)
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = if (vm.updateDownloadState == UpdateDownloadState.FAILED) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (vm.updateDownloadState == UpdateDownloadState.DOWNLOADING || vm.updateDownloadState == UpdateDownloadState.VERIFYING) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { if (vm.updateDownloadState == UpdateDownloadState.VERIFYING) 1f else vm.updateDownloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            vm.updateDownloadError?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                            when (vm.updateDownloadState) {
                                UpdateDownloadState.DOWNLOADING -> TextButton(onClick = { vm.cancelUpdateDownload() }) { Text(stringResource(R.string.update_cancel_download)) }
                                UpdateDownloadState.READY_TO_INSTALL, UpdateDownloadState.WAITING_PERMISSION -> Button(onClick = { vm.installDownloadedUpdate(context) }) {
                                    Text(stringResource(if (vm.updateDownloadState == UpdateDownloadState.WAITING_PERMISSION) R.string.update_install_after_permission else R.string.update_install_downloaded))
                                }
                                UpdateDownloadState.VERIFYING, UpdateDownloadState.INSTALLING -> Button(onClick = {}, enabled = false) { Text(stringResource(R.string.update_state_verifying)) }
                                else -> Button(onClick = { vm.downloadUpdate() }) {
                                    Text(stringResource(if (vm.updateDownloadState == UpdateDownloadState.FAILED || vm.updateDownloadState == UpdateDownloadState.CANCELLED) R.string.update_retry_download else R.string.update_download_action))
                                }
                            }
                        }
                    }
                }

            }
        }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.latest_changelog), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    vm.latestReleaseInfo?.let { info ->
                        androidx.compose.material3.AssistChip(
                            onClick = { vm.openReleasePage(context, info) },
                            label = { Text(info.version) }
                        )
                    }
                }
                val changelog = vm.latestReleaseInfo
                if (changelog == null) {
                    Text(stringResource(R.string.release_notes_empty), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                } else {
                    Text(changelog.releaseName, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    if (changelog.releaseNotes.isNotBlank()) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                changelog.releaseNotes.take(12_000),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Text(stringResource(R.string.update_no_notes), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.openReleasePage(context, changelog) }) {
                        Text(stringResource(R.string.view_release_notes))
                    }
                    if (vm.updateInfo == null && runCatching {
                            com.blockline.app.data.AppUpdateRepository.compareVersions(changelog.version, BuildConfig.VERSION_NAME) > 0
                        }.getOrDefault(false)) {
                        Text(stringResource(R.string.update_download_prompt), style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.update_download_size, String.format(java.util.Locale.ROOT, "%.1f MB", changelog.apkSizeBytes / 1_048_576.0)),
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        when (vm.updateDownloadState) {
                            UpdateDownloadState.DOWNLOADING -> {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { vm.updateDownloadProgress / 100f }, modifier = Modifier.fillMaxWidth()
                                )
                                Text(stringResource(R.string.update_state_downloading, vm.updateDownloadProgress))
                                TextButton(onClick = { vm.cancelUpdateDownload() }) { Text(stringResource(R.string.update_cancel_download)) }
                            }
                            UpdateDownloadState.VERIFYING -> Text(stringResource(R.string.update_state_verifying))
                            UpdateDownloadState.READY_TO_INSTALL, UpdateDownloadState.WAITING_PERMISSION -> Button(onClick = { vm.installDownloadedUpdate(context) }) {
                                Text(stringResource(if (vm.updateDownloadState == UpdateDownloadState.WAITING_PERMISSION) R.string.update_install_after_permission else R.string.update_install_downloaded))
                            }
                            UpdateDownloadState.INSTALLING -> Text(stringResource(R.string.update_state_installing))
                            else -> Button(onClick = { vm.downloadUpdate() }) {
                                Text(stringResource(if (vm.updateDownloadState == UpdateDownloadState.FAILED || vm.updateDownloadState == UpdateDownloadState.CANCELLED) R.string.update_retry_download else R.string.update_download_action))
                            }
                        }
                        vm.updateDownloadError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_language_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.app_language_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = vm.appLanguage == "en",
                        onClick = {
                            vm.changeAppLanguage("en")
                            context.findActivity()?.recreate()
                        },
                        label = { Text(stringResource(R.string.language_english)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = vm.appLanguage == "vi",
                        onClick = {
                            vm.changeAppLanguage("vi")
                            context.findActivity()?.recreate()
                        },
                        label = { Text(stringResource(R.string.language_vietnamese)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = vm.appLanguage == "system",
                        onClick = {
                            vm.changeAppLanguage("system")
                            context.findActivity()?.recreate()
                        },
                        label = { Text(stringResource(R.string.language_system)) }
                    )
                }
            }
        }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Surface(color = com.blockline.app.ui.theme.Emerald.copy(alpha = .16f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Text("◐", Modifier.padding(12.dp), color = com.blockline.app.ui.theme.Emerald, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    }
                    Column {
                        Text(stringResource(R.string.appearance), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.appearance_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = vm.themeMode in setOf(ThemeMode.DARK, ThemeMode.DARK_PLUS),
                        onClick = { vm.themeMode = ThemeMode.DARK_PLUS },
                        label = { Text(stringResource(R.string.theme_dark_plus)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = vm.themeMode in setOf(ThemeMode.LIGHT, ThemeMode.LIGHT_PLUS),
                        onClick = { vm.themeMode = ThemeMode.LIGHT_PLUS },
                        label = { Text(stringResource(R.string.theme_light_plus)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = vm.themeMode == ThemeMode.AMOLED,
                        onClick = { vm.themeMode = ThemeMode.AMOLED },
                        label = { Text(stringResource(R.string.theme_amoled)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = vm.themeMode == ThemeMode.SYSTEM,
                        onClick = { vm.themeMode = ThemeMode.SYSTEM },
                        label = { Text(stringResource(R.string.system)) }
                    )
                }
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.accent_color), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.accent_color_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = vm.accentColor == AccentColor.EMERALD, onClick = { vm.accentColor = AccentColor.EMERALD }, label = { Text(stringResource(R.string.accent_emerald)) })
                    androidx.compose.material3.FilterChip(selected = vm.accentColor == AccentColor.BLUE, onClick = { vm.accentColor = AccentColor.BLUE }, label = { Text(stringResource(R.string.accent_blue)) })
                    androidx.compose.material3.FilterChip(selected = vm.accentColor == AccentColor.VIOLET, onClick = { vm.accentColor = AccentColor.VIOLET }, label = { Text(stringResource(R.string.accent_violet)) })
                    androidx.compose.material3.FilterChip(selected = vm.accentColor == AccentColor.AMBER, onClick = { vm.accentColor = AccentColor.AMBER }, label = { Text(stringResource(R.string.accent_amber)) })
                }
                androidx.compose.material3.OutlinedButton(onClick = { vm.resetAppearanceAndEditor() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reset_appearance_editor))
                }
            }
        }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.chrome_game_effects), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.chrome_game_effects_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.Switch(
                            checked = vm.gameEffectsEnabled,
                            onCheckedChange = { vm.gameEffectsEnabled = it }
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.chrome_click_sounds), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.chrome_click_sounds_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.Switch(
                            checked = vm.clickSoundsEnabled,
                            onCheckedChange = { vm.clickSoundsEnabled = it }
                        )
                    }
                }
            }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Text("Aa", Modifier.padding(10.dp), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text(stringResource(R.string.editor_typography), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.editor_font_size, vm.fontSize), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { vm.fontSize = (vm.fontSize - 1).coerceAtLeast(10) }, enabled = vm.fontSize > 10) { Text(stringResource(R.string.smaller_text)) }
                    Button(onClick = { vm.fontSize = (vm.fontSize + 1).coerceAtMost(24) }, enabled = vm.fontSize < 24) { Text(stringResource(R.string.larger_text)) }
                }
            }
        }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.operation_log_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.operation_log_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { vm.clearOperationLog() },
                        enabled = vm.current != null && vm.operationLog.isNotEmpty()
                    ) { Text(stringResource(R.string.operation_log_clear)) }
                    TextButton(
                        onClick = {
                            val copiedText = vm.operationLog.takeLast(100).joinToString("\n") { entry ->
                                val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(entry.timestamp))
                                "$time · ${entry.action}${if (entry.detail.isBlank()) "" else " — ${entry.detail}"}"
                            }
                            clipboard.setText(AnnotatedString(copiedText))
                            vm.message = context.getString(R.string.operation_log_copied)
                        },
                        enabled = vm.operationLog.isNotEmpty()
                    ) { Text(stringResource(R.string.operation_log_copy)) }
                }
                if (vm.current == null) {
                    Text(stringResource(R.string.operation_log_select_project))
                } else if (vm.operationLog.isEmpty()) {
                    Text(stringResource(R.string.operation_log_empty), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    vm.operationLog.takeLast(20).asReversed().forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.action, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            if (entry.detail.isNotBlank()) Text(entry.detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            Text(
                                android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(entry.timestamp)),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()
                    }
                    if (vm.operationLog.size > 20) {
                        Text(stringResource(R.string.operation_log_more, vm.operationLog.size - 20), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        }
        item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_blockline), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_blurb), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.version), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.version_phase, BuildConfig.VERSION_NAME))
                }
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.privacy), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.privacy_detail), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        item {
        Text(stringResource(R.string.early_notice), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
