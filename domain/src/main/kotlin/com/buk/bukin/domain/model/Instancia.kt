package com.buk.bukin.domain.model

/**
 * One scheduled run of a course — what the ticket card renders.
 *
 * Spanish domain nouns are kept deliberately: they match the Postgres schema, and
 * translating them would cost more in mismatch than it gains in consistency.
 *
 * Times are pre-formatted strings this session. Real [java.time] values arrive with the
 * Supabase DTOs in session 3; formatting them is not this session's problem.
 */
data class Instancia(
    val curso: String,
    val fecha: String,
    val horaInicio: String,
    val duracion: String,
    val horaCheckIn: String,
    val horaSalida: String,
)
