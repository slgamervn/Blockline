package com.blockline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import com.blockline.app.AppViewModel
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
import com.blockline.app.ui.theme.ThemeMode
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: AppViewModel, onOpen: () -> Unit) {
    val animationsEnabled = rememberSystemAnimationsEnabled()
    var actionProject by remember { mutableStateOf<com.blockline.app.data.Project?>(null) }
    var renameProject by remember { mutableStateOf<com.blockline.app.data.Project?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::importPack) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
        IconButton(onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) }) { Icon(Icons.Default.Add, stringResource(R.string.import_pack_cd)) }
    }) }, floatingActionButton = {
        FloatingActionButton(onClick = { vm.createSample(); onOpen() }) { Icon(Icons.Default.Add, stringResource(R.string.create_sample_cd)) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(stringResource(R.string.nav_projects), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.projects_subtitle)) }
                Button(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.import_pack)) }
            }
            if (animationsEnabled) {
                AnimatedVisibility(
                    visible = vm.isLoading,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(100))
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            } else if (vm.isLoading) {
                Text(
                    stringResource(R.string.loading),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
            if (vm.projects.isEmpty()) EmptyState(Icons.Default.FolderOpen, stringResource(R.string.no_projects), stringResource(R.string.no_projects_hint), Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f)) {
                items(vm.projects, key = { it.id }) { p ->
                    var itemVisible by remember(p.id) { mutableStateOf(false) }
                    LaunchedEffect(p.id) { itemVisible = true }
                    AnimatedVisibility(
                        visible = itemVisible,
                        enter = if (animationsEnabled) {
                            fadeIn(animationSpec = tween(150)) +
                                slideInVertically(animationSpec = tween(150)) { height -> height / 28 }
                        } else EnterTransition.None,
                        exit = ExitTransition.None
                    ) {
                        var menu by remember(p.id) { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text(if (p.id == vm.current?.id) stringResource(R.string.current_open) else stringResource(R.string.last_opened_date, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.lastOpened)))) },
                                leadingContent = { Icon(Icons.Default.Archive, null) },
                                trailingContent = {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.project_actions)) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { vm.selectProject(p); menu = false; onOpen() })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { renameProject = p; renameValue = p.name; menu = false })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { vm.duplicateProject(p); menu = false })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { actionProject = p; menu = false })
                                    }
                                },
                                modifier = Modifier.clickable { vm.selectProject(p); onOpen() }
                            )
                        }
                    }
                }
            }
        }
    }
    renameProject?.let { p ->
        AlertDialog(onDismissRequest = { renameProject = null }, title = { Text(stringResource(R.string.rename_project_title)) },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, label = { Text(stringResource(R.string.project_name)) }) },
            confirmButton = { TextButton(onClick = { vm.renameProject(p, renameValue); renameProject = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameProject = null }) { Text(stringResource(R.string.cancel)) } })
    }
    actionProject?.let { p ->
        AlertDialog(onDismissRequest = { actionProject = null }, title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_confirm, p.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p); actionProject = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { actionProject = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
