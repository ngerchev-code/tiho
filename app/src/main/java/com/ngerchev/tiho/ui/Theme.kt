package com.ngerchev.tiho.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neutral, calm palette. The only strong colour is the red used while the microphone is live.
val RecordLight = Color(0xFFD93025)
val RecordDark = Color(0xFFF28B82)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F2937),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8EBF0),
    onPrimaryContainer = Color(0xFF111827),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF0F3),
    onSecondaryContainer = Color(0xFF1F2937),
    tertiary = Color(0xFF4B5563),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFC5CAD3),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF5F6F8),
    surfaceContainerHigh = Color(0xFFEEF0F3),
    surfaceContainerHighest = Color(0xFFE8EBF0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5E7EB),
    onPrimary = Color(0xFF111827),
    primaryContainer = Color(0xFF2A2F3A),
    onPrimaryContainer = Color(0xFFF3F4F6),
    secondary = Color(0xFFB8BEC9),
    onSecondary = Color(0xFF111827),
    secondaryContainer = Color(0xFF252A33),
    onSecondaryContainer = Color(0xFFE5E7EB),
    tertiary = Color(0xFFB8BEC9),
    onTertiary = Color(0xFF111827),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF0F1115),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1C2028),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF3B4250),
    outlineVariant = Color(0xFF262B35),
    error = Color(0xFFF28B82),
    onError = Color(0xFF111827),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF14171C),
    surfaceContainer = Color(0xFF181B21),
    surfaceContainerHigh = Color(0xFF1F232B),
    surfaceContainerHighest = Color(0xFF262B35)
)

@Composable
fun TihoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}

@Composable
fun recordColor(): Color = if (isSystemInDarkTheme()) RecordDark else RecordLight
