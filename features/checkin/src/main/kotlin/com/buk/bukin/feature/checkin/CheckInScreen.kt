package com.buk.bukin.feature.checkin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.ble.BleCapability
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.BukSkeletonHost
import com.buk.bukin.designsystem.component.CheckInMorph
import com.buk.bukin.designsystem.component.MorphPhase
import com.buk.bukin.designsystem.component.NoticeCard
import com.buk.bukin.designsystem.component.NoticeSeverity
import com.buk.bukin.designsystem.component.ProximityIllustration
import com.buk.bukin.designsystem.component.TicketCard
import com.buk.bukin.designsystem.component.TicketCardSkeleton
import com.buk.bukin.designsystem.component.horaApertura
import com.buk.bukin.designsystem.component.rememberBukHaptics
import com.buk.bukin.designsystem.component.rememberZone
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccessInk
import com.buk.bukin.domain.model.CheckInErrorReason
import com.buk.bukin.domain.model.CheckInState
import com.buk.bukin.domain.model.Instancia

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
 * The ticket and the footer never change; only the centre does. That is the whole point — a
 * user glancing at this screen should be able to tell which state they are in from across a
 * room, because the loudest complaint about the app this replaces is that nothing visibly
 * changes and nobody knows whether their attendance registered.
 *
 * Two things follow from that complaint and are not optional:
 *
 * - **The centre is a polite live region.** SCANNING → READY → SUCCESS is *spoken*, not
 *   only drawn. A person who cannot see the check mark otherwise has no way at all to know
 *   the thing this product exists to tell them.
 * - **Ready, Enviando and Success are one composable**, [CheckInMorph], not three. Swapping
 *   composables at the payoff is what made the old screen jump-cut.
 *
 * Stateless: it takes a [CheckInState] and emits events. There is no "buscar" button and no
 * pull-to-refresh; scanning is something the app does, not something a user starts.
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
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberBukHaptics()

    // The server recomputed the code and refused it. This is the only failure the app
    // treats as worth feeling.
    LaunchedEffect(state) {
        if ((state as? CheckInState.Error)?.reason == CheckInErrorReason.CODE_REJECTED) {
            haptics.reject()
        }
    }

    BukScreen(
        modifier = modifier,
        onBack = onBack.takeIf { state !is CheckInState.Success },
        footer = true,
    ) {
        BukSkeletonHost {
            // A skeleton, not nothing. Rendering no ticket while the row was in flight is
            // what made the header land late and push the layout down.
            if (instancia == null) {
                TicketCardSkeleton()
            } else {
                TicketCard(instancia = instancia, onHelpClick = { showHelp = true })
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // An empty node carrying only the announcement. It is deliberately not the
            // container itself: putting a contentDescription on the centre would collapse
            // everything inside it into one node and silence the button's own label.
            StateAnnouncer(state)

            when (state) {
                CheckInState.Scanning -> ScanningCentre()

                // This clears itself when connectivity returns, but the action stays: the
                // platform only calls a network validated once it has proved it, and
                // nothing on this screen may be a dead end while it waits.
                CheckInState.Offline -> ScanningCentre(
                    notice = {
                        NoticeCard(
                            title = stringResource(R.string.checkin_offline_title),
                            body = stringResource(R.string.checkin_offline_body),
                            // A temporary condition that clears itself is not a failure,
                            // and should not look like one.
                            severity = NoticeSeverity.Informational,
                            actionLabel = stringResource(R.string.error_save_failed_action),
                            onAction = onRecover,
                        )
                    },
                )

                is CheckInState.EsperandoHora -> EsperandoCentre(state.fechaInicio)

                CheckInState.Ready, CheckInState.Enviando, CheckInState.Success ->
                    SuccessPath(state, onCheckIn)

                is CheckInState.Error ->
                    BlockedCentre(state.reason, onRecover, permissionPermanentlyDenied)
            }
        }
    }

    if (showHelp) {
        NecesitoAyudaSheet(state = state, onDismiss = { showHelp = false })
    }
}

/**
 * Speaks the state change.
 *
 * A blind user currently gets no notification that their attendance registered — which is
 * precisely *"you never know if your attendance was actually registered"*, with no visual
 * workaround available. A polite live region says it, once, as it happens.
 */
@Composable
private fun StateAnnouncer(state: CheckInState) {
    val text = when (state) {
        CheckInState.Scanning -> stringResource(R.string.checkin_announce_scanning)
        CheckInState.Ready -> stringResource(R.string.checkin_announce_ready)
        CheckInState.Enviando -> stringResource(R.string.checkin_announce_enviando)
        CheckInState.Success -> stringResource(R.string.checkin_announce_success)
        CheckInState.Offline -> stringResource(R.string.checkin_offline_title)
        is CheckInState.EsperandoHora -> stringResource(R.string.checkin_esperando_title)
        is CheckInState.Error -> stringResource(state.reason.titleRes())
    }
    Box(
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = text
        },
    )
}

/**
 * The three states that share one container.
 *
 * They are deliberately *not* separated by an `AnimatedContent`: the whole design of the
 * payoff is that the button does not get replaced by anything, it becomes the thing.
 */
@Composable
private fun SuccessPath(state: CheckInState, onCheckIn: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CheckInMorph(
            phase = when (state) {
                CheckInState.Enviando -> MorphPhase.Enviando
                CheckInState.Success -> MorphPhase.Success
                else -> MorphPhase.Ready
            },
            onCheckIn = onCheckIn,
        )

        if (state == CheckInState.Success) {
            Spacer(Modifier.height(BukSpacing.lg))
            Text(
                text = stringResource(R.string.checkin_success),
                style = MaterialTheme.typography.displaySmall,
                color = BukSuccessInk,
                textAlign = TextAlign.Center,
            )
        } else if (state == CheckInState.Enviando) {
            Spacer(Modifier.height(BukSpacing.lg))
            Text(
                text = stringResource(R.string.checkin_enviando),
                style = MaterialTheme.typography.titleLarge,
                color = BukInk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The cause, for the live region. Same words the card shows. */
private fun CheckInErrorReason.titleRes(): Int = when (this) {
    CheckInErrorReason.BLUETOOTH_OFF -> R.string.error_bluetooth_off_title
    CheckInErrorReason.BLUETOOTH_UNAVAILABLE -> R.string.error_no_bluetooth_title
    CheckInErrorReason.PERMISSION_DENIED -> R.string.error_permission_title
    CheckInErrorReason.SCAN_FAILED -> R.string.error_scan_failed_title
    CheckInErrorReason.HOST_NOT_FOUND -> R.string.error_no_host_title
    CheckInErrorReason.SAVE_FAILED -> R.string.error_save_failed_title
    CheckInErrorReason.CODE_REJECTED -> R.string.error_code_rejected_title
    CheckInErrorReason.CODE_REJECTED_REPETIDO -> R.string.error_code_mismatch_title
    CheckInErrorReason.OUT_OF_WINDOW -> R.string.error_out_of_window_title
}

/** Signed in, but the session's hour has not come. The radio is not even on yet. */
@Composable
private fun EsperandoCentre(fechaInicio: java.time.Instant) {
    val zone = rememberZone()
    val abre = remember(fechaInicio, zone) { horaApertura(fechaInicio, zone) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = BukSpacing.sm),
    ) {
        ProximityIllustration()
        Spacer(Modifier.height(BukSpacing.lg))
        NoticeCard(
            title = stringResource(R.string.checkin_esperando_title),
            body = stringResource(R.string.checkin_esperando_body, abre),
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
        // Rule: no spinner without a sentence. This is the sentence — and the arcs above
        // are its converse, the motion that stops a waiting screen looking like a hung one.
        Text(
            text = stringResource(R.string.checkin_scanning),
            style = MaterialTheme.typography.titleLarge,
            color = BukInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.82f),
        )
        Spacer(Modifier.weight(1f))
        if (notice != null) {
            notice()
            Spacer(Modifier.height(BukSpacing.md))
        }
    }
}

/**
 * A blocked state names its cause and offers the action that unblocks it. There is no dead
 * disabled control and no dead end anywhere in this branch.
 */
@Composable
private fun BlockedCentre(
    reason: CheckInErrorReason,
    onRecover: () -> Unit,
    permissionPermanentlyDenied: Boolean = false,
) {
    // A null action means there is genuinely nothing to offer — not a button that does
    // nothing. BLUETOOTH_UNAVAILABLE is the only reason that can honestly be actionless,
    // and it is also the only one that is truly blocking rather than a passing condition.
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
        CheckInErrorReason.CODE_REJECTED_REPETIDO -> Triple(
            R.string.error_code_mismatch_title,
            R.string.error_code_mismatch_body,
            R.string.error_code_mismatch_action,
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
        severity = if (reason == CheckInErrorReason.BLUETOOTH_UNAVAILABLE ||
            reason == CheckInErrorReason.CODE_REJECTED_REPETIDO
        ) {
            NoticeSeverity.Blocking
        } else {
            NoticeSeverity.Informational
        },
        actionLabel = action?.let { stringResource(it) },
        onAction = action?.let { { onRecover() } },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInScanningPreview() = PreviewState(CheckInState.Scanning)

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInOfflinePreview() = PreviewState(CheckInState.Offline)

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInReadyPreview() = PreviewState(CheckInState.Ready)

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInSuccessPreview() = PreviewState(CheckInState.Success)

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInBlockedPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.BLUETOOTH_OFF))

/** The one blocked state with no way out. Renders without an action button on purpose. */
@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInNoBluetoothPreview() =
    PreviewState(CheckInState.Error(CheckInErrorReason.BLUETOOTH_UNAVAILABLE))

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun CheckInEsperandoPreview() =
    PreviewState(CheckInState.EsperandoHora(java.time.Instant.parse("2026-07-13T15:00:00Z")))

/** The layout requirement most likely to break these screens. */
@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6, fontScale = 2.0f)
@Composable
private fun CheckInLargeTypePreview() = PreviewState(CheckInState.Scanning)

/** The narrowest phone the app claims to support. */
@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6, widthDp = 320, heightDp = 640)
@Composable
private fun CheckInNarrowPreview() = PreviewState(CheckInState.Ready)

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
