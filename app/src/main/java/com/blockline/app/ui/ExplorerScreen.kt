package com.blockline.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blockline.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(vm: AppViewModel, onOpenEditor: () -> Unit) {
    val files = listOf("manifest.json", "entities/sample_mob.json", "scripts/main.js", "functions/hello.mcfunction")
    Scaffold(topBar = { TopAppBar(title = { Text("Explorer") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(files) { f ->
                ListItem(headlineContent = { Text(f) })
            }
        }
    }
}
