package com.buk.bukin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukMinTouchTarget
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.bukPressable
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSurface

/**
 * Which side of the room are you on.
 *
 * Deliberately quiet: it is a fork in the road, not a destination, and it must not compete
 * with the check-in screen for attention.
 *
 * **It is a known cut, not an oversight.** This screen gates every launch — `MainActivity`
 * sends every returning user here — so a collaborator answers "¿Cómo entras hoy?" every
 * day, forever, about a distinction that exists for the app's benefit rather than theirs.
 * Almost none of the 300 collaborators will ever host. It stays for the demo because role
 * switching happens constantly while demonstrating, and a two-tap path through the profile
 * menu costs more on demo day than the daily tax costs a user who does not exist yet.
 *
 * Upgrade path when this stops being a demo: remember the role after the first answer, open
 * straight into the session list, and move host mode behind the avatar menu. That is a
 * change to `MainActivity.startKey` and one menu entry — the navigation graph already
 * supports it, since `SessionPicker` takes `isHost` as a parameter.
 */
@Composable
fun RolePickerScreen(
    onCollaborator: () -> Unit,
    onHost: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BukScreen(modifier = modifier, footer = true, footerMicrocopy = false) {
        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.role_title),
            style = MaterialTheme.typography.headlineLarge,
            color = BukInk,
        )

        Spacer(Modifier.height(BukSpacing.xl))

        RoleCard(
            title = stringResource(R.string.role_collaborator),
            hint = stringResource(R.string.role_collaborator_hint),
            onClick = onCollaborator,
        )
        Spacer(Modifier.height(BukSpacing.sm2))
        RoleCard(
            title = stringResource(R.string.role_host),
            hint = stringResource(R.string.role_host_hint),
            onClick = onHost,
        )

        Spacer(Modifier.weight(1f))

        // Quiet on purpose. It is a service door, not a third role — but on demo day it is
        // the difference between "it doesn't work" and knowing which of five things failed.
        val diag = stringResource(R.string.diag_open)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = BukMinTouchTarget)
                .clip(BukShape.full)
                .bukPressable(onClick = onDiagnostics, onClickLabel = diag)
                .padding(horizontal = BukSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = diag,
                style = MaterialTheme.typography.labelSmall,
                color = BukInkMuted,
            )
        }
    }
}

@Composable
private fun RoleCard(title: String, hint: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BukShape.xl)
            .background(BukSurface)
            .bukPressable(onClick = onClick, ripple = true, onClickLabel = title)
            .padding(BukSpacing.md2),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = BukInk)
        Spacer(Modifier.height(BukSpacing.xs))
        Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = BukInkMuted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun RolePickerPreview() {
    BukInTheme { RolePickerScreen(onCollaborator = {}, onHost = {}, onDiagnostics = {}) }
}
