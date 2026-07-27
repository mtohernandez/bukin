package com.buk.bukin.domain.model

/**
 * A person attending a course.
 *
 * There is no authentication in v1 — a person types their name and that is the whole
 * identity model — so [id] is client-asserted and therefore forgeable. This is a deliberate
 * scope cut, and it is a bigger practical hole than the relay attack: BLE proves *a phone*
 * was in the room, never *whose*. Only authentication closes it. The RPC boundary is shaped
 * so swapping [id] for a JWT claim is a one-argument change. See `context/architecture.md`.
 */
data class Colaborador(
    val id: String,
    val nombre: String,

    /** Already has attendance recorded for the instance being asked about. Host-side only. */
    val yaRegistrado: Boolean = false,
)
