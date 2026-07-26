package com.buk.bukin.feature.checkin

import androidx.lifecycle.ViewModel
import com.buk.bukin.domain.model.CheckInErrorReason
import com.buk.bukin.domain.model.CheckInState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Screen state for the collaborator flow.
 *
 * Session 1 has no radio and no network, so the state is driven by hand. Sessions 2 and 3
 * replace [advanceForDebug] and the body of [onCheckIn] with a BLE scan and an RPC call;
 * the surface this screen renders against does not change.
 */
class CheckInViewModel : ViewModel() {

    private val _state = MutableStateFlow<CheckInState>(CheckInState.Scanning)
    val state: StateFlow<CheckInState> = _state.asStateFlow()

    /** The one deliberate tap. Terminal — success is never dismissed or confirmed. */
    fun onCheckIn() {
        _state.update { CheckInState.Success }
    }

    /**
     * The way out offered by a blocked state. Session 2 turns Bluetooth on for real;
     * for now every recovery returns to scanning, which is the honest end state — the
     * screen resumes looking for the host on its own.
     */
    fun onRecover() {
        _state.update { CheckInState.Scanning }
    }

    /** Debug-only: walks every state so all of them are inspectable without hardware. */
    fun advanceForDebug() {
        _state.update { current ->
            when (current) {
                CheckInState.Scanning -> CheckInState.Ready
                CheckInState.Ready -> CheckInState.Success
                CheckInState.Success -> CheckInState.Offline
                CheckInState.Offline -> CheckInState.Error(CheckInErrorReason.BLUETOOTH_OFF)
                is CheckInState.Error -> when (current.reason) {
                    CheckInErrorReason.BLUETOOTH_OFF ->
                        CheckInState.Error(CheckInErrorReason.HOST_NOT_FOUND)
                    CheckInErrorReason.HOST_NOT_FOUND ->
                        CheckInState.Error(CheckInErrorReason.SAVE_FAILED)
                    CheckInErrorReason.SAVE_FAILED -> CheckInState.Scanning
                }
            }
        }
    }
}
