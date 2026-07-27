package com.buk.bukin.domain.model

/**
 * The collaborator screen is a state machine, not a bag of booleans. These states cannot
 * coexist, so they are a sealed interface — `isScanning + isReady + hasError` is exactly
 * the shape that lets the UI show two things at once and leaves a user asking whether
 * their attendance registered.
 */
sealed interface CheckInState {

    /**
     * Signed into this session, but its hour has not come. The radio is not even switched
     * on yet — there is nothing to find and nothing to press.
     */
    data class EsperandoHora(val fechaInicio: java.time.Instant) : CheckInState

    /** Listening for the host's broadcast. Starts on its own; nothing to press. */
    data object Scanning : CheckInState

    /** Scanning works without the network, but saving does not. Says so, honestly. */
    data object Offline : CheckInState

    /** Host detected and validated. The button unlocked itself. */
    data object Ready : CheckInState

    /** The confirmation is in flight. Guards against a second request from a double tap. */
    data object Enviando : CheckInState

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
    /** Hardware present, switched off. One tap away. */
    BLUETOOTH_OFF,

    /** No Bluetooth radio in this device at all. Nothing to offer but an explanation. */
    BLUETOOTH_UNAVAILABLE,

    /** The nearby-devices permission was refused. Ask again, or send them to settings. */
    PERMISSION_DENIED,

    /** The scan itself failed — the platform refused to start or dropped it. Retry. */
    SCAN_FAILED,

    /** The broadcast was lost between detecting it and tapping. Look again. */
    HOST_NOT_FOUND,

    /** The write failed on the way out — no network, or the server never answered. */
    SAVE_FAILED,

    /**
     * The server recomputed the code and refused it. Almost always a code that went stale
     * while the screen sat idle; occasionally the replay defence doing its job.
     */
    CODE_REJECTED,

    /**
     * The server has refused the code more than once in a row.
     *
     * A single rejection is almost always a code that went stale while the screen sat
     * idle, and "we'll look for the current one" is the truth. Repeated rejections are
     * not that: they mean the signal in the room does not match what the server holds for
     * this session — a host that reopened the room and rotated its key, or a beacon for a
     * different session entirely. Retrying cannot fix either, so the app stops promising
     * it can and names the way out instead.
     */
    CODE_REJECTED_REPETIDO,

    /** The server says this session's hour has passed, or has not arrived. */
    OUT_OF_WINDOW,
}
