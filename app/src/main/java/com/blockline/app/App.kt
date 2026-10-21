package com.blockline.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blockline.app.ui.EditorScreen
import com.blockline.app.ui.ExplorerScreen
import com.blockline.app.ui.MoreScreen
import com.blockline.app.ui.OverviewScreen
import com.blockline.app.ui.ProjectsScreen
import com.blockline.app.ui.SearchScreen
import com.blockline.app.ui.ToolsScreen
import com.blockline.app.ui.theme.BlocklineTheme

private class TabDef(val route: String, val label: Int, val icon: ImageVector)
private val TABS = listOf(
    TabDef("projects", R.string.nav_projects, Icons.Default.Dashboard),
    TabDef("explorer", R.string.nav_explorer, Icons.Default.FolderOpen),
    TabDef("search", R.string.nav_search, Icons.Default.Search),
    TabDef("tools", R.string.nav_tools, Icons.Default.Build),
    TabDef("more", R.string.nav_more, Icons.Default.MoreHoriz)
)
private fun goTab(nav: NavController, route: String) {
    nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
}

@Composable
fun BlocklineRoot(vm: AppViewModel = viewModel()) {
    BlocklineTheme(vm.themeMode) {
        val nav = rememberNavController()
        val context = LocalContext.current
        val snack = remember { SnackbarHostState() }
        val msg = vm.message
        val update = vm.updateInfo
        LaunchedEffect(msg) { if (msg != null) { snack.showSnackbar(msg); vm.message = null } }
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        if (update != null) {
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate(update.version) },
                title = { Text(stringResource(R.string.update_dialog_title)) },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.update_dialog_versions, BuildConfig.VERSION_NAME, update.version))
                        Text(update.releaseName, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        if (update.releaseNotes.isNotBlank()) {
                            Text(update.releaseNotes.take(2_400), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(stringResource(R.string.update_no_notes))
                        }
                        if (update.apkSizeBytes > 0L) Text(stringResource(R.string.update_download_size, String.format(java.util.Locale.ROOT, "%.1f", update.apkSizeBytes / 1_048_576.0)))
                    }
                },
                confirmButton = {
                    Button(onClick = { vm.openUpdateDownload(context) }) { Text(stringResource(R.string.update_now)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissUpdate(update.version) }) { Text(stringResource(R.string.update_later)) }
                }
            )
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (route != "editor") NavigationBar {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route || (t.route == "overview" && t.route == route),
                            onClick = { goTab(nav, t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = "projects", modifier = Modifier.padding(pad)) {
                composable("projects") { ProjectsScreen(vm) { nav.navigate("overview") } }
                composable("overview") { OverviewScreen(vm, onBack = { nav.popBackStack() }, onExplorer = { goTab(nav, "explorer") }) }
                composable("explorer") { ExplorerScreen(vm) { nav.navigate("editor") } }
                composable("search") { SearchScreen(vm) { nav.navigate("editor") } }
                composable("tools") { ToolsScreen(vm) { nav.navigate("editor") } }
                composable("more") { MoreScreen(vm) }
                composable("editor") { EditorScreen(vm) { nav.popBackStack() } }
            }
        }
    }
}
