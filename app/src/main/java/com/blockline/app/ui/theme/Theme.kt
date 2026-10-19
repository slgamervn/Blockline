package com.blockline.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { DARK, LIGHT, SYSTEM }

private val Emerald = Color(0xFF3DBA7A)
private val Amber = Color(0xFFE6B35A)

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Color(0xFF04210F),
    primaryContainer = Color(0xFF1C4A32),
    onPrimaryContainer = Color(0xFFBFF0D4),
    secondary = Amber,
    onSecondary = Color(0xFF2A1C00),
    secondaryContainer = Color(0xFF4A3A14),
    onSecondaryContainer = Color(0xFFFFE2A8),
    tertiary = Color(0xFF7FB8E8),
    background = Color(0xFF101612),
    onBackground = Color(0xFFE3EAE5),
    surface = Color(0xFF151D18),
    onSurface = Color(0xFFE3EAE5),
    surfaceVariant = Color(0xFF212B25),
    onSurfaceVariant = Color(0xFFAEBBB3),
    outline = Color(0xFF55655B),
    error = Color(0xFFFF8A80),
    surfaceContainerLowest = Color(0xFF0D120F),
    surfaceContainerLow = Color(0xFF131A16),
    surfaceContainer = Color(0xFF18211C),
    surfaceContainerHigh = Color(0xFF1E2822),
    surfaceContainerHighest = Color(0xFF242F29)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E8A55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFF0D4),
    onPrimaryContainer = Color(0xFF04210F),
    secondary = Color(0xFF8A6200),
    secondaryContainer = Color(0xFFFFE2A8),
    tertiary = Color(0xFF2B6C9E),
    background = Color(0xFFF4F8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1EAE4),
    onSurfaceVariant = Color(0xFF455249)
)

@Composable
fun BlocklineTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
