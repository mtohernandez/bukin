package com.buk.bukin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukInFooter
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * Which side of the room are you on.
 *
 * Deliberately quiet: it is a fork in the road, not a destination, and it must not compete
 * with the check-in screen for attention. Two cards, no illustration, no brand furniture
 * beyond the footer.
 */
@Composable
fun RolePickerScreen(
    onCollaborator: () -> Unit,
    onHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.role_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(BukSpacing.xl))

        RoleCard(
            title = stringResource(R.string.role_collaborator),
            hint = stringResource(R.string.role_collaborator_hint),
            onClick = onCollaborator,
        )
        Spacer(Modifier.height(BukSpacing.md))
        RoleCard(
            title = stringResource(R.string.role_host),
            hint = stringResource(R.string.role_host_hint),
            onClick = onHost,
        )

        Spacer(Modifier.weight(1f))
        BukInFooter()
        Spacer(Modifier.height(BukSpacing.md))
    }
}

@Composable
private fun RoleCard(title: String, hint: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(BukSpacing.md)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(BukSpacing.xs))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RolePickerPreview() {
    BukInTheme { RolePickerScreen(onCollaborator = {}, onHost = {}) }
}
