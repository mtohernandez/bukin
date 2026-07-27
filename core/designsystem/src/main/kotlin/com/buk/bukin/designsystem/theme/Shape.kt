package com.buk.bukin.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * `4 · 8 · 12 · 16 · 20 · 28 · Full`.
 *
 * `RoundedCornerShape` does not appear outside this file and `TicketShape.kt`. Everything
 * else names a step.
 */
object BukShape {
    /** Hairline details. */
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(8.dp)

    /** Skeleton blocks, small chips. */
    val md = RoundedCornerShape(12.dp)

    /** Buttons, inputs. */
    val lg = RoundedCornerShape(16.dp)

    /** Notice cards, surfaces. */
    val xl = RoundedCornerShape(20.dp)

    /** Ticket card, instance cards, sheets. */
    val xxl = RoundedCornerShape(28.dp)

    /** Pills, avatars, the success container. */
    val full = RoundedCornerShape(percent = 50)
}

val BukShapes: Shapes = Shapes(
    extraSmall = BukShape.sm,
    small = BukShape.md,
    medium = BukShape.lg,
    large = BukShape.xl,
    extraLarge = BukShape.xxl,
)
