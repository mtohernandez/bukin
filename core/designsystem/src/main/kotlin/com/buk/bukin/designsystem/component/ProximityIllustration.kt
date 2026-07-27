package com.buk.bukin.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkSoft
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * Two figures with a signal travelling between them — the SCANNING centrepiece.
 *
 * **The arcs move, and that is the point of this component existing at all.** This
 * illustration previously contained no animation API of any kind, so during SCANNING the
 * entire screen was frozen: a static picture above a static sentence. That is the state a
 * person spends the longest in while uncertain, and a frozen screen is indistinguishable
 * from a crashed app — the exact shape of the top complaint about the product this
 * replaces, *"the buttons do not change state so you never know if your attendance was
 * actually registered."* "No spinner without a sentence" has a converse, and it is just as
 * binding: no sentence without motion, where the person is waiting on something the app is
 * actively doing.
 *
 * Three rules it keeps:
 *
 * - **One clock.** A single `rememberInfiniteTransition`; the three arcs take their phase
 *   from it by offset. Not three transitions.
 * - **Zero recomposition.** The phase is read inside the `Canvas` draw lambda. Nothing
 *   about a travelling arc belongs in composition.
 * - **Not a spinner.** It depicts the activity rather than standing in for unknown
 *   progress, which is why it is exempt from the one-indeterminate-indicator rule. The test
 *   is simple: a spinner would look identical whatever the app were doing; these arcs would
 *   be wrong for any state but SCANNING.
 *
 * `ponytail:` deliberately presentation-only. The arcs loop on a timer and do not know
 * whether anything has actually been heard, so the illustration is honest about *scanning*
 * but cannot distinguish "listening" from "heard something stale". Making it state-aware
 * means `CheckInState.Scanning` carrying a sighting flag, which touches `:domain` and
 * `:core:ble` — out of scope for a design session. Upgrade path: add the flag, and let a
 * detected-but-stale sighting brighten the arriving arc.
 */
@Composable
fun ProximityIllustration(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.checkin_proximity_description)
    val phase = travelPhase(enabled = animationsEnabled())

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val width = (screenWidth * 0.52f).dp.coerceIn(160.dp, 240.dp)

    Canvas(
        modifier = modifier
            .size(width = width, height = width * IllustrationAspect)
            .semantics { contentDescription = description },
    ) {
        val unit = size.minDimension / 12f
        val upper = Offset(size.width * 0.14f, size.height * 0.20f)
        val lower = Offset(size.width * 0.62f, size.height * 0.72f)

        drawFigure(headCenter = upper, unit = unit)
        drawFigure(headCenter = lower, unit = unit, mirrored = true)

        // Read inside the draw lambda. Reading it in composition would recompose the whole
        // screen on every frame of a state that can last minutes.
        drawSignal(origin = lower, unit = unit, phase = phase.value)
    }
}

/**
 * Three arcs on one clock, staggered by a third of a cycle each. Each scales outward from
 * the emitting figure and fades as it crosses the gap, so the picture reads as a signal in
 * flight rather than as a picture of one.
 */
private fun DrawScope.drawSignal(origin: Offset, unit: Float, phase: Float) {
    repeat(ArcCount) { i ->
        val t = (phase + i / ArcCount.toFloat()) % 1f
        val radius = unit * (ArcMinRadius + t * (ArcMaxRadius - ArcMinRadius))
        // Fade in quickly, out slowly: an arc that pops into existence at full strength
        // reads as a flicker.
        val alpha = (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return@repeat

        drawArc(
            color = BukInkSoft.copy(alpha = alpha),
            startAngle = 185f,
            sweepAngle = 85f,
            useCenter = false,
            topLeft = Offset(origin.x - radius, origin.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = unit * 0.9f, cap = StrokeCap.Round),
        )
    }
}

/**
 * A head and a shoulder mass with a gap between them — the mockup's silhouette. [mirrored]
 * flips which side the shoulder slopes toward, so the two figures face each other.
 */
private fun DrawScope.drawFigure(headCenter: Offset, unit: Float, mirrored: Boolean = false) {
    val headRadius = unit * 0.88f
    drawCircle(color = BukInkSoft, radius = headRadius, center = headCenter)

    val bodyWidth = unit * 2.9f
    val bodyHeight = unit * 1.7f
    val bodyTop = headCenter.y + headRadius * 1.45f
    val bodyLeft = if (mirrored) headCenter.x - bodyWidth + unit * 0.95f else headCenter.x - unit * 0.95f

    drawRoundRect(
        color = BukInkSoft,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(unit * 0.85f),
    )
}

/** Motion off: the arcs sit at their resting radii, which is what shipped before. */
@Composable
private fun travelPhase(enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(StaticPhase) }
    val transition = rememberInfiniteTransition(label = "proximity")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "signalTravel",
    )
}

private const val ArcCount = 3
private const val ArcMinRadius = 2.2f
private const val ArcMaxRadius = 5.0f

/** Mid-cycle, so the static fallback shows arcs at a sensible spread rather than stacked. */
private const val StaticPhase = 0.33f

private const val IllustrationAspect = 165f / 210f

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun ProximityIllustrationPreview() {
    BukInTheme {
        Box(
            Modifier
                .background(BukField)
                .padding(BukSpacing.lg),
        ) {
            ProximityIllustration()
        }
    }
}
