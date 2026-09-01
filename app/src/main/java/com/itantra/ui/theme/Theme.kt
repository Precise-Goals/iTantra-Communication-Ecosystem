package com.itantra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val iTantraDarkColorScheme = darkColorScheme(
    primary = SignalOrange500,
    onPrimary = SpaceBlue900,
    primaryContainer = SignalOrange300.copy(alpha = 0.15f),
    onPrimaryContainer = SignalOrange300,
    secondary = ActiveGreen500,
    onSecondary = Surface900,
    secondaryContainer = ActiveGreen300.copy(alpha = 0.15f),
    onSecondaryContainer = ActiveGreen300,
    tertiary = PeerDotWifi,
    onTertiary = Surface900,
    error = DistressRed500,
    onError = Color.White,
    errorContainer = DistressRed600.copy(alpha = 0.2f),
    onErrorContainer = DistressRed400,
    background = Surface900,
    onBackground = OnSurface,
    surface = Surface800,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceDim,
    outline = OnSurfaceDimmer,
    outlineVariant = SpaceBlue600,
    inverseSurface = OnSurface,
    inverseOnSurface = Surface900,
    inversePrimary = SpaceBlue700
)

@Composable
fun ITantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = iTantraDarkColorScheme,
        typography = ITantraTypography,
        shapes = ITantraShapes,
        content = content
    )
}
