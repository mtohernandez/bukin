package com.buk.bukin.domain.model

/**
 * A person attending a course.
 *
 * There is no authentication in v1 — the collaborator is selected, not logged in — so
 * [id] is client-chosen. The RPC boundary is shaped so swapping it for a JWT claim is a
 * one-argument change. See `context/architecture.md`.
 */
data class Colaborador(
    val id: String,
    val nombre: String,
)
