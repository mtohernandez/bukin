package com.buk.bukin.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * It used to skip 12 and 20, which is why components reached past it — `TicketCard`
 * computed `BukSpacing.xs + 2.dp` to land on 6 and hardcoded `12.dp`/`6.dp` for the pill.
 * **If a value is not on the scale, the scale is wrong; do not do arithmetic at the call
 * site.**
 */
object BukSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val xs2: Dp = 6.dp
    val sm: Dp = 8.dp
    val sm2: Dp = 12.dp
    val md: Dp = 16.dp
    val md2: Dp = 20.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xl2: Dp = 40.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
}

/**
 * The horizontal page gutter.
 *
 * One UI asks for at least 24dp side margins; the mockups use 20, and a 320dp phone cannot
 * afford 24 once the ticket has to hold two times side by side. Resolved by width.
 */
val bukGutter: Dp
    @Composable
    @ReadOnlyComposable
    get() = if (LocalConfiguration.current.screenWidthDp < 360) BukSpacing.md2 else BukSpacing.lg
