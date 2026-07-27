package com.buk.bukin.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * A **leading** back affordance and a title.
 *
 * The screens this replaces each ended with a centred `TextButton` reading *Volver*, below
 * the content and above the footer. That is not an Android pattern — it is where a web page
 * puts it — and it meant the back control was the last thing in the reading order on every
 * screen that had one, and off the bottom of the screen at 200% font scale.
 *
 * Predictive back is handled once, by the navigation display, rather than per screen.
 */
@Composable
fun BukTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val label = stringResource(R.string.nav_back)
            Box(
                modifier = Modifier
                    .size(BukMinTouchTarget)
                    .clip(BukShape.full)
                    .bukPressable(onClick = onBack, onClickLabel = label),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BukIcons.ArrowBack,
                    contentDescription = label,
                    tint = BukInk,
                    modifier = Modifier.size(BukSpacing.lg),
                )
            }
            Spacer(Modifier.width(BukSpacing.sm))
        }

        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = BukInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        actions()
    }
}

private val BarHeight = 64.dp
