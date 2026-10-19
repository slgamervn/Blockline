package com.blockline.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockline.app.AppViewModel
import com.blockline.app.ui.theme.Emerald
import com.blockline.app.ui.theme.OnSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onBack: () -> Unit) {
    var content by remember { mutableStateOf("{\n  \"format_version\": 2,\n  \"header\": {\n    \"name\": \"Blockline Sample\"\n  }\n}") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Save, null, tint = Emerald)
                    }
                }
            )
        }
    ) { pad ->
        BasicTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            textStyle = TextStyle(color = OnSurface, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        )
    }
}
