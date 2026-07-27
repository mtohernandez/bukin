package com.buk.bukin.feature.checkin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.rememberBukHaptics
import com.buk.bukin.designsystem.component.horaApertura
import com.buk.bukin.designsystem.component.rememberZone
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSurface
import com.buk.bukin.domain.model.CheckInErrorReason
import com.buk.bukin.domain.model.CheckInState

/**
 * "Necesito ayuda", which until this session did nothing at all.
 *
 * `TicketCard` declared `onHelpClick: () -> Unit = {}` and `CheckInScreen` called
 * `TicketCard(instancia = it)`, taking the default — so the control had been inert on every
 * screen since session 1.
 *
 * The sheet is **contextual on [CheckInState]**, because what it can usefully offer while
 * scanning is not what it can offer after the server refused a stale code. Every branch
 * ends at the same last resort: *pídele a tu anfitrión que te registre a mano* — which is a
 * real path that exists in the app, not a platitude. That is what closes the "never leave
 * the user alone" requirement: every dead end now terminates somewhere real.
 *
 * Copy rule throughout: **name the cause, then the fix, then the action.** Never a raw
 * error code, never blame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NecesitoAyudaSheet(
    state: CheckInState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val haptics = rememberBukHaptics()

    LaunchedEffect(Unit) { haptics.sheetOpened() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = BukSurface,
        shape = BukShape.xxl,
    ) {
        AyudaContent(state)
        Spacer(Modifier.height(BukSpacing.xl))
    }
}

@Composable
private fun AyudaContent(state: CheckInState) {
    val zone = rememberZone()
    val abre = (state as? CheckInState.EsperandoHora)?.let { horaApertura(it.fechaInicio, zone) }

    val pasa: String = when (state) {
        is CheckInState.EsperandoHora -> stringResource(R.string.ayuda_esperando_pasa)
        CheckInState.Scanning -> stringResource(R.string.ayuda_scanning_pasa)
        CheckInState.Offline -> stringResource(R.string.ayuda_offline_pasa)
        CheckInState.Ready -> stringResource(R.string.ayuda_ready_pasa)
        CheckInState.Enviando -> stringResource(R.string.ayuda_enviando_pasa)
        CheckInState.Success -> stringResource(R.string.ayuda_success_pasa)
        // The cause, named in the same words the card on screen uses. Cause first, then
        // the fix below — the same shape as every error message in the app.
        is CheckInState.Error -> stringResource(state.reason.titleRes())
    }

    val tips: List<String> = when (state) {
        is CheckInState.EsperandoHora -> listOf(
            stringResource(R.string.ayuda_esperando_tip_1, abre.orEmpty()),
            stringResource(R.string.ayuda_esperando_tip_2),
        )
        CheckInState.Scanning -> listOf(
            stringResource(R.string.ayuda_scanning_tip_1),
            stringResource(R.string.ayuda_scanning_tip_2),
            stringResource(R.string.ayuda_scanning_tip_3),
        )
        CheckInState.Offline -> listOf(
            stringResource(R.string.ayuda_offline_tip_1),
            stringResource(R.string.ayuda_offline_tip_2),
        )
        CheckInState.Ready -> listOf(stringResource(R.string.ayuda_ready_tip_1))
        CheckInState.Enviando -> listOf(stringResource(R.string.ayuda_enviando_tip_1))
        CheckInState.Success -> listOf(stringResource(R.string.ayuda_success_tip_1))
        is CheckInState.Error -> listOfNotNull(
            stringResource(state.reason.bodyRes()),
            state.reason.actionRes()?.let { stringResource(it) },
        )
    }

    Column(Modifier.padding(horizontal = BukSpacing.lg)) {
        Text(
            text = stringResource(R.string.ayuda_title),
            style = MaterialTheme.typography.headlineSmall,
            color = BukInk,
        )

        Spacer(Modifier.height(BukSpacing.lg))
        SectionLabel(stringResource(R.string.ayuda_que_pasa))
        Spacer(Modifier.height(BukSpacing.sm))
        Text(
            text = pasa,
            style = MaterialTheme.typography.bodyLarge,
            color = BukInk,
        )

        Spacer(Modifier.height(BukSpacing.lg))
        SectionLabel(stringResource(R.string.ayuda_que_hacer))
        Spacer(Modifier.height(BukSpacing.sm))
        tips.forEach { tip ->
            Bullet(tip)
            Spacer(Modifier.height(BukSpacing.sm))
        }

        // Success is the one state with nothing to fix, so it does not get told to go and
        // ask someone for help it does not need.
        if (state !is CheckInState.Success) {
            Spacer(Modifier.height(BukSpacing.sm))
            Text(
                text = stringResource(R.string.ayuda_ultimo_recurso),
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BukBlue,
    )
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .padding(top = BukSpacing.sm)
                .size(BukSpacing.xs2)
                .clip(BukShape.full)
                .background(BukBlue),
        )
        Spacer(Modifier.width(BukSpacing.sm2))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = BukInk,
        )
    }
}

/** The cause. */
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

/** The explanation, which in this app always contains the fix. */
private fun CheckInErrorReason.bodyRes(): Int = when (this) {
    CheckInErrorReason.BLUETOOTH_OFF -> R.string.error_bluetooth_off_body
    CheckInErrorReason.BLUETOOTH_UNAVAILABLE -> R.string.error_no_bluetooth_body
    CheckInErrorReason.PERMISSION_DENIED -> R.string.error_permission_body
    CheckInErrorReason.SCAN_FAILED -> R.string.error_scan_failed_body
    CheckInErrorReason.HOST_NOT_FOUND -> R.string.error_no_host_body
    CheckInErrorReason.SAVE_FAILED -> R.string.error_save_failed_body
    CheckInErrorReason.CODE_REJECTED -> R.string.error_code_rejected_body
    CheckInErrorReason.CODE_REJECTED_REPETIDO -> R.string.error_code_mismatch_body
    CheckInErrorReason.OUT_OF_WINDOW -> R.string.error_out_of_window_body
}

/**
 * The button the blocked card offers, repeated as a tip so the sheet names the same action.
 *
 * Null where there is genuinely nothing to press — a phone with no Bluetooth radio — which
 * is exactly the case that falls through to the last resort at the bottom of the sheet.
 */
private fun CheckInErrorReason.actionRes(): Int? = when (this) {
    CheckInErrorReason.BLUETOOTH_OFF -> R.string.error_bluetooth_off_action
    CheckInErrorReason.BLUETOOTH_UNAVAILABLE -> null
    CheckInErrorReason.PERMISSION_DENIED -> R.string.error_permission_action
    CheckInErrorReason.SCAN_FAILED -> R.string.error_scan_failed_action
    CheckInErrorReason.HOST_NOT_FOUND -> R.string.error_no_host_action
    CheckInErrorReason.SAVE_FAILED -> R.string.error_save_failed_action
    CheckInErrorReason.CODE_REJECTED -> R.string.error_code_rejected_action
    // No tip of its own: this one goes straight to the last resort at the foot of the sheet.
    CheckInErrorReason.CODE_REJECTED_REPETIDO -> null
    CheckInErrorReason.OUT_OF_WINDOW -> R.string.error_out_of_window_action
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AyudaScanningPreview() {
    BukInTheme { Column(Modifier.padding(vertical = BukSpacing.lg)) { AyudaContent(CheckInState.Scanning) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AyudaOfflinePreview() {
    BukInTheme { Column(Modifier.padding(vertical = BukSpacing.lg)) { AyudaContent(CheckInState.Offline) } }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AyudaCodeRejectedPreview() {
    BukInTheme {
        Column(Modifier.padding(vertical = BukSpacing.lg)) {
            AyudaContent(CheckInState.Error(CheckInErrorReason.CODE_REJECTED))
        }
    }
}
