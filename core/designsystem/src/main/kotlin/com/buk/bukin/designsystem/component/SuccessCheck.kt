package com.buk.bukin.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccess

private val CheckSize = 150.dp

/**
 * The payoff. The single most important moment in the app — the top complaint about the
 * existing product is that nothing visibly changes, so a user never knows whether their
 * attendance registered.
 *
 * The stroke draws itself on. With motion turned off it appears complete instead, which
 * is the same information without the flourish.
 */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    val progress = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(animate) {
        if (animate) {
            progress.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
    }

    val description = stringResource(R.string.checkin_success_description)
    val measure = remember { PathMeasure() }
    val drawn = remember { Path() }

    Canvas(
        modifier = modifier
            .size(CheckSize)
            .semantics { contentDescription = description },
    ) {
        val w = size.width
        val h = size.height
        val full = Path().apply {
            moveTo(w * 0.14f, h * 0.52f)
            lineTo(w * 0.40f, h * 0.78f)
            lineTo(w * 0.88f, h * 0.20f)
        }

        measure.setPath(full, false)
        drawn.reset()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)

        drawPath(
            path = drawn,
            color = BukSuccess,
            style = Stroke(
                width = w * 0.155f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SuccessCheckPreview() {
    BukInTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(BukSpacing.lg),
        ) {
            SuccessCheck()
        }
    }
}
