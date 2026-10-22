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

enum class ThemeMode { DARK, LIGHT, SYSTEM }

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
    background = Color(0xFF080B0A),
    onBackground = OnSurface,
    surface = Color(0xFF101512),
    onSurface = OnSurface,
    surfaceVariant = Color(0xFF1A221C),
    onSurfaceVariant = Color(0xFFA1ADA4),
    outline = Color(0xFF34473A),
    error = Color(0xFFFF8A80)
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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = CompactTypography, content = content)
}
