package com.buk.bukin.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBorder
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * The outlined card the collaborator screen uses to explain a condition — offline, or a
 * blocked state.
 *
 * [actionLabel] is not optional decoration: a state that blocks progress must name its
 * cause *and* offer the way out. When there is nothing to do about it, as with being
 * offline, the card is informational and carries no button.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, BukBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BukSpacing.md, vertical = BukSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
                Spacer(Modifier.height(BukSpacing.xs))
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NoticeCardPreview() {
    BukInTheme {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(BukSpacing.gutter),
        ) {
            NoticeCard(
                title = stringResource(R.string.checkin_offline_title),
                body = stringResource(R.string.checkin_offline_body),
            )
            Spacer(Modifier.height(BukSpacing.md))
            NoticeCard(
                title = stringResource(R.string.error_bluetooth_off_title),
                body = stringResource(R.string.error_bluetooth_off_body),
                actionLabel = stringResource(R.string.error_bluetooth_off_action),
                onAction = {},
            )
        }
    }
}
