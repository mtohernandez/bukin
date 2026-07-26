package com.buk.bukin.feature.checkin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.buk.bukin.ble.BleCapability
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccess
import com.buk.bukin.domain.model.CheckInErrorReason
import com.buk.bukin.domain.model.CheckInState
import com.buk.bukin.domain.model.Instancia
import com.buk.bukin.designsystem.component.horaApertura
import com.buk.bukin.designsystem.component.rememberZone

@Composable
fun CheckInRoute(
    instanciaId: Int,
    colaboradorId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = viewModel(),
) {
    // Which session and which person come from the nav key. Binding here rather than
    // through a ViewModel factory keeps the default factory usable for one more session.
    LaunchedEffect(instanciaId, colaboradorId) {
        viewModel.bind(instanciaId, colaboradorId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val instancia by viewModel.instancia.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    // Tracks whether the dialog is still raisable. Once "no volver a preguntar" is checked
    // the launcher returns instantly with nothing shown, which would look like a dead
    // button — so past that point the recovery action goes to Settings instead.
    var permissionPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        permissionPermanentlyDenied = denied.any { permission ->
            activity?.shouldShowRequestPermissionRationale(permission) == false
        }
        viewModel.onRecover()
    }

    CheckInScreen(
        state = state,
        instancia = instancia,
        onCheckIn = viewModel::onCheckIn,
        onBack = onBack,
        onRecover = {
            // Each blocked state has its own way out. Routing here rather than in the
            // ViewModel because every one of them is an Android intent or a permission
            // dialog, neither of which belongs in a state holder.
            when ((state as? CheckInState.Error)?.reason) {
                CheckInErrorReason.BLUETOOTH_OFF ->
                    // Settings rather than ACTION_REQUEST_ENABLE: that dialog needs
                    // BLUETOOTH_CONNECT, which a collaborator is never asked for.
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

                CheckInErrorReason.PERMISSION_DENIED ->
                    if (permissionPermanentlyDenied) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    } else {
                        permissionLauncher.launch(
                            BleCapability.requiredPermissions(forHost = false).toTypedArray(),
                        )
                    }

                // The server says the hour has passed. Nothing to retry here; the way
                // out is back to the list to pick a session that is actually open.
                CheckInErrorReason.OUT_OF_WINDOW -> onBack()

                else -> viewModel.onRecover()
            }
        },
        permissionPermanentlyDenied = permissionPermanentlyDenied,
        modifier = modifier,
    )
}

/** `LocalContext` is a ContextWrapper inside a dialog; unwrap to reach the Activity. */
private fun Context.findActivity(): Activity? = generateSequence(this) {
    (it as? ContextWrapper)?.baseContext
}.filterIsInstance<Activity>().firstOrNull()

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
    instancia: Instancia?,
    onCheckIn: () -> Unit,
    onBack: () -> Unit,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier,
    permissionPermanentlyDenied: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
    ) {
        Spacer(Modifier.height(BukSpacing.md))

        // Null only for the moment between opening the screen and the row arriving.
        // Rendering an empty ticket would be a worse lie than rendering none.
        instancia?.let { TicketCard(instancia = it) }

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
                    // This state clears itself when connectivity returns, but the action
                    // stays: the platform only calls a network validated once it has proved
                    // it, and nothing on this screen may be a dead end while it waits.
                    CheckInState.Offline -> ScanningCentre(
                        notice = {
                            NoticeCard(
                                title = stringResource(R.string.checkin_offline_title),
                                body = stringResource(R.string.checkin_offline_body),
                                actionLabel = stringResource(R.string.error_save_failed_action),
                                onAction = onRecover,
                            )
                        },
                    )
                    is CheckInState.EsperandoHora -> EsperandoCentre(current.fechaInicio)
                    CheckInState.Ready -> CheckInButton(onClick = onCheckIn)
                    CheckInState.Enviando -> EnviandoCentre()
                    CheckInState.Success -> SuccessCentre()
                    is CheckInState.Error ->
                        BlockedCentre(current.reason, onRecover, permissionPermanentlyDenied)
                }
            }
        }

        // Check-in used to be a dead end. With a session list in front of it, leaving to
        // pick a different session has to be possible.
        if (state !is CheckInState.Success) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.host_back), color = BukInkMuted)
            }
        }

        BukInFooter()
        Spacer(Modifier.height(BukSpacing.md))
    }
}

/** Signed in, but the session's hour has not come. The radio is not even on yet. */
@Composable
private fun EsperandoCentre(fechaInicio: java.time.Instant) {
    val zone = rememberZone()
    val abre = remember(fechaInicio, zone) { horaApertura(fechaInicio, zone) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = BukSpacing.md),
    ) {
        ProximityIllustration()
        Spacer(Modifier.height(BukSpacing.lg))
        NoticeCard(
            title = stringResource(R.string.checkin_esperando_title),
            body = stringResource(R.string.checkin_esperando_body, abre),
        )
    }
}

/** In flight. The button is gone, so a second tap has nothing to hit. */
@Composable
private fun EnviandoCentre() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProximityIllustration()
        Spacer(Modifier.height(BukSpacing.lg))
        Text(
            text = stringResource(R.string.checkin_enviando),
            style = MaterialTheme.typography.bodyLarge,
            color = BukInkMuted,
            textAlign = TextAlign.Center,
        )
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
private fun BlockedCentre(
    reason: CheckInErrorReason,
    onRecover: () -> Unit,
    permissionPermanentlyDenied: Boolean = false,
) {
    // A null action means there is genuinely nothing to offer — not a button that does
    // nothing. BLUETOOTH_UNAVAILABLE is the only reason that can honestly be actionless.
    val (title, body, action) = when (reason) {
        CheckInErrorReason.BLUETOOTH_OFF -> Triple(
            R.string.error_bluetooth_off_title,
            R.string.error_bluetooth_off_body,
            R.string.error_bluetooth_off_action,
        )
        CheckInErrorReason.BLUETOOTH_UNAVAILABLE -> Triple(
            R.string.error_no_bluetooth_title,
            R.string.error_no_bluetooth_body,
            null,
        )
        CheckInErrorReason.PERMISSION_DENIED -> Triple(
            R.string.error_permission_title,
            R.string.error_permission_body,
            if (permissionPermanentlyDenied) {
                R.string.error_permission_settings_action
            } else {
                R.string.error_permission_action
            },
        )
        CheckInErrorReason.SCAN_FAILED -> Triple(
            R.string.error_scan_failed_title,
            R.string.error_scan_failed_body,
            R.string.error_scan_failed_action,
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
        // The server recomputed the code and refused it. Named as a stale code rather than
        // as an attack, because that is what it almost always is — and telling an honest
        // user they look like a fraud is worse than telling them to look again.
        CheckInErrorReason.CODE_REJECTED -> Triple(
            R.string.error_code_rejected_title,
            R.string.error_code_rejected_body,
            R.string.error_code_rejected_action,
        )
        CheckInErrorReason.OUT_OF_WINDOW -> Triple(
            R.string.error_out_of_window_title,
            R.string.error_out_of_window_body,
            R.string.error_out_of_window_action,
        )
    }

    NoticeCard(
        title = stringResource(title),
        body = stringResource(body),
        actionLabel = action?.let { stringResource(it) },
        onAction = action?.let { { onRecover() } },
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

@Preview(showBackground = true)
@Composable
private fun CheckInPermissionPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.PERMISSION_DENIED))

/** The one blocked state with no way out. Renders without an action button on purpose. */
@Preview(showBackground = true)
@Composable
private fun CheckInNoBluetoothPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.BLUETOOTH_UNAVAILABLE))

@Preview(showBackground = true)
@Composable
private fun CheckInScanFailedPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.SCAN_FAILED))

@Preview(showBackground = true)
@Composable
private fun CheckInEnviandoPreview() = PreviewState(CheckInState.Enviando)

@Preview(showBackground = true)
@Composable
private fun CheckInEsperandoPreview() =
    PreviewState(CheckInState.EsperandoHora(java.time.Instant.parse("2026-07-13T15:00:00Z")))

@Preview(showBackground = true)
@Composable
private fun CheckInCodeRejectedPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.CODE_REJECTED))

@Composable
private fun PreviewState(state: CheckInState) {
    BukInTheme {
        CheckInScreen(
            state = state,
            instancia = Instancia(
                id = 1,
                cursoNombre = "Manejo de alimentos",
                duracionMinutos = 120,
                fechaInicio = java.time.Instant.parse("2026-07-13T15:00:00Z"),
                fechaFin = java.time.Instant.parse("2026-07-13T17:00:00Z"),
                activa = true,
                abierta = true,
                inscrito = true,
                asistencia = false,
            ),
            onCheckIn = {},
            onBack = {},
            onRecover = {},
        )
    }
}
