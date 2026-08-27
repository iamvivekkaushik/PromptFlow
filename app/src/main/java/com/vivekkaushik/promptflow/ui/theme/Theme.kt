package com.vivekkaushik.promptflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark-first brand theme (spec: lime seed; dynamic-color light pass is a listed "next step")
private val DarkColorScheme = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = LimeContainer,
    onPrimaryContainer = OnLimeContainer,
    secondary = OnSurfaceVariant,
    onSecondary = Surface,
    background = AppBackground,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerLow = SurfaceContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Record,
    tertiary = Warning,
)

@Composable
fun PromptFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
