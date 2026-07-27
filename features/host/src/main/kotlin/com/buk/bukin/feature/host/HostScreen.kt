package com.buk.bukin.feature.host

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.ble.BleCapability
import com.buk.bukin.ble.BleStatus
import com.buk.bukin.ble.HostState
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukMinTouchTarget
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.NoticeCard
import com.buk.bukin.designsystem.component.NoticeSeverity
import com.buk.bukin.designsystem.component.bukPressable
import com.buk.bukin.designsystem.component.instanciaSubtitulo
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSurface
import kotlinx.coroutines.delay

@Composable
fun HostRoute(
    instanciaId: Int,
    onBack: () -> Unit,
    onOpenRoster: () -> Unit,
    onOpenManual: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HostViewModel = viewModel(),
) {
    LaunchedEffect(instanciaId) { viewModel.bind(instanciaId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    val notificationTitle = stringResource(R.string.host_notification_title)
    val notificationText = stringResource(R.string.host_notification_text)

    // Only meaningful after a request has actually been made: shouldShowRequestPermission-
    // Rationale is false both for "permanently denied" and for "never asked", so testing it
    // before the first request would send a brand-new host to Settings instead of showing
    // them the dialog. Set here, in the result callback, and nowhere else.
    var permissionPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        permissionPermanentlyDenied = denied.any {
            activity?.shouldShowRequestPermissionRationale(it) == false
        }
        viewModel.onRecover()
    }

    HostScreen(
        uiState = uiState,
        onStart = { viewModel.start(notificationTitle, notificationText) },
        onStop = viewModel::stop,
        onBack = onBack,
        onOpenRoster = onOpenRoster,
        onOpenManual = onOpenManual,
        onRecover = {
            when (uiState.status) {
                BleStatus.Disabled ->
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

                is BleStatus.PermissionsMissing ->
                    if (permissionPermanentlyDenied) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    } else {
                        permissionLauncher.launch(
                            BleCapability.requiredPermissions(forHost = true).toTypedArray(),
                        )
                    }

                else -> viewModel.onRecover()
            }
        },
        modifier = modifier,
    )
}

/**
 * The operator surface.
 *
 * Plainer than the collaborator screen on purpose — a host wants the code and the state
 * legible from arm's length, not beautiful. It still uses the same scaffold, the same type
 * scale and the same tokens, because "plain" was previously being achieved by rendering
 * half its type in undeclared Material defaults, which is a different thing entirely.
 */
@Composable
fun HostScreen(
    uiState: HostUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenRoster: () -> Unit,
    onOpenManual: () -> Unit,
    onRecover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BukScreen(
        modifier = modifier,
        title = uiState.instancia?.cursoNombre ?: stringResource(R.string.host_title),
        onBack = onBack,
    ) {
        uiState.instancia?.let {
            Text(
                text = instanciaSubtitulo(it),
                style = MaterialTheme.typography.bodySmall,
                color = BukInkMuted,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val blocked = uiState.status.blockedCopy()
            if (blocked != null) {
                NoticeCard(
                    title = stringResource(blocked.first),
                    body = stringResource(blocked.second),
                    // CannotAdvertise has no way out: this chipset will never broadcast.
                    severity = if (blocked.third == null) {
                        NoticeSeverity.Blocking
                    } else {
                        NoticeSeverity.Informational
                    },
                    actionLabel = blocked.third?.let { stringResource(it) },
                    onAction = blocked.third?.let { { onRecover() } },
                )
            } else if (uiState.errorDeRed) {
                // The key never reached the server, so no code this phone emits could ever
                // verify. Saying the room is open would be a lie the host cannot see through.
                NoticeCard(
                    title = stringResource(R.string.sesiones_error),
                    body = stringResource(R.string.error_save_failed_body),
                    actionLabel = stringResource(R.string.sesiones_retry),
                    onAction = onStart,
                )
            } else {
                SessionCentre(uiState, onStart, onStop)
            }
        }

        // Useful before the room opens too — a host checking who is enrolled, or
        // registering someone who arrived early.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BukSpacing.sm2),
        ) {
            SecondaryAction(stringResource(R.string.roster_open), onOpenRoster, Modifier.weight(1f))
            SecondaryAction(stringResource(R.string.manual_open), onOpenManual, Modifier.weight(1f))
        }
        Spacer(Modifier.height(BukSpacing.md))
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = BukMinTouchTarget)
            .clip(BukShape.lg)
            .background(BukSurface)
            .bukPressable(onClick = onClick, onClickLabel = label)
            .padding(horizontal = BukSpacing.md, vertical = BukSpacing.sm2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = BukBlue,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = BukMinTouchTarget)
            .clip(BukShape.lg)
            .background(BukBlue)
            .bukPressable(onClick = onClick, onClickLabel = label)
            .padding(horizontal = BukSpacing.lg, vertical = BukSpacing.sm2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun SessionCentre(uiState: HostUiState, onStart: () -> Unit, onStop: () -> Unit) {
    when (val session = uiState.session) {
        HostState.Stopped -> {
            Text(
                text = stringResource(R.string.host_idle_body),
                style = MaterialTheme.typography.bodyLarge,
                color = BukInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BukSpacing.lg))
            PrimaryAction(stringResource(R.string.host_start), onStart)
        }

        HostState.Starting -> Text(
            text = stringResource(R.string.host_starting),
            style = MaterialTheme.typography.titleMedium,
            color = BukInk,
        )

        is HostState.Broadcasting -> {
            Text(
                text = stringResource(R.string.host_broadcasting),
                style = MaterialTheme.typography.titleLarge,
                color = BukInk,
            )
            Spacer(Modifier.height(BukSpacing.sm))

            // A format argument, not `label + ": $id"` assembled in Kotlin. That built a
            // sentence in code, which code-standards.md forbids for exactly this reason.
            Text(
                text = stringResource(R.string.host_instancia_valor, session.instanciaId),
                style = MaterialTheme.typography.bodySmall,
                color = BukInkMuted,
            )

            Spacer(Modifier.height(BukSpacing.lg))
            Text(
                text = stringResource(R.string.host_code_label),
                style = MaterialTheme.typography.labelMedium,
                color = BukInkMuted,
            )
            // The whole point of this screen on demo day: this string must equal what
            // scan.swift prints on the Mac.
            Text(
                text = session.code.toHex(),
                style = MaterialTheme.typography.headlineSmall,
                color = BukBlue,
            )
            Spacer(Modifier.height(BukSpacing.sm))

            Text(
                text = stringResource(
                    R.string.host_window_label,
                    rememberCountdown(session.windowEndsAtEpochMillis),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )
            Spacer(Modifier.height(BukSpacing.lg))

            // A host clock more than a window out generates codes rejected 100% of the
            // time. The offset is applied automatically; this says so when it is large
            // enough that someone should go and fix the phone's clock setting.
            if (uiState.relojDesfasado()) {
                Text(
                    text = stringResource(R.string.diag_clock_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(BukSpacing.sm))
            }

            Text(
                text = stringResource(R.string.host_keep_open),
                style = MaterialTheme.typography.bodySmall,
                color = BukInkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(BukSpacing.md))
            SecondaryAction(stringResource(R.string.host_stop), onStop)
        }

        is HostState.Failed -> {
            NoticeCard(
                title = stringResource(R.string.error_advertise_failed_title),
                body = stringResource(R.string.error_advertise_failed_body, session.errorCode),
                actionLabel = stringResource(R.string.host_start),
                onAction = onStart,
            )
        }
    }
}

/** Seconds until the code rotates. Ticks once a second; nothing here needs finer. */
@Composable
private fun rememberCountdown(windowEndsAtEpochMillis: Long): Int {
    var remaining by remember(windowEndsAtEpochMillis) {
        mutableLongStateOf(windowEndsAtEpochMillis - System.currentTimeMillis())
    }
    LaunchedEffect(windowEndsAtEpochMillis) {
        while (remaining > 0) {
            delay(1_000)
            remaining = windowEndsAtEpochMillis - System.currentTimeMillis()
        }
    }
    return ((remaining + 999) / 1000).coerceAtLeast(0).toInt()
}

/** Title, body, and an optional action — null where there is genuinely nothing to offer. */
private fun BleStatus.blockedCopy(): Triple<Int, Int, Int?>? = when (this) {
    BleStatus.Ready -> null
    BleStatus.NoAdapter -> Triple(
        R.string.error_no_bluetooth_title,
        R.string.error_no_bluetooth_body,
        null,
    )
    BleStatus.Disabled -> Triple(
        R.string.error_bluetooth_off_title,
        R.string.error_bluetooth_off_body,
        R.string.error_bluetooth_off_action,
    )
    is BleStatus.PermissionsMissing -> Triple(
        R.string.error_permission_title,
        R.string.error_permission_body,
        R.string.error_permission_action,
    )
    BleStatus.CannotAdvertise -> Triple(
        R.string.error_cannot_advertise_title,
        R.string.error_cannot_advertise_body,
        null,
    )
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun Context.findActivity(): Activity? = generateSequence(this) {
    (it as? ContextWrapper)?.baseContext
}.filterIsInstance<Activity>().firstOrNull()

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun HostIdlePreview() = PreviewHost(HostUiState())

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun HostBroadcastingPreview() = PreviewHost(
    HostUiState(
        session = HostState.Broadcasting(
            instanciaId = 42,
            code = byteArrayOf(0x67, -0x17, 0x4b, -0x08, -0x60, -0x77, 0x59, -0x16),
            counter = 58_000_000,
            windowEndsAtEpochMillis = System.currentTimeMillis() + 12_000,
        ),
    ),
)

/** The one host failure a user cannot fix. Renders without an action button on purpose. */
@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun HostCannotAdvertisePreview() =
    PreviewHost(HostUiState(status = BleStatus.CannotAdvertise))

@Composable
private fun PreviewHost(uiState: HostUiState) {
    BukInTheme {
        HostScreen(
            uiState = uiState,
            onStart = {},
            onStop = {},
            onBack = {},
            onOpenRoster = {},
            onOpenManual = {},
            onRecover = {},
        )
    }
}
