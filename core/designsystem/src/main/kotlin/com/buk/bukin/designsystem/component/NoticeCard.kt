package com.buk.bukin.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukBorder
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukStroke
import com.buk.bukin.designsystem.theme.BukSurface

/**
 * How serious this is.
 *
 * "Sin conexión" is a temporary condition that clears itself the moment the network comes
 * back; "este teléfono no tiene Bluetooth" is a wall. Rendering both as the same neutral
 * outlined card made them look like the same kind of object, which is how a passing
 * condition ends up reading as a failure.
 */
enum class NoticeSeverity {
    /** A condition, not a fault. Brand-tinted edge. */
    Informational,

    /** Progress is stopped until something changes. Carries the app's error colour. */
    Blocking,
}

/**
 * The card a screen uses to explain a condition.
 *
 * [actionLabel] is not optional decoration: a state that blocks progress must name its
 * cause **and** offer the way out. When there is genuinely nothing to offer — a phone with
 * no Bluetooth radio — the card carries no button rather than a button that does nothing.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    severity: NoticeSeverity = NoticeSeverity.Informational,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val edge = when (severity) {
        NoticeSeverity.Informational -> BukBlue.copy(alpha = 0.28f)
        NoticeSeverity.Blocking -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BukShape.xl)
            .background(BukSurface)
            .border(BorderStroke(BukStroke.emphasis, edge), BukShape.xl)
            .padding(BukSpacing.md2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BukSpacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = BukInkMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(BukSpacing.md))
            Box(
                modifier = Modifier
                    .heightIn(min = BukMinTouchTarget)
                    .clip(BukShape.lg)
                    .background(BukBlue)
                    .bukPressable(onClick = onAction, onClickLabel = actionLabel)
                    .padding(horizontal = BukSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun NoticeCardPreview() {
    BukInTheme {
        Column(
            Modifier
                .background(BukField)
                .padding(BukSpacing.md2),
        ) {
            NoticeCard(
                title = stringResource(R.string.checkin_offline_title),
                body = stringResource(R.string.checkin_offline_body),
                actionLabel = stringResource(R.string.error_save_failed_action),
                onAction = {},
            )
            Spacer(Modifier.height(BukSpacing.md))
            NoticeCard(
                title = stringResource(R.string.error_no_bluetooth_title),
                body = stringResource(R.string.error_no_bluetooth_body),
                severity = NoticeSeverity.Blocking,
            )
        }
    }
}
