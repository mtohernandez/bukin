package com.buk.bukin.feature.checkin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukInFooter
import com.buk.bukin.designsystem.component.CheckInButton
import com.buk.bukin.designsystem.component.NoticeCard
import com.buk.bukin.designsystem.component.ProximityIllustration
import com.buk.bukin.designsystem.component.SuccessCheck
import com.buk.bukin.designsystem.component.TicketCard
import com.buk.bukin.designsystem.component.rememberDemoInstancia
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccess
import com.buk.bukin.domain.model.CheckInErrorReason
import com.buk.bukin.domain.model.CheckInState

@Composable
fun CheckInRoute(
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = viewModel(),
    showDebugStateControl: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckInScreen(
        state = state,
        onCheckIn = viewModel::onCheckIn,
        onRecover = viewModel::onRecover,
        modifier = modifier,
        onDebugAdvance = if (showDebugStateControl) viewModel::advanceForDebug else null,
    )
}

/**
 * The collaborator screen.
 *
 * The ticket header and the footer never change; only the centre swaps. That is the whole
 * point — a user glancing at this screen should be able to tell which state they are in
 * from across a room, because the loudest complaint about the app this replaces is that
 * nothing visibly changes and nobody knows whether their attendance registered.
 *
 * Stateless: it takes a [CheckInState] and emits events. There is no "buscar" button and
 * no pull-to-refresh; scanning is something the app does, not something a user starts.
 */
@Composable
fun CheckInScreen(
    state: CheckInState,
    onCheckIn: () -> Unit,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier,
    onDebugAdvance: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
    ) {
        Spacer(Modifier.height(BukSpacing.md))

        TicketCard(instancia = rememberDemoInstancia())

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                // Arriving at Success is the payoff, so it grows in rather than cross-fades.
                if (targetState is CheckInState.Success) {
                    (fadeIn(tween(320)) + scaleIn(tween(420), initialScale = 0.82f))
                        .togetherWith(fadeOut(tween(160)))
                } else {
                    fadeIn(tween(260)).togetherWith(fadeOut(tween(160)))
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "checkInState",
        ) { current ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (current) {
                    CheckInState.Scanning -> ScanningCentre()
                    CheckInState.Offline -> ScanningCentre(
                        notice = {
                            NoticeCard(
                                title = stringResource(R.string.checkin_offline_title),
                                body = stringResource(R.string.checkin_offline_body),
                            )
                        },
                    )
                    CheckInState.Ready -> CheckInButton(onClick = onCheckIn)
                    CheckInState.Success -> SuccessCentre()
                    is CheckInState.Error -> BlockedCentre(current.reason, onRecover)
                }
            }
        }

        if (onDebugAdvance != null) {
            TextButton(
                onClick = onDebugAdvance,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.debug_next_state),
                    style = MaterialTheme.typography.labelSmall,
                    color = BukInkMuted,
                )
            }
        }

        BukInFooter()
        Spacer(Modifier.height(BukSpacing.md))
    }
}

@Composable
private fun ScanningCentre(notice: @Composable (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        ProximityIllustration()
        Spacer(Modifier.height(BukSpacing.lg))
        // Rule: no spinner without a sentence. This is the sentence.
        Text(
            text = stringResource(R.string.checkin_scanning),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            // Held short so it breaks over two lines, as in the mockup, instead of
            // running edge to edge.
            modifier = Modifier.fillMaxWidth(0.72f),
        )
        Spacer(Modifier.weight(1f))
        if (notice != null) {
            notice()
            Spacer(Modifier.height(BukSpacing.md))
        }
    }
}

@Composable
private fun SuccessCentre() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SuccessCheck()
        Spacer(Modifier.height(BukSpacing.lg))
        Text(
            text = stringResource(R.string.checkin_success),
            style = MaterialTheme.typography.titleLarge,
            color = BukSuccess,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A blocked state names its cause and offers the action that unblocks it. There is no
 * dead disabled control and no dead end anywhere in this branch.
 */
@Composable
private fun BlockedCentre(reason: CheckInErrorReason, onRecover: () -> Unit) {
    val (title, body, action) = when (reason) {
        CheckInErrorReason.BLUETOOTH_OFF -> Triple(
            R.string.error_bluetooth_off_title,
            R.string.error_bluetooth_off_body,
            R.string.error_bluetooth_off_action,
        )
        CheckInErrorReason.HOST_NOT_FOUND -> Triple(
            R.string.error_no_host_title,
            R.string.error_no_host_body,
            R.string.error_no_host_action,
        )
        CheckInErrorReason.SAVE_FAILED -> Triple(
            R.string.error_save_failed_title,
            R.string.error_save_failed_body,
            R.string.error_save_failed_action,
        )
    }

    NoticeCard(
        title = stringResource(title),
        body = stringResource(body),
        actionLabel = stringResource(action),
        onAction = onRecover,
    )
}

@Preview(showBackground = true)
@Composable
private fun CheckInScanningPreview() = PreviewState(CheckInState.Scanning)

@Preview(showBackground = true)
@Composable
private fun CheckInOfflinePreview() = PreviewState(CheckInState.Offline)

@Preview(showBackground = true)
@Composable
private fun CheckInReadyPreview() = PreviewState(CheckInState.Ready)

@Preview(showBackground = true)
@Composable
private fun CheckInSuccessPreview() = PreviewState(CheckInState.Success)

@Preview(showBackground = true)
@Composable
private fun CheckInBlockedPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.BLUETOOTH_OFF))

@Composable
private fun PreviewState(state: CheckInState) {
    BukInTheme {
        CheckInScreen(state = state, onCheckIn = {}, onRecover = {})
    }
}
