package com.buk.bukin.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSkeletonBase
import com.buk.bukin.designsystem.theme.BukSkeletonSheen
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * The loading affordance for everything except the splash.
 *
 * Per NN/g: under a second show nothing, and for a full-page load in the 1–10 s range show
 * a **skeleton** rather than a spinner — a shape the person can predict beats a shape that
 * tells them nothing. After this component exists the app contains exactly one
 * indeterminate indicator, the splash AVD.
 *
 * What it replaces was worse than a spinner. The session list tracked `cargando` and
 * rendered *nothing* for it, falling through to a `LazyColumn` over an empty list, so the
 * screen was blank and then popped. Check-in rendered no ticket at all while the row was in
 * flight, so the header landed late and shoved the whole layout down.
 *
 * **One clock for the whole screen.** Twenty rows must not mean twenty
 * `InfiniteTransition`s, and the phase is read inside `drawWithCache` rather than in
 * composition, so a shimmering list recomposes zero times per frame.
 */
@Composable
fun BukSkeletonHost(content: @Composable () -> Unit) {
    val phase = if (animationsEnabled()) rememberShimmerPhase() else StaticPhase
    CompositionLocalProvider(LocalShimmerPhase provides phase, content = content)
}

/** One block. Give it the size of the thing it stands in for, never a generic bar. */
@Composable
fun BukSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = BukShape.md,
) {
    val phase = LocalShimmerPhase.current
    Box(
        modifier
            .background(BukSkeletonBase, shape)
            .drawWithCache {
                val travel = size.width * 2f
                onDrawWithContent {
                    drawContent()
                    // Read here, not in composition — this is a frame-rate value.
                    val x = (phase.value * travel) - size.width / 2f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = SheenStops,
                            start = Offset(x, 0f),
                            end = Offset(x + size.width, size.height),
                        ),
                    )
                }
            },
    )
}

/** A text-shaped block: one line at the height of the style it replaces. */
@Composable
fun BukSkeletonLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    BukSkeleton(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = BukShape.sm,
    )
}

private val SheenStops = listOf(BukSkeletonSheen.copy(alpha = 0f), BukSkeletonSheen, BukSkeletonSheen.copy(alpha = 0f))

@Composable
private fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
}

/** Motion off: the blocks still occupy their bounds, they just stop travelling. */
private val StaticPhase: State<Float> = mutableFloatStateOf(0f)

private val LocalShimmerPhase = compositionLocalOf { StaticPhase }

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun BukSkeletonPreview() {
    BukInTheme {
        BukSkeletonHost {
            Column(
                Modifier
                    .background(BukField)
                    .padding(BukSpacing.md2),
            ) {
                BukSkeletonLine(0.7f, height = 18.dp)
                Spacer(Modifier.height(BukSpacing.sm))
                BukSkeletonLine(0.45f)
                Spacer(Modifier.height(BukSpacing.md))
                BukSkeleton(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    shape = BukShape.xxl,
                )
            }
        }
    }
}
