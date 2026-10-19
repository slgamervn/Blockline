package com.blockline.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blockline.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: AppViewModel, onOpen: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Projects") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Blockline Phase 1")
            Button(onClick = onOpen) { Text("Open sample") }
        }
    }
}

@Composable
fun OverviewScreen(vm: AppViewModel, onBack: () -> Unit, onExplorer: () -> Unit) {
    Text("Overview")
}

@Composable
fun SearchScreen() { Text("Search") }

@Composable
fun ToolsScreen() { Text("Tools") }

@Composable
fun MoreScreen(vm: AppViewModel) { Text("More") }
