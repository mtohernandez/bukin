package com.buk.bukin.diagnostics

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.ble.BleCapability
import com.buk.bukin.ble.BleScanner
import com.buk.bukin.ble.BleStatus
import com.buk.bukin.ble.HostSession
import com.buk.bukin.domain.crypto.RotatingCode
import com.buk.bukin.ble.HostState
import com.buk.bukin.ble.ScanEvent
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the radio actually sees, in one screen.
 *
 * This exists because on demo day "it doesn't work" is not a diagnosis. Adapter state,
 * every permission by name, whether this chipset can broadcast at all, and a live list of
 * what is on the air — enough to tell a dead radio from a missing permission from an empty
 * room without a laptop and a logcat.
 */
class BleDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter

    private val _sightings = MutableStateFlow<List<ScanEvent>>(emptyList())
    val sightings: StateFlow<List<ScanEvent>> = _sightings.asStateFlow()

    val status: StateFlow<BleStatus> = BleCapability.statusFlow(application, forHost = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BleStatus.Ready)

    val hostState: StateFlow<HostState> = HostSession.state

    /** `server_now - device_now` from the last opened room. Zero until one is opened. */
    val clockOffsetSeconds: StateFlow<Long> = HostSession.clockOffsetSeconds

    val hasAdapter: Boolean = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true
    val canAdvertise: Boolean get() = adapter?.isMultipleAdvertisementSupported == true

    fun permissionReport(): List<Pair<String, Boolean>> =
        BleCapability.requiredPermissions(forHost = true).map { permission ->
            permission.substringAfterLast('.') to
                (ContextCompat.checkSelfPermission(getApplication(), permission)
                    == PackageManager.PERMISSION_GRANTED)
        }

    init {
        viewModelScope.launch {
            BleScanner.scan(getApplication()).collect { event ->
                // Newest first, capped — this is a diagnostic tail, not a log file.
                _sightings.update { (listOf(event) + it).take(MAX_ROWS) }
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 30
    }
}

@Composable
fun BleDiagnosticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BleDiagnosticsViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()
    val clockOffset by viewModel.clockOffsetSeconds.collectAsStateWithLifecycle()
    val sightings by viewModel.sightings.collectAsStateWithLifecycle()

    BukScreen(
        modifier = modifier,
        title = stringResource(R.string.diag_title),
        onBack = onBack,
    ) {
        DiagRow(stringResource(R.string.diag_adapter), viewModel.hasAdapter.yesNo())
        DiagRow(stringResource(R.string.diag_enabled), viewModel.isEnabled.yesNo())
        DiagRow(stringResource(R.string.diag_can_advertise), viewModel.canAdvertise.yesNo())
        DiagRow("BleStatus", status.label())

        Spacer(Modifier.height(BukSpacing.sm))
        Text(
            text = stringResource(R.string.diag_permissions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        viewModel.permissionReport().forEach { (name, granted) ->
            DiagRow(name, granted.yesNo())
        }

        Spacer(Modifier.height(BukSpacing.sm))
        DiagRow(stringResource(R.string.diag_host_state), hostState.label())
        DiagRow(
            stringResource(R.string.diag_clock_offset),
            stringResource(R.string.diag_clock_offset_value, clockOffset),
        )
        // Anything past one window means every code this phone emits is rejected, while the
        // beacon still looks perfectly healthy. Worth saying out loud, not just showing.
        if (kotlin.math.abs(clockOffset) > RotatingCode.WINDOW_SECONDS) {
            Text(
                text = stringResource(R.string.diag_clock_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(BukSpacing.md))
        Text(
            text = stringResource(R.string.diag_sightings),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (sightings.isEmpty()) {
            Text(
                text = stringResource(R.string.diag_none_yet),
                style = MaterialTheme.typography.bodySmall,
                color = BukInkMuted,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(sightings) { event ->
                Text(
                    text = event.label(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BukInkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Boolean.yesNo(): String =
    stringResource(if (this) R.string.diag_yes else R.string.diag_no)

private fun BleStatus.label(): String = when (this) {
    BleStatus.Ready -> "Ready"
    BleStatus.NoAdapter -> "NoAdapter"
    BleStatus.Disabled -> "Disabled"
    is BleStatus.PermissionsMissing ->
        "PermissionsMissing(${permissions.joinToString { it.substringAfterLast('.') }})"
    BleStatus.CannotAdvertise -> "CannotAdvertise"
}

private fun HostState.label(): String = when (this) {
    HostState.Stopped -> "Stopped"
    HostState.Starting -> "Starting"
    is HostState.Broadcasting -> "Broadcasting id=$instanciaId code=${code.toHex()} counter=$counter"
    is HostState.Failed -> "Failed($errorCode)"
}

private fun ScanEvent.label(): String = when (this) {
    is ScanEvent.Sighting -> "id=$instanciaId ${code.toHex()} ${rssi}dBm"
    is ScanEvent.Failed -> "SCAN FAILED code=$errorCode"
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

@Preview(showBackground = true)
@Composable
private fun BleDiagnosticsPreview() {
    BukInTheme {
        Column(Modifier.padding(BukSpacing.lg)) {
            DiagRow("Adaptador", "sí")
            DiagRow("BleStatus", "Ready")
            DiagRow("BLUETOOTH_SCAN", "sí")
        }
    }
}
