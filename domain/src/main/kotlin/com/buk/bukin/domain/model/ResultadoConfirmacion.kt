package com.buk.bukin.domain.model

/**
 * What the server decided about a confirmation attempt.
 *
 * The client never judges its own check-in. It relays the eight opaque bytes it overheard
 * and Postgres recomputes the code; this is that verdict coming back. See invariant 4 in
 * `context/architecture.md`.
 */
sealed interface ResultadoConfirmacion {

    /** Registered. */
    data object Ok : ResultadoConfirmacion

    /**
     * Already registered, and nothing changed.
     *
     * Renders exactly like [Ok]. A person who taps twice did nothing wrong and must never
     * see an error for it — the assessment calls this out explicitly.
     */
    data object YaRegistrado : ResultadoConfirmacion

    /**
     * The code did not verify: stale by more than one window, from another instance, or
     * never derived from this room's key at all. This is the replay defence firing.
     */
    data object CodigoInvalido : ResultadoConfirmacion

    /** The session's hour has not come, or has passed. */
    data object FueraDeVentana : ResultadoConfirmacion

    /**
     * The request never reached the server. Distinct from a rejection: nothing was decided
     * and retrying is the right move.
     */
    data object SinRed : ResultadoConfirmacion
}
