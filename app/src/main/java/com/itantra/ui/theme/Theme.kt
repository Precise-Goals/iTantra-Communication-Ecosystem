package com.itantra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// =========================================================
// iTantra Theme — Pure White Monochromatic Theme
// =========================================================

private val iTantraLightColorScheme = lightColorScheme(
    primary = iTantraBlack,
    onPrimary = iTantraWhite,
    primaryContainer = iTantraCardAlt,
    onPrimaryContainer = iTantraBlack,
    secondary = iTantraBlack80,
    onSecondary = iTantraWhite,
    secondaryContainer = iTantraSurfaceHover,
    onSecondaryContainer = iTantraBlack80,
    tertiary = iTantraBlack60,
    onTertiary = iTantraWhite,
    background = iTantraBackground,
    onBackground = iTantraBlack,
    surface = iTantraSurface,
    onSurface = iTantraBlack,
    surfaceVariant = iTantraCard,
    onSurfaceVariant = iTantraBlack60,
    outline = iTantraBorder,
    outlineVariant = iTantraDivider,
    error = iTantraError,
    onError = iTantraWhite
)

@Composable
fun ITantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = iTantraLightColorScheme,
        typography = ITantraTypography,
        content = content
    )
}
