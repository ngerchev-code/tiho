package com.ngerchev.tiho.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ngerchev.tiho.data.Accent
import com.ngerchev.tiho.data.FileKind
import com.ngerchev.tiho.data.TextSize

/** True when the app is drawn dark (follows the user's choice, not only the system). */
val LocalIsDark = staticCompositionLocalOf { false }

/** Multiplier for message and draft text, from the "text size" setting. */
val LocalMessageScale = staticCompositionLocalOf { 1f }

// The red used while the microphone is live.
private val RecordLight = Color(0xFFD93025)
private val RecordDark = Color(0xFFF28B82)

private class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color
)

private fun accentColors(accent: Accent, dark: Boolean): AccentColors = when (accent) {
    Accent.GRAPHITE ->
        if (dark) AccentColors(Color(0xFFE5E7EB), Color(0xFF111827), Color(0xFF2A2F3A), Color(0xFFF3F4F6))
        else AccentColors(Color(0xFF1F2937), Color(0xFFFFFFFF), Color(0xFFE8EBF0), Color(0xFF111827))

    Accent.BLUE ->
        if (dark) AccentColors(Color(0xFF8AB4F8), Color(0xFF0B1F45), Color(0xFF1E3A6E), Color(0xFFDCE8FF))
        else AccentColors(Color(0xFF2563EB), Color(0xFFFFFFFF), Color(0xFFDBEAFE), Color(0xFF0B2A6B))

    Accent.GREEN ->
        if (dark) AccentColors(Color(0xFF6EDCB0), Color(0xFF06331F), Color(0xFF14533A), Color(0xFFD5F3E6))
        else AccentColors(Color(0xFF0B8A5C), Color(0xFFFFFFFF), Color(0xFFD5F3E6), Color(0xFF06402B))

    Accent.VIOLET ->
        if (dark) AccentColors(Color(0xFFC4A7F5), Color(0xFF2A1259), Color(0xFF45288A), Color(0xFFEADFFB))
        else AccentColors(Color(0xFF7C4DDB), Color(0xFFFFFFFF), Color(0xFFEADFFB), Color(0xFF2E1065))
}

/** The colour shown for an accent in the settings picker. */
fun accentPreview(accent: Accent, dark: Boolean): Color = accentColors(accent, dark).primary

private fun colorScheme(accent: Accent, dark: Boolean): ColorScheme {
    val a = accentColors(accent, dark)
    return if (dark) {
        darkColorScheme(
            primary = a.primary,
            onPrimary = a.onPrimary,
            primaryContainer = a.container,
            onPrimaryContainer = a.onContainer,
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
    } else {
        lightColorScheme(
            primary = a.primary,
            onPrimary = a.onPrimary,
            primaryContainer = a.container,
            onPrimaryContainer = a.onContainer,
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
    }
}

@Composable
fun TihoTheme(
    dark: Boolean,
    accent: Accent,
    textSize: TextSize,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalMessageScale provides textSize.scale
    ) {
        MaterialTheme(colorScheme = colorScheme(accent, dark), content = content)
    }
}

@Composable
fun recordColor(): Color = if (LocalIsDark.current) RecordDark else RecordLight

/** Familiar colours for the file-type badges. */
fun fileKindColor(kind: FileKind): Color = when (kind) {
    FileKind.IMAGE -> Color(0xFF6B7280)
    FileKind.WORD -> Color(0xFF2B579A)
    FileKind.EXCEL -> Color(0xFF217346)
    FileKind.POWERPOINT -> Color(0xFFD24726)
    FileKind.PDF -> Color(0xFFC62828)
}
