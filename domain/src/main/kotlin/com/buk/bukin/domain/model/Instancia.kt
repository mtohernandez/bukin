package com.buk.bukin.domain.model

import java.time.Instant

/**
 * One scheduled run of a course — what the session list and the ticket card render.
 *
 * Spanish domain nouns are kept deliberately: they match the Postgres schema, and
 * translating them would cost more in mismatch than it gains in consistency.
 *
 * Times are real [Instant]s. Formatting them is presentation, so it happens in
 * `:core:designsystem` where the rest of the presentation lives.
 */
data class Instancia(
    /**
     * Serialized as a big-endian int32 inside the BLE service UUID, which is why it is an
     * [Int] here and an `integer` in Postgres rather than a uuid.
     */
    val id: Int,
    val cursoNombre: String,
    val duracionMinutos: Int,
    val fechaInicio: Instant,
    val fechaFin: Instant,

    /**
     * Whether the clock says this session can be checked into right now — computed by the
     * server so the app and the RPC cannot disagree about it.
     *
     * Deliberately *not* "the host pressed a button". A collaborator signs into a session
     * ahead of time and waits for its hour.
     */
    val activa: Boolean,

    /** A host has opened the room, so there is a key and a broadcast to find. */
    val abierta: Boolean,

    /** This collaborator signed in ahead of time — they will be `PRE_INSCRITO`. */
    val inscrito: Boolean,

    /** This collaborator's attendance is already recorded. Nothing left to do. */
    val asistencia: Boolean,
)
