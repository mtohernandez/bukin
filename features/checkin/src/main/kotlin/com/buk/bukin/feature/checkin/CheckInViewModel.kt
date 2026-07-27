package com.buk.bukin.feature.checkin

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buk.bukin.ble.BleCapability
import com.buk.bukin.ble.BleScanner
import com.buk.bukin.ble.BleStatus
import com.buk.bukin.ble.ScanEvent
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.data.NetworkMonitor
import com.buk.bukin.domain.crypto.RotatingCode
import com.buk.bukin.domain.model.CheckInErrorReason
import com.buk.bukin.domain.model.CheckInState
import com.buk.bukin.domain.model.Instancia
import com.buk.bukin.domain.model.ResultadoConfirmacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state for the collaborator flow, driven by the radio and closed by the server.
 *
 * Three inputs merge into one state: what the antenna can hear, whether the session's hour
 * has come, and the verdict the server returned for a submission. Nothing on this screen
 * asks the user to start anything.
 *
 * An [AndroidViewModel] rather than a constructor-injected one because the only dependency
 * is a Context and the default factory already supplies it. [instanciaId] and
 * [colaboradorId] arrive from the nav key through [bind] instead of a factory, which keeps
 * that true for one more session.
 */
class CheckInViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The most recent sighting, which is what gets submitted.
     *
     * Deliberately the latest and not the first: a person can detect the host, get
     * distracted, and tap a minute later. The code they first saw would be two windows
     * stale by then and the server would reject it, with no symptom a user could act on.
     * This is the most likely source of spurious rejections once real people are involved.
     */
    @Volatile
    private var latestSighting: ScanEvent.Sighting? = null

    private var instanciaId: Int = 0
    private var colaboradorId: String = ""

    /** Bumped by [onRecover] to re-run the preflight after a permission grant or a retry. */
    private val retries = MutableStateFlow(0)

    /**
     * Consecutive `CODIGO_INVALIDO` answers.
     *
     * Reset by a success and by leaving the screen. Not reset by a retry — the whole point
     * is to notice that retrying is not working.
     */
    private var rechazosSeguidos = 0

    /**
     * The session being checked into, once loaded. Its `activa` flag is computed by the
     * server from the clock, so the app and the RPC cannot disagree about whether the
     * button should have been offered at all.
     */
    private val _instancia = MutableStateFlow<Instancia?>(null)
    val instancia: StateFlow<Instancia?> = _instancia.asStateFlow()

    /** The submission's outcome, which overrides everything the radio has to say. */
    private val submission = MutableStateFlow<CheckInState?>(null)

    fun bind(instanciaId: Int, colaboradorId: String) {
        if (this.instanciaId == instanciaId && this.colaboradorId == colaboradorId) return
        this.instanciaId = instanciaId
        this.colaboradorId = colaboradorId
        cargarInstancia()
    }

    private fun cargarInstancia() {
        viewModelScope.launch {
            BukInRepository.listarInstancias(colaboradorId).onSuccess { lista ->
                _instancia.value = lista.firstOrNull { it.id == instanciaId }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val radioState: StateFlow<CheckInState> =
        retries
            .flatMapLatest { BleCapability.statusFlow(getApplication()) }
            .flatMapLatest { status ->
                if (status !is BleStatus.Ready) {
                    flowOf(CheckInState.Error(status.toReason()))
                } else {
                    BleScanner.scan(getApplication())
                        // Only this room's beacon counts. Another session broadcasting
                        // nearby must not unlock a button that the server would then
                        // reject — the filter is on the magic prefix, not on the instance.
                        .filterRelevant()
                        .onEach { if (it is ScanEvent.Sighting) latestSighting = it }
                        // The grace period, in one operator: every new event cancels the
                        // pending "the host is gone" emission and restarts the countdown.
                        .transformLatest { event ->
                            emit(event)
                            delay(GRACE_MILLIS)
                            emit(null)
                        }
                        .map { event ->
                            when (event) {
                                is ScanEvent.Sighting -> CheckInState.Ready
                                is ScanEvent.Failed -> CheckInState.Error(CheckInErrorReason.SCAN_FAILED)
                                // Nothing heard for a whole grace period: back to looking.
                                null -> CheckInState.Scanning
                            }
                        }
                        // Announce SCANNING the moment the scan starts, before anything is
                        // heard. Without this the branch emits nothing in an empty room, the
                        // StateFlow keeps whatever it held before — and recovering from any
                        // error leaves the old error on screen until a beacon happens by.
                        // Switching Bluetooth back on looked like it did nothing at all.
                        .onStart { emit(CheckInState.Scanning) }
                }
            }
            .stateIn(
                scope = viewModelScope,
                // Also the answer to "stop scanning off-screen" and to leaked callbacks:
                // collectAsStateWithLifecycle unsubscribes at STOPPED, this cancels the
                // callbackFlow, and awaitClose calls stopScan. No DisposableEffect needed.
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS),
                initialValue = CheckInState.Scanning,
            )

    /** Whether the device can actually reach the internet, straight from the platform. */
    private val online = NetworkMonitor.isOnline(application)

    val state: StateFlow<CheckInState> =
        combine(radioState, submission, _instancia, online) { radio, enviado, instancia, hayRed ->
            when {
                // "Sin conexión" is a claim about the network, so it stops being true the
                // moment the network comes back. Leaving it up would strand someone on a
                // card that says they need internet while they are holding a phone that
                // has it — and the card is what replaces the button, so the screen would
                // be a dead end with no way out but the back gesture.
                enviado == CheckInState.Offline && hayRed -> radio

                // Any other finished submission outranks the radio.
                enviado != null -> enviado
                // Signed in but the hour has not come. The radio is irrelevant until then,
                // and offering a button that cannot work is worse than saying so.
                instancia != null && !instancia.activa ->
                    CheckInState.EsperandoHora(instancia.fechaInicio)
                else -> radio
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MILLIS),
            initialValue = CheckInState.Scanning,
        )

    /**
     * The one deliberate tap.
     *
     * The client validates nothing. It hands over the eight opaque bytes it overheard and
     * Postgres decides — a client asserting "the code was valid" is not authorization.
     */
    fun onCheckIn() {
        // Setting Enviando first is the in-flight guard: a second tap sees Enviando and
        // returns, so a double tap can never become two requests.
        //
        // Deliberately narrower than "any finished submission". A previous attempt that
        // failed for want of network must not block the retry that the returning network
        // makes possible.
        if (submission.value == CheckInState.Enviando) return
        val sighting = latestSighting

        // Nothing heard, or heard too long ago. Anything older than one window is certain
        // to be rejected, and a rejection the user cannot act on reads as a broken app.
        // Admit the host was lost and go back to looking instead.
        if (sighting == null ||
            !RotatingCode.isFresh(sighting.seenAtElapsedRealtime, SystemClock.elapsedRealtime())
        ) {
            latestSighting = null
            submission.value = CheckInState.Error(CheckInErrorReason.HOST_NOT_FOUND)
            return
        }

        submission.value = CheckInState.Enviando
        viewModelScope.launch {
            val resultado = BukInRepository.confirmarAsistencia(
                instanciaId = instanciaId,
                colaboradorId = colaboradorId,
                code = sighting.code,
            )
            submission.value = when (resultado) {
                // The user does not care about the difference, and a duplicate must never
                // surface an error — the assessment calls this out explicitly.
                ResultadoConfirmacion.Ok,
                ResultadoConfirmacion.YaRegistrado,
                -> {
                    rechazosSeguidos = 0
                    // Re-read the row so the ticket's state pill flips to "Asistencia
                    // marcada". It is loaded once at bind, so without this the card sits
                    // there still claiming the pre-check-in state directly above a screen
                    // saying the attendance registered — observed on the phone. The pill
                    // exists to carry state; it has to carry the state that just changed.
                    cargarInstancia()
                    CheckInState.Success
                }

                ResultadoConfirmacion.CodigoInvalido -> {
                    // Never resubmit the bytes the server just refused. Without this the
                    // retry can hand over the identical code and fail identically, which
                    // is what makes a rejection look unrecoverable even when it is not.
                    latestSighting = null
                    rechazosSeguidos += 1
                    if (rechazosSeguidos >= RECHAZOS_ANTES_DE_ESCALAR) {
                        CheckInState.Error(CheckInErrorReason.CODE_REJECTED_REPETIDO)
                    } else {
                        CheckInState.Error(CheckInErrorReason.CODE_REJECTED)
                    }
                }

                ResultadoConfirmacion.FueraDeVentana ->
                    CheckInState.Error(CheckInErrorReason.OUT_OF_WINDOW)

                // Nothing was decided. Distinct from a rejection on purpose.
                ResultadoConfirmacion.SinRed -> CheckInState.Offline

                // Reached the server and it refused. Not the user's fault and not their
                // connection, so it says neither.
                ResultadoConfirmacion.ErrorServidor ->
                    CheckInState.Error(CheckInErrorReason.SAVE_FAILED)
            }
        }
    }

    /** Re-runs the preflight, and clears a finished submission so the radio speaks again. */
    fun onRecover() {
        if (submission.value !is CheckInState.Success) {
            submission.value = null
        }
        cargarInstancia()
        retries.update { it + 1 }
    }

    /**
     * Keeps only what this session's host is broadcasting.
     *
     * A [ScanEvent.Failed] is passed through untouched — a scan that will not start is not
     * an irrelevant sighting, and swallowing it would leave the screen on SCANNING forever.
     */
    private fun kotlinx.coroutines.flow.Flow<ScanEvent>.filterRelevant() =
        map { event ->
            when {
                event is ScanEvent.Sighting && event.instanciaId != instanciaId -> null
                else -> event
            }
        }.filterNotNull()

    private fun BleStatus.toReason(): CheckInErrorReason = when (this) {
        BleStatus.NoAdapter -> CheckInErrorReason.BLUETOOTH_UNAVAILABLE
        BleStatus.Disabled -> CheckInErrorReason.BLUETOOTH_OFF
        is BleStatus.PermissionsMissing -> CheckInErrorReason.PERMISSION_DENIED
        // Host-only, and the collaborator never asks for it. Mapped rather than ignored so
        // this `when` stays exhaustive if the sealed hierarchy grows.
        BleStatus.CannotAdvertise -> CheckInErrorReason.BLUETOOTH_UNAVAILABLE
        BleStatus.Ready -> CheckInErrorReason.SCAN_FAILED
    }

    private companion object {
        /**
         * One rejection is a stale code and the app says so. Two in a row is a signal that
         * does not match the session, and no amount of looking again will change it.
         */
        const val RECHAZOS_ANTES_DE_ESCALAR = 2

        /**
         * How long the host may go unheard before the button locks again.
         *
         * Comfortably longer than the stop/start blip at each 30-second rotation, and short
         * enough that walking out of the room takes the button away while the person is
         * still looking at the screen.
         */
        const val GRACE_MILLIS = 10_000L

        /** Survives a rotation without tearing the scan down and starting it up again. */
        const val SUBSCRIPTION_GRACE_MILLIS = 5_000L
    }
}
