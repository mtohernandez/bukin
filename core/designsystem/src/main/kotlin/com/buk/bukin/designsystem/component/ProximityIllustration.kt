package com.buk.bukin.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkSoft
import com.buk.bukin.designsystem.theme.BukSpacing

private val IllustrationWidth = 210.dp
private val IllustrationHeight = 165.dp

/**
 * Two figures with signal arcs between them — the SCANNING and OFFLINE centrepiece.
 *
 * Drawn on a Canvas rather than shipped as a vector drawable so the figures take their
 * colour from the theme. A drawable would need its fill baked into the XML, which is
 * exactly the hex-outside-Color.kt this project forbids.
 */
@Composable
fun ProximityIllustration(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.checkin_proximity_description)
    Canvas(
        modifier = modifier
            .size(IllustrationWidth, IllustrationHeight)
            .semantics { contentDescription = description },
    ) {
        val unit = size.minDimension / 12f

        val upper = Offset(size.width * 0.14f, size.height * 0.20f)
        val lower = Offset(size.width * 0.62f, size.height * 0.72f)

        drawFigure(headCenter = upper, unit = unit)
        drawFigure(headCenter = lower, unit = unit, mirrored = true)

        // Signal fan radiating from the lower figure up toward the upper one. The radii
        // stop short of the upper figure's shoulder — arcs cutting through it read as
        // noise rather than as a signal crossing the gap.
        listOf(2.2f, 3.4f, 4.6f).forEach { r ->
            val radius = unit * r
            drawArc(
                color = BukInkSoft,
                startAngle = 185f,
                sweepAngle = 85f,
                useCenter = false,
                topLeft = Offset(lower.x - radius, lower.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = unit * 0.9f, cap = StrokeCap.Round),
            )
        }
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

@Preview(showBackground = true)
@Composable
private fun ProximityIllustrationPreview() {
    BukInTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(BukSpacing.lg),
        ) {
            ProximityIllustration()
        }
    }
}
