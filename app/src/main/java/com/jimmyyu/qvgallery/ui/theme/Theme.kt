package com.jimmyyu.qvgallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.jimmyyu.qvgallery.data.AppSettings
import com.jimmyyu.qvgallery.data.ThemeMode

/** Accent choices offered when Material You is turned off. */
val AccentColors = listOf(
    0xFF5C6BC0, 0xFF9E9E9E, 0xFFFF9800, 0xFFFF6F00, 0xFFFF3D00, 0xFFD50000,
    0xFFC2185B, 0xFFEC407A, 0xFF8E24AA, 0xFF3F51B5, 0xFF1E88E5, 0xFF039BE5,
    0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFF795548,
)

@Composable
fun isDark(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun QvTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val context = LocalContext.current
    var scheme = if (settings.dynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        seedScheme(Color(settings.accentColor.toInt()), dark)
    }
    if (dark && settings.amoled) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A small tonal scheme derived from one accent, used when dynamic colour is off. */
fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    fun tone(lightness: Float, satScale: Float = 1f): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed.toArgb(), hsl)
        hsl[1] = (hsl[1] * satScale).coerceIn(0f, 1f)
        hsl[2] = lightness
        return Color(ColorUtils.HSLToColor(hsl))
    }
    return if (dark) {
        darkColorScheme(
            primary = tone(0.75f),
            onPrimary = tone(0.15f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.75f, 0.4f),
            onSecondary = tone(0.15f, 0.4f),
            secondaryContainer = tone(0.28f, 0.35f),
            onSecondaryContainer = tone(0.90f, 0.4f),
            tertiary = tone(0.78f, 0.6f),
            background = tone(0.06f, 0.15f),
            surface = tone(0.06f, 0.15f),
            surfaceVariant = tone(0.20f, 0.15f),
            surfaceContainerLowest = tone(0.04f, 0.15f),
            surfaceContainerLow = tone(0.09f, 0.15f),
            surfaceContainer = tone(0.11f, 0.15f),
            surfaceContainerHigh = tone(0.14f, 0.15f),
            surfaceContainerHighest = tone(0.18f, 0.15f),
            onSurface = tone(0.90f, 0.1f),
            onSurfaceVariant = tone(0.78f, 0.1f),
            outline = tone(0.55f, 0.1f),
            outlineVariant = tone(0.30f, 0.1f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.88f),
            onPrimaryContainer = tone(0.12f),
            secondary = tone(0.40f, 0.4f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.88f, 0.4f),
            onSecondaryContainer = tone(0.12f, 0.4f),
            tertiary = tone(0.42f, 0.6f),
            background = tone(0.98f, 0.3f),
            surface = tone(0.98f, 0.3f),
            surfaceVariant = tone(0.90f, 0.2f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(0.96f, 0.25f),
            surfaceContainer = tone(0.94f, 0.25f),
            surfaceContainerHigh = tone(0.92f, 0.25f),
            surfaceContainerHighest = tone(0.90f, 0.25f),
            onSurface = tone(0.10f, 0.1f),
            onSurfaceVariant = tone(0.30f, 0.1f),
            outline = tone(0.50f, 0.1f),
            outlineVariant = tone(0.80f, 0.1f),
        )
    }
}
