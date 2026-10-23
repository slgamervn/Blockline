package com.blockline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blockline.app.ui.theme.BlocklineTheme
import com.blockline.app.ui.theme.EditorTokens
import com.blockline.app.ui.theme.ThemeMode
import com.blockline.app.ui.theme.editorTokens

private val previewFiles = listOf(
    "manifest.json",
    "entities/zombie.json",
    "scripts/main.js",
    "functions/tick.mcfunction",
    "textures/ui/icon.png",
    "texts/en_US.lang"
)

@Composable
private fun EditorChromePreview(mode: ThemeMode) {
    BlocklineTheme(mode) {
        val tokens = editorTokens(mode, systemDark = true)
        Column(
            Modifier.fillMaxWidth().height(560.dp).background(tokens.editorBackground)
        ) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                EditorActivityBar(
                    selected = "explorer",
                    onExplorer = {},
                    onSearch = {},
                    onSettings = {},
                    tokens = tokens
                )
                EditorSidebar(
                    title = "Explorer",
                    items = previewFiles,
                    selectedPath = "manifest.json",
                    tokens = tokens,
                    onSelect = {}
                )
                Column(Modifier.weight(1f).fillMaxSize().padding(8.dp)) {
                    Text("manifest.json", color = tokens.foreground, style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)
                            .background(tokens.sidebarBackground),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("manifest.json  ●", Modifier.background(tokens.tabSelected).padding(8.dp), color = tokens.foreground)
                        Text("main.js", Modifier.background(tokens.tabInactive).padding(8.dp), color = tokens.muted)
                    }
                    Text(
                        highlight(
                            """{
  "format_version": 2,
  "header": {
    "name": "§aBlockline §bPack",
    "description": "Bedrock add-on",
    "version": [1, 0, 0]
  }
}""",
                            Lang.JSON,
                            previewPalette(tokens)
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = tokens.foreground
                    )
                }
            }
            EditorShortcutBar(
                enabled = true,
                onInsert = {},
                onUndo = {},
                onRedo = {},
                onFormat = {},
                onSave = {},
                onFind = {},
                onHideKeyboard = {},
                tokens = tokens
            )
            EditorStatusBar(8, 3, "JSON", saved = false, tokens = tokens)
        }
    }
}

private fun previewPalette(tokens: EditorTokens) = Palette(
    key = tokens.tokenKey,
    string = tokens.tokenString,
    number = tokens.tokenNumber,
    keyword = tokens.tokenKeyword,
    comment = tokens.tokenComment,
    errorBackground = tokens.errorBackground,
    boolean = tokens.tokenKeyword,
    function = tokens.tokenFunction,
    property = tokens.tokenKey,
    identifier = tokens.foreground
)

@Preview(name = "Editor Chrome — Dark+", showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun EditorChromeDarkPlusPreview() {
    EditorChromePreview(ThemeMode.DARK_PLUS)
}

@Preview(name = "Editor Chrome — Light+", showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun EditorChromeLightPlusPreview() {
    EditorChromePreview(ThemeMode.LIGHT_PLUS)
}

@Preview(name = "Editor Chrome — AMOLED", showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun EditorChromeAmoledPreview() {
    EditorChromePreview(ThemeMode.AMOLED)
}

@Preview(name = "Command Palette — Dark+", showBackground = true)
@Composable
private fun CommandPaletteDarkPlusPreview() {
    BlocklineTheme(ThemeMode.DARK_PLUS) {
        EditorCommandPalette(
            query = "manifest",
            onQueryChange = {},
            candidates = listOf("Go to File", "Find", "Format Document", "Save", "File: manifest.json", "File: scripts/main.js"),
            onSelect = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Command Palette — Light+", showBackground = true)
@Composable
private fun CommandPaletteLightPlusPreview() {
    BlocklineTheme(ThemeMode.LIGHT_PLUS) {
        EditorCommandPalette(
            query = "manifest",
            onQueryChange = {},
            candidates = listOf("Go to File", "Find", "Format Document", "Save", "File: manifest.json", "File: scripts/main.js"),
            onSelect = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Command Palette — AMOLED", showBackground = true)
@Composable
private fun CommandPaletteAmoledPreview() {
    BlocklineTheme(ThemeMode.AMOLED) {
        EditorCommandPalette(
            query = "manifest",
            onQueryChange = {},
            candidates = listOf("Go to File", "Find", "Format Document", "Save", "File: manifest.json", "File: scripts/main.js"),
            onSelect = {},
            onDismiss = {}
        )
    }
}
