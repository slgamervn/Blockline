package com.blockline.app.ui.theme

import androidx.compose.ui.graphics.Color

data class EditorTokens(
    val editorBackground: Color,
    val sidebarBackground: Color,
    val activityBarBackground: Color,
    val tabSelected: Color,
    val tabInactive: Color,
    val divider: Color,
    val selection: Color,
    val currentLine: Color,
    val tokenKey: Color,
    val tokenString: Color,
    val tokenNumber: Color,
    val tokenKeyword: Color,
    val tokenFunction: Color,
    val tokenComment: Color,
    val tokenType: Color,
    val foreground: Color,
    val muted: Color,
    val statusBar: Color,
    val inventoryLight: Color,
    val inventoryDark: Color,
    val gameGreen: Color
)

/** One token source for the editor; UI components must not scatter literal colors. */
fun editorTokens(mode: ThemeMode, systemDark: Boolean = true): EditorTokens {
    val light = when (mode) {
        ThemeMode.LIGHT, ThemeMode.LIGHT_PLUS -> true
        ThemeMode.SYSTEM -> !systemDark
        else -> false
    }
    if (light) return EditorTokens(
        editorBackground = Color(0xFFFFFFFF),
        sidebarBackground = Color(0xFFF3F3F3),
        activityBarBackground = Color(0xFF2C2C2C),
        tabSelected = Color(0xFFFFFFFF),
        tabInactive = Color(0xFFE8E8E8),
        divider = Color(0xFFE5E5E5),
        selection = Color(0xFFADD6FF),
        currentLine = Color(0xFFF5F5F5),
        tokenKey = Color(0xFF0451A5),
        tokenString = Color(0xFFA31515),
        tokenNumber = Color(0xFF098658),
        tokenKeyword = Color(0xFF0000FF),
        tokenFunction = Color(0xFF795E26),
        tokenComment = Color(0xFF008000),
        tokenType = Color(0xFF267F99),
        foreground = Color(0xFF1F1F1F),
        muted = Color(0xFF616161),
        statusBar = Color(0xFF007ACC),
        inventoryLight = Color(0xFFFFFFFF),
        inventoryDark = Color(0xFFB7B7B7),
        gameGreen = Color(0xFF267F45)
    )

    val amoled = mode == ThemeMode.AMOLED
    return EditorTokens(
        editorBackground = if (amoled) Color.Black else Color(0xFF1E1E1E),
        sidebarBackground = if (amoled) Color(0xFF050505) else Color(0xFF252526),
        activityBarBackground = if (amoled) Color.Black else Color(0xFF333333),
        tabSelected = if (amoled) Color.Black else Color(0xFF1E1E1E),
        tabInactive = if (amoled) Color(0xFF101010) else Color(0xFF2D2D2D),
        divider = if (amoled) Color(0xFF222222) else Color(0xFF3C3C3C),
        selection = Color(0xFF264F78),
        currentLine = if (amoled) Color(0xFF101010) else Color(0xFF2A2D2E),
        tokenKey = Color(0xFF9CDCFE),
        tokenString = Color(0xFFCE9178),
        tokenNumber = Color(0xFFB5CEA8),
        tokenKeyword = Color(0xFF569CD6),
        tokenFunction = Color(0xFFDCDCAA),
        tokenComment = Color(0xFF6A9955),
        tokenType = Color(0xFF4EC9B0),
        foreground = Color(0xFFD4D4D4),
        muted = Color(0xFF858585),
        statusBar = Color(0xFF007ACC),
        inventoryLight = Color(0xFF686868),
        inventoryDark = Color(0xFF151515),
        gameGreen = Color(0xFF3DBA7A)
    )
}
