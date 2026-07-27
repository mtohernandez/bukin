package com.buk.bukin.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukBlueHalo
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukMotion
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccessInk
import com.buk.bukin.designsystem.theme.bukGutter
import kotlin.math.min

/**
 * Where the check-in screen resolves.
 *
 * The button's container **becomes** the success container. One object that resizes,
 * rounds, recolours and grows a check inside itself — never two composables swapped. What
 * this replaces was a jump cut: `AnimatedContent` cross-faded the button out and scaled a
 * bare green tick in from nothing, so the single most important moment in the app was the
 * one moment where the app visibly lost track of what it was drawing.
 *
 * It is drawn as **one canvas at a fixed footprint**. The container's bounds, corner radius
 * and fill are computed inside the draw lambda from a single [Animatable], so a running
 * morph costs zero recompositions *and* zero relayouts — only redraws. Animating a real
 * `size` in dp would have relaid out the whole column on every frame of the payoff.
 *
 * Reduced motion collapses this to a **cut**: [progress] snaps, so the success container
 * still appears, filled, with the check complete. The information never depends on the
 * animation.
 */
@Composable
fun CheckInMorph(
    phase: MorphPhase,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = animationsEnabled()
    val haptics = rememberBukHaptics()
    val sound = rememberBukSound()

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val gutter = bukGutter
    // What the component actually gets, not what the screen is: BukScreen has already
    // taken a gutter off each side. The old halo was a flat 280dp, which on a 320dp phone
    // with 20dp gutters left exactly zero margin.
    val available = (screenWidth.dp - gutter * 2)
    val buttonWidth = responsive(screenWidth, 0.62f, min = 176.dp, max = 240.dp)
    val successSize = responsive(screenWidth, 0.30f, min = 96.dp, max = 128.dp)
    val haloSize = (buttonWidth * OuterSpread).coerceAtMost(available)
    val footprintHeight = maxOf(ButtonHeight, successSize)

    val progress = remember { Animatable(phase.target()) }
    LaunchedEffect(phase, animate) {
        if (animate) {
            progress.animateTo(phase.target(), BukMotion.spatialDefault)
        } else {
            progress.snapTo(phase.target())
        }
    }

    // The Confirm haptic fires when the container starts moving, not on pointer-down and
    // not when the spring settles — so the thing you feel and the thing you see are one
    // event rather than two.
    LaunchedEffect(phase) {
        when (phase) {
            MorphPhase.Enviando -> haptics.confirm()
            MorphPhase.Success -> sound?.confirm()
            MorphPhase.Ready -> Unit
        }
    }

    val pulse = haloPulse(enabled = animate && phase == MorphPhase.Ready)
    val label = stringResource(R.string.checkin_button)
    val successLabel = stringResource(R.string.checkin_success_description)

    // SCANNING → READY is the moment the app stops asking the person to wait, so the
    // control arrives with a little weight rather than appearing between two frames.
    val entry = remember { Animatable(if (animate) 0.85f else 1f) }
    LaunchedEffect(Unit) { if (animate) entry.animateTo(1f, BukMotion.spatialDefault) }

    Box(
        // Sized to the **halo**, not the button. Sizing the root to the button clipped the
        // rings to the control's own bounds, so the beacon was a faint disc the same width
        // as the thing it was supposed to be radiating from.
        modifier = modifier
            .size(width = haloSize, height = footprintHeight * HaloHeightFactor)
            .graphicsLayer {
                val e = entry.value
                scaleX = e
                scaleY = e
            }
            .drawBehind {
                drawHalo(
                    p = progress.value,
                    pulse = pulse.value,
                    buttonWidth = buttonWidth.toPx(),
                    buttonHeight = ButtonHeight.toPx(),
                )
            }
            .semantics {
                contentDescription = if (phase == MorphPhase.Success) successLabel else label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = buttonWidth, height = footprintHeight)
                .drawBehind {
                    drawContainer(
                        p = progress.value,
                        buttonWidth = buttonWidth.toPx(),
                        buttonHeight = ButtonHeight.toPx(),
                        successSize = successSize.toPx(),
                        buttonRadius = ButtonRadius.toPx(),
                    )
                },
        )

        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier
                .clearAndSetSemantics {}
                .graphicsLayer {
                    // Frame-rate reads, inside the layer block.
                    val p = progress.value
                    alpha = (1f - p / LabelExitAt).coerceIn(0f, 1f)
                    translationY = -p * LabelRise.toPx()
                },
        )

        // The whole footprint is the target, so the control is never smaller than it looks
        // and never smaller than 48dp.
        if (phase == MorphPhase.Ready) {
            Box(
                Modifier
                    .size(width = buttonWidth, height = footprintHeight)
                    .clip(BukShape.full)
                    .bukPressable(onClick = onCheckIn, onClickLabel = label),
            )
        }
    }
}

/** Which end of the morph the container is heading for. */
enum class MorphPhase {
    /** A pill, brand blue, labelled. The halo breathes. */
    Ready,

    /** In flight. The container has closed to a circle but has not yet claimed success. */
    Enviando,

    /** Terminal. Filled, rounded, check complete. */
    Success,
    ;

    internal fun target(): Float = when (this) {
        Ready -> 0f
        // Deliberately short of 1: the shape has resolved but the check has not drawn and
        // the fill has not finished turning. A container that announced success while the
        // write was still in flight would be lying, and a failed write returns it to Ready.
        Enviando -> 0.55f
        Success -> 1f
    }
}

private fun DrawScope.drawContainer(
    p: Float,
    buttonWidth: Float,
    buttonHeight: Float,
    successSize: Float,
    buttonRadius: Float,
) {
    val shapeP = (p / ShapeSettlesAt).coerceIn(0f, 1f)
    val w = lerp(buttonWidth, successSize, shapeP)
    val h = lerp(buttonHeight, successSize, shapeP)
    val radius = lerp(buttonRadius, successSize / 2f, shapeP)

    // Colour runs on its own, slower ramp and never overshoots — an overshooting fill
    // reads as a rendering bug rather than as polish.
    val fill = lerp(BukBlue, BukSuccessInk, (p / 1f).coerceIn(0f, 1f))

    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(radius, radius),
    )

    if (p <= CheckStartsAt) return

    // The check draws on *inside* the filled container. White on BukSuccessInk is 5.70:1;
    // the bare BukSuccess stroke on the page field this replaces measured 2.43:1.
    val cp = ((p - CheckStartsAt) / (1f - CheckStartsAt)).coerceIn(0f, 1f)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = successSize / 2f

    val full = Path().apply {
        moveTo(cx - r * 0.40f, cy + r * 0.02f)
        lineTo(cx - r * 0.11f, cy + r * 0.31f)
        lineTo(cx + r * 0.42f, cy - r * 0.28f)
    }
    val measure = PathMeasure().apply { setPath(full, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * cp, drawn, true)

    drawPath(
        path = drawn,
        color = Color.White,
        style = Stroke(
            width = successSize * 0.10f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/**
 * Concentric rings echoing the button's own pill, at descending alpha.
 *
 * A beacon while Ready — the proximity idea, stated once and quietly. When the morph runs
 * they expand outward and dissipate, so the payoff has the rings clearing away from it
 * rather than sitting behind a container that is no longer a button.
 *
 * Decorative, and hidden from the accessibility tree by the parent's semantics.
 */
private fun DrawScope.drawHalo(p: Float, pulse: Float, buttonWidth: Float, buttonHeight: Float) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val alpha = (1f - p).coerceIn(0f, 1f)
    if (alpha <= 0f) return

    BukBlueHalo.forEachIndexed { index, color ->
        // index 0 is the outermost and faintest.
        val spread = if (index == 0) OuterSpread else InnerSpread
        val expansion = pulse * (1f + p * (0.34f - index * 0.10f))
        val w = buttonWidth * spread * expansion
        val h = buttonHeight * spread * expansion
        drawRoundRect(
            color = color.copy(alpha = color.alpha * alpha),
            topLeft = Offset(centre.x - w / 2f, centre.y - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2f, h / 2f),
        )
    }
}

@Composable
private fun haloPulse(enabled: Boolean): State<Float> {
    if (!enabled) return remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "halo")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )
}

/**
 * `min(fraction-of-width, cap)`, floored.
 *
 * Every size in this component used to be a fixed dp, and the halo was 280dp wide — on a
 * 320dp device with 20dp gutters that left exactly zero margin.
 */
private fun responsive(screenWidthDp: Int, fraction: Float, min: Dp, max: Dp): Dp {
    // The upper bound has to be computed first and the lower bound clamped under it.
    // Writing this as `coerceIn(min, minOf(max, screenWidth))` crashes outright on a screen
    // narrower than `min` — `coerceIn` throws on an inverted range — and that is not
    // hypothetical: it took down the check-in screen at 116dp during the width sweep.
    // A floor wider than the screen is not a floor, it is a contradiction.
    val upper = minOf(max, screenWidthDp.dp)
    val candidate = (screenWidthDp * fraction).dp
    return candidate.coerceIn(minOf(min, upper), upper)
}

private val ButtonHeight = 72.dp
private val ButtonRadius = 20.dp
private val LabelRise = 24.dp

/** The label is gone before the container has finished closing. */
private const val LabelExitAt = 0.35f

/** How far each halo ring spreads past the button, and how tall the halo box has to be. */
private const val OuterSpread = 1.62f
private const val InnerSpread = 1.30f
private const val HaloHeightFactor = 1.9f
private const val ShapeSettlesAt = 0.55f
private const val CheckStartsAt = 0.62f

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun MorphReadyPreview() = MorphPreview(MorphPhase.Ready)

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun MorphEnviandoPreview() = MorphPreview(MorphPhase.Enviando)

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun MorphSuccessPreview() = MorphPreview(MorphPhase.Success)

@Composable
private fun MorphPreview(phase: MorphPhase) {
    BukInTheme {
        Box(
            Modifier
                .background(BukField)
                .size(340.dp, 240.dp),
            contentAlignment = Alignment.Center,
        ) {
            CheckInMorph(phase = phase, onCheckIn = {})
        }
    }
}
