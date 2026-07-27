package com.buk.bukin.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.buk.bukin.designsystem.theme.BukMotion

/**
 * The press state, in one place.
 *
 * Every interactive surface scales to **0.97** on press and returns on `spatialFast`. It is
 * one `graphicsLayer` and it is most of what makes a control feel worth pressing — the app
 * had no press feedback of any kind before this, relying entirely on Material's default
 * ripple, and the ticket's help strip had a bare `Modifier.clickable` with not even that.
 *
 * It is a single shared modifier on purpose. Press feedback that is present on four
 * surfaces and missing on the fifth reads worse than none at all, and the only way to stop
 * that happening is to make "add a press state" not a decision anyone takes per component.
 *
 * Two rules it enforces:
 *
 * - **A disabled surface does not press.** A control that animates under your finger and
 *   then does nothing is a lie, and the session list was full of rows whose whole
 *   affordance was a coloured word.
 * - **Ripple is opt-in.** It belongs on list rows, where the app is a menu. It does not
 *   belong on the ticket, which is an object.
 *
 * The scale is read inside the `graphicsLayer` lambda, so pressing recomposes nothing.
 */
fun Modifier.bukPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    ripple: Boolean = false,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)

    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = BukMotion.spatialFast,
        label = "pressScale",
    )

    this
        .graphicsLayer {
            // Read here, never in composition — a press must not recompose the row.
            val s = scale.value
            scaleX = s
            scaleY = s
        }
        .clickable(
            interactionSource = interactionSource,
            indication = if (ripple) LocalIndication.current else null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = { currentOnClick() },
        )
}

/**
 * The press scale with no click of its own — for a surface whose click is already wired by
 * a Material component (a `Button`, a `Card(onClick = …)`) but which still has to
 * acknowledge the finger the same way everything else does.
 */
fun Modifier.bukPressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = BukMotion.spatialFast,
        label = "pressScale",
    )
    graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

private const val PRESS_SCALE = 0.97f

/**
 * Nothing interactive falls below this in either dimension.
 *
 * Stated once and imported, rather than being a 48 that four components happen to agree on.
 */
val BukMinTouchTarget: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(48f)
