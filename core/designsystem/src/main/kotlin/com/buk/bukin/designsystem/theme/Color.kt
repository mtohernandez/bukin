package com.buk.bukin.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/*
 * The only file in the project that may contain a hex literal.
 *
 * Four Buk brand values, and everything else is a ramp, tint, or alpha of them. If a new
 * shade is needed, derive it here — do not add a fifth constant, and never inline a hex
 * in a composable.
 */

/** Primary / brand blue. */
val BukBlue: Color = Color(0xFF2F4DAA)

/** Page background. */
val BukBackground: Color = Color(0xFFF7F9FF)

/** Primary text, near-black. */
val BukInk: Color = Color(0xFF030819)

/** Success. */
val BukSuccess: Color = Color(0xFF2BAB51)

// ---------------------------------------------------------------------------
// Derived — no new hex below this line.
// ---------------------------------------------------------------------------

/** Lighter end of the ticket-card gradient: [BukBlue] lifted toward white. */
val BukBlueLight: Color = lerp(BukBlue, Color.White, 0.24f)

/** Top-left → bottom-right fill of the ticket card. */
val BukBlueGradient: List<Color> = listOf(BukBlue, BukBlueLight)

/**
 * Concentric halo rings behind the Check In button, outermost first. Descending alpha
 * over the page background so they read as light rather than as translucent panes.
 */
val BukBlueHalo: List<Color> = listOf(
    BukBlue.copy(alpha = 0.10f).compositeOver(BukBackground),
    BukBlue.copy(alpha = 0.22f).compositeOver(BukBackground),
)

/** Muted body text and footer microcopy. */
val BukInkMuted: Color = BukInk.copy(alpha = 0.55f)

/** Card and surface over the page background. */
val BukSurface: Color = Color.White.compositeOver(BukBackground)

/** Hairline borders — outlined cards, the help strip. */
val BukBorder: Color = BukInk.copy(alpha = 0.12f)

/** Desaturated blue-grey for the proximity figures. */
val BukInkSoft: Color = lerp(lerp(BukBlue, BukInk, 0.42f), Color.White, 0.30f)

/** The dark pill badge on the ticket card. */
val BukInkPill: Color = lerp(BukInk, BukBlue, 0.18f)

/** Dotted stub separator and other hairlines drawn on top of the blue card. */
val BukOnBlueMuted: Color = Color.White.copy(alpha = 0.55f)

/** Separator dots on the ticket stub — fainter than the labels beside them. */
val BukOnBlueFaint: Color = Color.White.copy(alpha = 0.35f)
