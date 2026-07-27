package com.buk.bukin.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Light only, no dynamic colour. The brand is the point — a device wallpaper palette is
 * not, and a dark variant of a ticket that has to read as the same object in every photo
 * of the demo is a liability rather than a feature.
 *
 * The page is [BukField], not [BukBackground]. That closed a question open since session 1:
 * every mockup renders a periwinkle field, it gives the blue ticket something to sit on
 * instead of floating on white, and it introduces no fifth hex — it is `BukBlue` at 10%
 * over `BukBackground`.
 *
 * System bar icons are forced dark in `MainActivity` via `enableEdgeToEdge`, which owns
 * that decision; setting it here as well would fight it.
 */
private val BukColorScheme = lightColorScheme(
    primary = BukBlue,
    onPrimary = Color.White,
    primaryContainer = BukBlueDeep,
    onPrimaryContainer = Color.White,

    background = BukField,
    onBackground = BukInk,

    surface = BukSurface,
    onSurface = BukInk,
    surfaceVariant = BukSurface,
    onSurfaceVariant = BukInkMuted,

    // All success meaning. `BukSuccess` itself fails both the text and the graphic
    // threshold and never reaches the scheme.
    tertiary = BukSuccessInk,
    onTertiary = Color.White,

    outline = BukBorder,
    outlineVariant = BukBorder,
)

@Composable
fun BukInTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BukColorScheme,
        typography = BukTypography,
        shapes = BukShapes,
        content = content,
    )
}
