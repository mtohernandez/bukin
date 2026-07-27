package com.buk.bukin.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stroke widths, elevations, and the handful of opacities the design actually names.
 *
 * They live here so `BorderStroke(1.dp, …)` and bare `0.55f` alphas stop being invented per
 * call site. A value that appears twice is a token; a value that appears once and is
 * intrinsic to one component stays a private constant in that component.
 */
object BukStroke {
    /** Hairline: card outlines, dividers. */
    val hairline: Dp = 1.dp

    /** A state-carrying edge — the brand-tinted or success-tinted instance card. */
    val emphasis: Dp = 2.dp
}

object BukElevation {
    /** Flat. The page is a field, not a stack of planes. */
    val none: Dp = 0.dp

    /** Cards that need to lift off the field just enough to read as objects. */
    val card: Dp = 1.dp

    /** The ticket, and modal sheets. */
    val raised: Dp = 3.dp
}

/**
 * Opacities that are *not* colour tokens — they modulate a whole element rather than
 * defining a shade, so they do not belong in `Color.kt`.
 */
object BukOpacity {
    /** A finished session: present, legible, plainly not actionable. */
    const val DISABLED = 0.45f

    /** The press state's companion — nothing else dims on touch. */
    const val PRESSED = 0.92f
}
