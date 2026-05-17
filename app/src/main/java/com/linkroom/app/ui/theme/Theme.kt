package com.linkroom.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AppAccent,
    onPrimary = AppTextPrimary,
    secondary = AppAccentIndigo,
    background = AppBackground,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurfaceHigh,
    onSurfaceVariant = AppTextSecondary,
    outline = AppBorder,
    tertiary = AppWarning,
    error = AppDanger
)

@Composable
fun LinkRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
