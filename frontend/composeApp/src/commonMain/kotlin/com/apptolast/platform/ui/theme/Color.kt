package com.apptolast.platform.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * AppToLast design tokens.
 *
 * Reference: GreenhouseAdmin (Kotlin) — acento verde `#00E676` sobre dark theme.
 * Reason: marca AppToLast = verde brillante en fondos oscuros.
 */
object AppToLastColors {
    // Primary — accent verde (GreenhouseAdmin)
    val Green500 = Color(0xFF00E676)
    val Green600 = Color(0xFF00C853)
    val Green700 = Color(0xFF009624)
    val Green100 = Color(0xFFB9F6CA)

    // Dark surface tokens
    val Dark900 = Color(0xFF0A0E13)
    val Dark800 = Color(0xFF12171F)
    val Dark700 = Color(0xFF1A212B)
    val Dark600 = Color(0xFF252E3B)
    val Dark500 = Color(0xFF334155)
    val Dark400 = Color(0xFF475569)

    // Light surface tokens (para tema claro futuro)
    val Light50 = Color(0xFFFAFAFA)
    val Light100 = Color(0xFFF5F5F5)
    val Light200 = Color(0xFFEEEEEE)

    // Semantic colors
    val Error = Color(0xFFEF5350)
    val Warning = Color(0xFFFFA726)
    val Info = Color(0xFF42A5F5)
    val Success = Green500

    // Status pod (Pod Dashboard spec)
    val PodRunning = Green500
    val PodPending = Warning
    val PodFailed = Error
    val PodSucceeded = Color(0xFF66BB6A)
    val PodUnknown = Color(0xFF78909C)
}

val AppToLastDarkColorScheme = darkColorScheme(
    primary = AppToLastColors.Green500,
    onPrimary = AppToLastColors.Dark900,
    primaryContainer = AppToLastColors.Green700,
    onPrimaryContainer = AppToLastColors.Green100,
    secondary = AppToLastColors.Green600,
    onSecondary = AppToLastColors.Dark900,
    tertiary = AppToLastColors.Info,
    background = AppToLastColors.Dark900,
    onBackground = Color(0xFFE6EAF0),
    surface = AppToLastColors.Dark800,
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = AppToLastColors.Dark700,
    onSurfaceVariant = Color(0xFFBDC5D1),
    outline = AppToLastColors.Dark500,
    outlineVariant = AppToLastColors.Dark600,
    error = AppToLastColors.Error,
    onError = Color.White,
)

val AppToLastLightColorScheme = lightColorScheme(
    primary = AppToLastColors.Green600,
    onPrimary = Color.White,
    primaryContainer = AppToLastColors.Green100,
    onPrimaryContainer = AppToLastColors.Green700,
    secondary = AppToLastColors.Green500,
    background = AppToLastColors.Light50,
    surface = Color.White,
    surfaceVariant = AppToLastColors.Light100,
    error = AppToLastColors.Error,
)
