package com.buk.bukin.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlueHalo
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukSpacing

private val ButtonWidth = 176.dp
private val ButtonHeight = 72.dp
private val HaloInnerWidth = 228.dp
private val HaloInnerHeight = 104.dp
private val HaloOuterWidth = 280.dp
private val HaloOuterHeight = 134.dp

/**
 * The one deliberate action in the whole app.
 *
 * Concentric halo rings at descending alpha pulse slowly behind it — a beacon, echoing
 * the proximity idea. When the system animation scale is zero the rings stay but stop
 * breathing; the affordance never depends on the motion.
 */
@Composable
fun CheckInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = haloPulse()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(HaloOuterWidth, HaloOuterHeight)
                .scale(pulse)
                .background(BukBlueHalo[0], MaterialTheme.shapes.extraLarge),
        )
        Box(
            Modifier
                .size(HaloInnerWidth, HaloInnerHeight)
                .scale(pulse)
                .background(BukBlueHalo[1], MaterialTheme.shapes.extraLarge),
        )
        Button(
            onClick = onClick,
            modifier = Modifier.size(ButtonWidth, ButtonHeight),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.checkin_button),
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}

/** 1f when motion is off, so the rings render at their resting size. */
@Composable
private fun haloPulse(): Float {
    if (!animationsEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "halo")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )
    return scale
}

@Preview(showBackground = true)
@Composable
private fun CheckInButtonPreview() {
    BukInTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(BukSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            CheckInButton(onClick = {})
        }
    }
}
