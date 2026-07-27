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
 *
 * Every pair below was measured with a WCAG 2.1 relative-luminance calculation over the
 * composited values, at the worst end of every gradient. The numbers in the comments are
 * that measurement, not an estimate. A new pair gets computed before it ships.
 */

/** Primary / brand blue. */
val BukBlue: Color = Color(0xFF2F4DAA)

/** Near-white base. The *page* is [BukField]; this is the value it is composited over. */
val BukBackground: Color = Color(0xFFF7F9FF)

/** Primary text, near-black. */
val BukInk: Color = Color(0xFF030819)

/**
 * Brand success.
 *
 * **Never carries text or an icon.** At `#2BAB51` it measures 2.98:1 on white and 2.43:1
 * on the page field — it fails the 4.5:1 text threshold *and* the 3:1 graphic threshold.
 * It stays here because it is one of the four authoritative brand values, but it is a
 * decorative accent only. [BukSuccessInk] carries every piece of success meaning.
 */
val BukSuccess: Color = Color(0xFF2BAB51)

// ---------------------------------------------------------------------------
// Derived — no new hex below this line.
// ---------------------------------------------------------------------------

/** The page. Periwinkle, not near-white — it gives the blue ticket a field to sit on. */
val BukField: Color = BukBlue.copy(alpha = 0.10f).compositeOver(BukBackground) // #E3E8F6

/** Deep end of the ticket gradient. */
val BukBlueDeep: Color = lerp(BukBlue, BukInk, 0.35f) // #1D3373

/**
 * Top-left → bottom-right fill of the ticket card.
 *
 * Runs **deep**, not light. The old direction (`BukBlue → lighter`) could not be made
 * legible: pure white on that light end measured 4.27:1, and white at 92% alpha still
 * only reached 4.44:1. Inverting makes [BukBlue] itself the lightest point of the card —
 * the worst case — and every white level clears: white 7.60:1, muted 4.80:1, faint 3.49:1.
 */
val BukBlueGradient: List<Color> = listOf(BukBlue, BukBlueDeep)

/**
 * Concentric halo rings behind the Check In button, outermost first. Descending alpha over
 * the page field so they read as light rather than as translucent panes.
 */
val BukBlueHalo: List<Color> = listOf(
    BukBlue.copy(alpha = 0.10f).compositeOver(BukField),
    BukBlue.copy(alpha = 0.22f).compositeOver(BukField),
)

/** All success meaning: text, icons, edges, pills. 4.65:1 on the field, 5.70:1 on white. */
val BukSuccessInk: Color = lerp(BukSuccess, BukInk, 0.30f) // #1C7544

/** Muted body text and captions. 4.66:1 on the field, 4.96:1 on a white surface. */
val BukInkMuted: Color = BukInk.copy(alpha = 0.58f)

/** Card and surface over the page field. */
val BukSurface: Color = Color.White.compositeOver(BukField)

/** Hairline borders — outlined cards, dividers. */
val BukBorder: Color = BukInk.copy(alpha = 0.12f)

/** Desaturated blue-grey for the proximity figures. 4.43:1 — a graphic, needs 3:1. */
val BukInkSoft: Color = lerp(lerp(BukBlue, BukInk, 0.42f), Color.White, 0.30f)

/** Labels on the ticket gradient. 4.80:1 at the gradient's light end. */
val BukOnBlueMuted: Color = Color.White.copy(alpha = 0.72f)

/** Tear dots and hairlines on the ticket — a graphic, not text. 3.49:1 against a 3:1 bar. */
val BukOnBlueFaint: Color = Color.White.copy(alpha = 0.55f)

/**
 * The ticket's state pill over the gradient.
 *
 * A translucent white wash rather than a solid, so the gradient reads through it. This is
 * one of the two glass surfaces, and it is designed as a translucent solid first — below
 * API 31 `Modifier.blur` is a silent no-op and this is all most devices will ever see.
 */
val BukOnBlueScrim: Color = Color.White.copy(alpha = 0.16f)

/** Skeleton base and its travelling highlight, over a white surface. */
val BukSkeletonBase: Color = BukInk.copy(alpha = 0.08f)
val BukSkeletonSheen: Color = BukInk.copy(alpha = 0.03f)
