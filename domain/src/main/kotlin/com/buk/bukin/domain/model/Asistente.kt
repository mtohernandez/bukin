package com.buk.bukin.domain.model

import java.time.Instant

/** How an attendance was recorded. `AUTO` exists in the schema but nothing produces it yet. */
enum class MetodoConfirmacion { BLE, MANUAL, AUTO }

/** Whether the person signed into the session ahead of time, or simply turned up. */
enum class Origen { PRE_INSCRITO, WALK_IN }

/**
 * One row of the host's live roster.
 *
 * [fechaLlegada] is null for someone enrolled who has not arrived — the host wants to see
 * who is still missing as much as who is present, and it is the same query either way.
 */
data class Asistente(
    val colaboradorId: String,
    val nombre: String,
    val fechaLlegada: Instant?,
    val metodo: MetodoConfirmacion?,
    val origen: Origen,
)
