package com.buk.bukin.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Motion tokens, built on `androidx.compose.animation.core.spring` — stable foundation API.
 *
 * **Material 3 Expressive is deliberately not adopted.** `MaterialShapes` and
 * `LoadingIndicator` were reverted to *experimental* in material3 1.5.0-alpha19
 * (b/497876695, b/497877850) and `MotionScheme` graduated only on the 1.5 alpha branch.
 * This project is on 1.4.0 stable via BOM 2026.06.01. Bumping the BOM to chase those APIs
 * would destabilise a working build to buy nothing this design needs.
 *
 * Two families, and the split is not cosmetic:
 *
 * - **Spatial** — anything that moves, resizes, or changes corner radius. Slight overshoot,
 *   because a physical object that stops dead reads as cheap.
 * - **Effects** — colour and opacity. `dampingRatio = 1f`, never overshoot. An overshooting
 *   colour goes *past* its target and comes back, which reads as a rendering bug rather
 *   than as polish.
 *
 * Settle times land inside One UI's stated 100–500 ms envelope.
 */
object BukMotion {

    // ---- Spatial -----------------------------------------------------------------
    /** Small moves: press scale, indicator morphs. */
    val spatialFast: SpringSpec<Float> =
        spring(dampingRatio = 0.80f, stiffness = 1400f)

    /** The success morph, card transitions. */
    val spatialDefault: SpringSpec<Float> =
        spring(dampingRatio = 0.85f, stiffness = 700f)

    /** The halo, ambient motion. */
    val spatialSlow: SpringSpec<Float> =
        spring(dampingRatio = 0.90f, stiffness = 350f)

    // ---- Effects -----------------------------------------------------------------
    val effectsFast: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)

    val effectsDefault: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

    // ---- Typed variants ----------------------------------------------------------
    // `spring()` is generic over the animated type but needs a matching visibility
    // threshold per type, so the two the app actually animates are spelled out here
    // rather than re-derived at each call site.

    /** Fills and tints. An effects spring — colour never overshoots. */
    val colorDefault: SpringSpec<Color> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

    /** Corner radius through the success morph, and the onboarding indicator's width. */
    val dpDefault: SpringSpec<Dp> =
        spring(dampingRatio = 0.85f, stiffness = 700f, visibilityThreshold = Dp.VisibilityThreshold)

    val dpFast: SpringSpec<Dp> =
        spring(dampingRatio = 0.80f, stiffness = 1400f, visibilityThreshold = Dp.VisibilityThreshold)
}
