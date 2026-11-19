package com.RamiroElortegui.lession.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LightBluePrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = LightBlueContainer,
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = LightBluePrimary,
    onSecondary = SurfaceWhite,
    background = BackgroundWhite,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    surfaceVariant = SurfaceWhite,
    onSurfaceVariant = TextMuted,
    error = ErrorRed,
    onError = SurfaceWhite
)

@Composable
fun LessionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
