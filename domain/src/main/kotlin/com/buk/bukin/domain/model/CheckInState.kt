package com.buk.bukin.domain.model

/**
 * The collaborator screen is a state machine, not a bag of booleans. These states cannot
 * coexist, so they are a sealed interface — `isScanning + isReady + hasError` is exactly
 * the shape that lets the UI show two things at once and leaves a user asking whether
 * their attendance registered.
 */
sealed interface CheckInState {

    /** Listening for the host's broadcast. Starts on its own; nothing to press. */
    data object Scanning : CheckInState

    /** Scanning works without the network, but saving does not. Says so, honestly. */
    data object Offline : CheckInState

    /** Host detected and validated. The button unlocked itself. */
    data object Ready : CheckInState

    /** Terminal. Nothing to dismiss, nothing to acknowledge. */
    data object Success : CheckInState

    /** Blocked. Every reason carries its own cause and its own way out. */
    data class Error(val reason: CheckInErrorReason) : CheckInState
}

/**
 * Why the flow is blocked. The UI maps each to a sentence and a one-tap action — no raw
 * error code ever reaches the screen, and no state blames the user.
 */
enum class CheckInErrorReason {
    BLUETOOTH_OFF,
    HOST_NOT_FOUND,
    SAVE_FAILED,
}
