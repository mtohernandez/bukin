package com.buk.bukin.designsystem.component

import com.buk.bukin.domain.model.Instancia
import java.time.Instant

/**
 * A fixed [Instancia] for `@Preview` only.
 *
 * Fixed rather than relative to `now()` so a preview render is reproducible and can be
 * compared against `docs/assets/`. Nothing in the running app uses it — real rows come from
 * `listar_instancias`.
 */
internal fun previewInstancia(): Instancia = Instancia(
    id = 1,
    cursoNombre = "Manejo de alimentos",
    duracionMinutos = 120,
    fechaInicio = Instant.parse("2026-07-13T15:00:00Z"),
    fechaFin = Instant.parse("2026-07-13T17:00:00Z"),
    activa = true,
    abierta = true,
    inscrito = true,
    asistencia = false,
)
