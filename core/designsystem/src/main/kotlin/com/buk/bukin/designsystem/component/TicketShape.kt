package com.buk.bukin.designsystem.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The ticket silhouette: a 28dp rounded rectangle with a semicircular notch bitten out of
 * each side at the tear line.
 *
 * **The tear is structural.** Before this shape existed, "this is a ticket" was carried
 * entirely by a row of dotted circles drawn inside a plain card — a texture, doing a job
 * that belongs to the outline. Punching real notches means the object reads as a ticket
 * from its shape alone, at a glance, in a screenshot, and behind a blur.
 *
 * Shared with `InstanceCard`, which is the whole point of it being a shape rather than a
 * decoration: a row in the session list and the screen it opens are visibly the same
 * object, so tapping one is a continuation rather than a swap.
 *
 * @param tearFromBottom distance from the bottom edge to the centre of the notches.
 * @param notchRadius radius of each semicircle.
 * @param corner corner radius of the card itself.
 */
class TicketShape(
    private val tearFromBottom: Dp,
    private val notchRadius: Dp = DefaultNotchRadius,
    private val corner: Dp = DefaultCorner,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { notchRadius.toPx() }
        val c = with(density) { corner.toPx() }
        val tearY = size.height - with(density) { tearFromBottom.toPx() }

        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    cornerRadius = CornerRadius(c, c),
                ),
            )
        }

        // Two full circles centred on the edges; only the inner half of each lands on the
        // card, which is what makes the bite a clean semicircle without any arc maths.
        val notches = Path().apply {
            addOval(Rect(-r, tearY - r, r, tearY + r))
            addOval(Rect(size.width - r, tearY - r, size.width + r, tearY + r))
        }

        return Outline.Generic(Path().apply { op(body, notches, PathOperation.Difference) })
    }

    private companion object {
        val DefaultNotchRadius = 10.dp
        val DefaultCorner = 28.dp
    }
}
