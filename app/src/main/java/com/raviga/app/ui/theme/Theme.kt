package com.raviga.app.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val Scheme = lightColorScheme(
    primary = Ink.ink,
    onPrimary = Ink.paper,
    primaryContainer = Ink.wash,
    onPrimaryContainer = Ink.ink,
    secondary = Ink.link,
    onSecondary = Ink.surface,
    secondaryContainer = Ink.blueWash,
    onSecondaryContainer = Ink.blueInk,
    tertiary = Ink.link,
    onTertiary = Ink.surface,
    background = Ink.paper,
    onBackground = Ink.ink,
    surface = Ink.paper,
    onSurface = Ink.ink,
    surfaceVariant = Ink.wash,
    onSurfaceVariant = Ink.graphite,
    surfaceContainer = Ink.surface,
    surfaceContainerLow = Ink.paper,
    surfaceContainerLowest = Ink.surface,
    surfaceContainerHigh = Ink.surface,
    surfaceContainerHighest = Ink.wash,
    surfaceTint = Ink.paper,
    inverseSurface = Ink.ink,
    inverseOnSurface = Ink.paper,
    outline = Ink.ruleStrong,
    outlineVariant = Ink.rule,
    error = Ink.brick,
    onError = Ink.paper,
    errorContainer = Ink.paper,
    onErrorContainer = Ink.brick,
    scrim = Ink.ink,
)

/** Light only: cream paper (docs/DESIGN.md, "Workshop", palette C). */
@Composable
fun RavigaTheme(content: @Composable () -> Unit) {
    val selection = TextSelectionColors(
        handleColor = Ink.link,
        backgroundColor = Ink.link.copy(alpha = 0.18f),
    )
    MaterialTheme(
        colorScheme = Scheme,
        typography = RaTypography,
        shapes = RaShapes,
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}
