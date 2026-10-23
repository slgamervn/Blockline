package com.blockline.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode {
    /** Legacy preference retained for existing installs; visually maps to Dark+. */
    DARK,
    /** Legacy preference retained for existing installs; visually maps to Light+. */
    LIGHT,
    SYSTEM,
    DARK_PLUS,
    LIGHT_PLUS,
    AMOLED
}

val Emerald = Color(0xFF3DBA7A)
val Amber = Color(0xFFE6B35A)
val OnSurface = Color(0xFFD4D4D4)

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

private val DarkPlusColors = darkColorScheme(
    primary = Color(0xFF007ACC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF264F78),
    onPrimaryContainer = Color(0xFFD7E9F8),
    secondary = Emerald,
    onSecondary = Color(0xFF071A10),
    secondaryContainer = Color(0xFF20382A),
    onSecondaryContainer = Color(0xFFC6F2D7),
    tertiary = Color(0xFF4EC9B0),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFD4D4D4),
    surface = Color(0xFF252526),
    onSurface = Color(0xFFD4D4D4),
    surfaceVariant = Color(0xFF333333),
    onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF3C3C3C),
    error = Color(0xFFF48771),
    errorContainer = Color(0xFF5A1D1D),
    onErrorContainer = Color(0xFFFFDAD4)
)

private val LightPlusColors = lightColorScheme(
    primary = Color(0xFF007ACC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE4F7),
    onPrimaryContainer = Color(0xFF003A64),
    secondary = Color(0xFF267F45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F1DD),
    onSecondaryContainer = Color(0xFF174A28),
    tertiary = Color(0xFF267F99),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFF3F3F3),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF515151),
    outline = Color(0xFFB8B8B8),
    error = Color(0xFFB00020)
)

private val AmoledColors = DarkPlusColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0A0A0A),
    outline = Color(0xFF242424)
)

private val BlocklineShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun BlocklineTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.LIGHT, ThemeMode.LIGHT_PLUS -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK, ThemeMode.DARK_PLUS, ThemeMode.AMOLED -> true
    }
    val colors = when (mode) {
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.LIGHT, ThemeMode.LIGHT_PLUS -> LightPlusColors
        ThemeMode.SYSTEM -> if (systemDark) DarkPlusColors else LightPlusColors
        ThemeMode.DARK, ThemeMode.DARK_PLUS -> DarkPlusColors
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = CompactTypography, shapes = BlocklineShapes, content = content)
}
