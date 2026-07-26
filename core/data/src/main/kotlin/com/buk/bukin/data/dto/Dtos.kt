package com.buk.bukin.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the RPC responses. These stay inside `:core:data` — repositories return
 * `:domain` types, so nothing above this module ever sees a `@SerialName`.
 *
 * Timestamps arrive as ISO-8601 strings and are parsed in the mappers rather than through a
 * custom serializer. kotlinx-serialization has no built-in `java.time` support, and one
 * `OffsetDateTime.parse` at the boundary is smaller than a serializer module.
 */

@Serializable
internal data class InstanciaDto(
    @SerialName("instancia_id") val instanciaId: Int,
    @SerialName("curso_nombre") val cursoNombre: String,
    @SerialName("duracion_minutos") val duracionMinutos: Int,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String,
    val estado: String,
    val activa: Boolean,
    val abierta: Boolean,
    val inscrito: Boolean,
    val asistencia: Boolean,
)

@Serializable
internal data class AsistenciaDto(
    @SerialName("colaborador_id") val colaboradorId: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    @SerialName("fecha_llegada") val fechaLlegada: String? = null,
    @SerialName("metodo_confirmacion") val metodoConfirmacion: String? = null,
    val origen: String,
)

@Serializable
internal data class ColaboradorDto(
    @SerialName("colaborador_id") val colaboradorId: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    @SerialName("ya_registrado") val yaRegistrado: Boolean,
)
