package com.buk.bukin.feature.host

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buk.bukin.ble.BleCapability
import com.buk.bukin.ble.BleStatus
import com.buk.bukin.ble.HostAdvertisingService
import com.buk.bukin.ble.HostSession
import com.buk.bukin.ble.HostState
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.domain.crypto.RotatingCode
import com.buk.bukin.domain.model.Instancia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** What the host screen renders. [status] is the preflight; [session] is the radio. */
data class HostUiState(
    val status: BleStatus = BleStatus.Ready,
    val session: HostState = HostState.Stopped,
    val instancia: Instancia? = null,
    val abriendo: Boolean = false,
    val errorDeRed: Boolean = false,

    /**
     * `server_now - device_now`, in seconds, from the moment the room opened.
     *
     * Surfaced so a wrong host clock is diagnosable rather than invisible. See [start].
     */
    val clockOffsetSeconds: Long = 0,
)

class HostViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The per-instance key. Generated here, held in memory, and never written down.
     *
     * It travels **up** to the server when the room opens and no endpoint ever returns it.
     * That direction is the whole point: with no authentication, an API that handed the key
     * back would let anyone generate valid codes from anywhere in the world, which is
     * strictly worse than the relay attack because it needs no physical presence at all.
     */
    private var instanceKey: ByteArray? = null

    private var instanciaId: Int = 0

    private val retries = MutableStateFlow(0)
    private val local = MutableStateFlow(HostUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HostUiState> =
        combine(
            retries.flatMapLatest { BleCapability.statusFlow(getApplication(), forHost = true) },
            HostSession.state,
            local,
        ) { status, session, extra ->
            // HostSession is process-global (one radio, one room at a time), so it happily
            // reports a broadcast belonging to a different instancia. Showing "la sala está
            // abierta" on the screen for a session that is not the one on air would be a
            // straightforward lie, so anything not about this instancia reads as stopped.
            val propia = when (session) {
                is HostState.Broadcasting -> session.instanciaId == instanciaId
                else -> true
            }
            extra.copy(
                status = status,
                session = if (propia) session else HostState.Stopped,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HostUiState(),
        )

    fun bind(instanciaId: Int) {
        if (this.instanciaId == instanciaId) return
        this.instanciaId = instanciaId
        viewModelScope.launch {
            BukInRepository.listarInstancias(null).onSuccess { lista ->
                local.update { it.copy(instancia = lista.firstOrNull { i -> i.id == instanciaId }) }
            }
        }
    }

    /**
     * Opens the room.
     *
     * Two things happen, in this order and not the other: the key goes up to the server, and
     * only then does the radio start. Broadcasting a code the server cannot verify would
     * produce check-ins that all fail with no visible cause.
     *
     * The server's own clock comes back with it. A host phone with automatic time switched
     * off drifts far enough to generate codes that are rejected 100% of the time, and there
     * is no symptom a user could diagnose — the beacon looks healthy and every check-in
     * fails. One subtraction removes the entire failure class.
     *
     * Advertising itself runs in a foreground service rather than here, because the host
     * puts the phone down and the screen locks — and a broadcast that died with the screen
     * would take the whole class's attendance with it.
     */
    fun start(notificationTitle: String, notificationText: String) {
        if (local.value.abriendo) return
        val key = instanceKey ?: ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        instanceKey = key
        local.update { it.copy(abriendo = true, errorDeRed = false) }

        viewModelScope.launch {
            BukInRepository.abrirInstancia(instanciaId, key).fold(
                onSuccess = { serverNow ->
                    val offset = serverNow.epochSecond - System.currentTimeMillis() / 1000
                    local.update {
                        it.copy(abriendo = false, clockOffsetSeconds = offset)
                    }
                    HostAdvertisingService.start(
                        context = getApplication(),
                        key = key,
                        instanciaId = instanciaId,
                        notificationTitle = notificationTitle,
                        notificationText = notificationText,
                        clockOffsetSeconds = offset,
                    )
                },
                onFailure = {
                    // No key stored server-side means no code could ever verify. Do not
                    // start the radio and pretend the room is open.
                    instanceKey = null
                    local.update { it.copy(abriendo = false, errorDeRed = true) }
                },
            )
        }
    }

    fun stop() {
        HostAdvertisingService.stop(getApplication())
        // A new room gets a new key: yesterday's captured code must never open today's.
        instanceKey = null
    }

    fun onRecover() {
        local.update { it.copy(errorDeRed = false) }
        retries.update { it + 1 }
    }

    override fun onCleared() {
        // Deliberately does NOT stop the service. Leaving the screen is not closing the
        // room — that is what the stop button is for.
        super.onCleared()
    }

    private companion object {
        const val KEY_BYTES = 16
    }
}

/** True when the host's clock is off by more than a whole rotation window. */
fun HostUiState.relojDesfasado(): Boolean =
    kotlin.math.abs(clockOffsetSeconds) > RotatingCode.WINDOW_SECONDS
