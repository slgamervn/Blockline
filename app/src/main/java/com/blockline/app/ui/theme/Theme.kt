package com.blockline.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode { DARK, LIGHT, SYSTEM, AMOLED }

/** Centralized editor chrome and syntax colors. Keep editor token colors out of individual screens. */
object EditorPalette {
    val editor = Color(0xFF1E1E1E)
    val sidebar = Color(0xFF252526)
    val activityBar = Color(0xFF333333)
    val tabInactive = Color(0xFF2D2D2D)
    val border = Color(0xFF3C3C3C)
    val selection = Color(0xFF264F78)
    val currentLine = Color(0xFF2A2D2E)
    val statusBar = Color(0xFF007ACC)
    val jsonKey = Color(0xFF9CDCFE)
    val string = Color(0xFFCE9178)
    val number = Color(0xFFB5CEA8)
    val keyword = Color(0xFF569CD6)
    val function = Color(0xFFDCDCAA)
    val comment = Color(0xFF6A9955)
    val type = Color(0xFF4EC9B0)
    val minecraftGreen = Color(0xFF3DBA7A)
    val pixelLight = Color(0xFFB9C2BC)
    val pixelDark = Color(0xFF151816)
}

val Emerald = Color(0xFF9BE7B5)
val Amber = Color(0xFFE6B35A)
val OnSurface = Color(0xFFF3F6F2)

private val CompactTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontSize = 48.sp, lineHeight = 52.sp),
        displayMedium = base.displayMedium.copy(fontSize = 40.sp, lineHeight = 44.sp),
        displaySmall = base.displaySmall.copy(fontSize = 32.sp, lineHeight = 36.sp),
        headlineLarge = base.headlineLarge.copy(fontSize = 28.sp, lineHeight = 32.sp),
        headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 21.sp, lineHeight = 25.sp),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
        titleMedium = base.titleMedium.copy(fontSize = 15.sp, lineHeight = 19.sp),
        titleSmall = base.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = base.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
        labelLarge = base.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelMedium = base.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
        labelSmall = base.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
    )
}

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
    background = EditorPalette.editor,
    onBackground = OnSurface,
    surface = EditorPalette.sidebar,
    onSurface = OnSurface,
    surfaceVariant = Color(0xFF303033),
    onSurfaceVariant = Color(0xFFC5C5C5),
    outline = EditorPalette.border,
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B43),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5F3D9),
    onPrimaryContainer = Color(0xFF082D1B),
    secondary = Color(0xFF805A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE2A8),
    onSecondaryContainer = Color(0xFF281900),
    tertiary = Color(0xFF2B6C9E),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF4B4B4B),
    outline = Color(0xFF767676),
    error = Color(0xFFBA1A1A)
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF63D995),
    onPrimary = Color(0xFF00210E),
    primaryContainer = Color(0xFF16452D),
    onPrimaryContainer = Color(0xFFB7F2CE),
    secondary = Color(0xFFE6B35A),
    onSecondary = Color(0xFF261900),
    secondaryContainer = Color(0xFF44300A),
    onSecondaryContainer = Color(0xFFFFE2A8),
    tertiary = Color(0xFF8BC7FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF050505),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFFCACACA),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFFF8A80)
)

@Composable
fun BlocklineTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = when (mode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            window.navigationBarColor = colors.surface.toArgbCompat()
            window.statusBarColor = colors.background.toArgbCompat()
        }
    }
    MaterialTheme(colorScheme = colors, typography = CompactTypography, content = content)
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
