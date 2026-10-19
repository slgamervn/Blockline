package com.blockline.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
        val snack = remember { SnackbarHostState() }
        val msg = vm.message
        LaunchedEffect(msg) { if (msg != null) { snack.showSnackbar(msg); vm.message = null } }
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
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
