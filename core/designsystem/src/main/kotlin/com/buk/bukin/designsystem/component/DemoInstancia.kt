package com.buk.bukin.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.buk.bukin.designsystem.R
import com.buk.bukin.domain.model.Instancia

/**
 * The one hardcoded course the demo renders, until session 3 fetches real rows.
 *
 * Its copy lives in `strings.xml` like every other Spanish string rather than inline in
 * Kotlin — a course name and a date are user-facing text, not configuration.
 */
@Composable
fun rememberDemoInstancia(): Instancia {
    val curso = stringResource(R.string.demo_curso)
    val fecha = stringResource(R.string.demo_fecha)
    val horaInicio = stringResource(R.string.demo_hora_inicio)
    val duracion = stringResource(R.string.demo_duracion)
    val horaCheckIn = stringResource(R.string.demo_hora_checkin)
    val horaSalida = stringResource(R.string.demo_hora_salida)
    return remember(curso, fecha, horaInicio, duracion, horaCheckIn, horaSalida) {
        Instancia(
            curso = curso,
            fecha = fecha,
            horaInicio = horaInicio,
            duracion = duracion,
            horaCheckIn = horaCheckIn,
            horaSalida = horaSalida,
        )
    }
}
