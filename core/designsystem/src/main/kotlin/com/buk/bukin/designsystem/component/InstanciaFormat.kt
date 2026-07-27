package com.buk.bukin.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.buk.bukin.designsystem.R
import com.buk.bukin.domain.model.Instancia
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turning an [Instancia]'s real timestamps into the strings the ticket renders.
 *
 * This lives in `:core:designsystem` rather than `:domain` because it is presentation: a
 * locale, a time zone and a display pattern. `:domain` holds [Instant]s and no opinion about
 * how they look.
 */
internal object InstanciaFormat {

    // Deployed in Colombia, Spanish only. Not the device locale: the entire app is written
    // in one language, and a phone set to English would otherwise render "Monday" beside
    // Spanish copy.
    private val locale: Locale = Locale.forLanguageTag("es-CO")

    private val fecha = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)
    private val hora = DateTimeFormatter.ofPattern("h:mm a", locale)

    /**
     * The ticket's two display figures, without the meridiem.
     *
     * At 34sp "7:30 p. m." is close to twice the rendered width of the bare time, and the
     * two blocks squeezed each other until the start time clipped on a 411dp phone. The
     * stub line directly beneath carries a full "Entrada desde 5:50 p. m.", so the hour is
     * never actually ambiguous.
     */
    private val horaCorta = DateTimeFormatter.ofPattern("h:mm", locale)

    /** How early the doors open, matching `ventana_activa` in the migrations. */
    private const val CHECK_IN_ABRE_MINUTOS = 10L

    fun fecha(instant: Instant, zone: ZoneId): String =
        fecha.format(instant.atZone(zone)).replaceFirstChar { it.titlecase(locale) }

    fun hora(instant: Instant, zone: ZoneId): String = hora.format(instant.atZone(zone))

    fun horaCorta(instant: Instant, zone: ZoneId): String = horaCorta.format(instant.atZone(zone))

    fun horaCheckIn(inicio: Instant, zone: ZoneId): String =
        hora(inicio.minusSeconds(CHECK_IN_ABRE_MINUTOS * 60), zone)

    /** The same, short-form, for the ticket stub where the state pill wants the width. */
    fun horaCheckInCorta(inicio: Instant, zone: ZoneId): String =
        horaCorta(inicio.minusSeconds(CHECK_IN_ABRE_MINUTOS * 60), zone)
}

/** "2 horas", "90 minutos" — whichever reads naturally for the duration given. */
@Composable
internal fun duracionLegible(minutos: Int): String =
    if (minutos >= 60 && minutos % 60 == 0) {
        pluralStringResource(R.plurals.ticket_duracion_horas, minutos / 60, minutos / 60)
    } else {
        pluralStringResource(R.plurals.ticket_duracion_minutos, minutos, minutos)
    }

/** The device's zone, re-read only if it changes under us. */
@Composable
fun rememberZone(): ZoneId {
    val context = LocalContext.current
    return remember(context) { ZoneId.systemDefault() }
}

/** "Lunes, 13 de julio · 10:00 a. m. · 2 horas" — one line under a session's name. */
@Composable
fun instanciaSubtitulo(instancia: Instancia): String {
    val zone = rememberZone()
    val duracion = duracionLegible(instancia.duracionMinutos)
    return "${InstanciaFormat.fecha(instancia.fechaInicio, zone)} · " +
        "${InstanciaFormat.hora(instancia.fechaInicio, zone)} · $duracion"
}

/**
 * When check-in unlocks for a session — ten minutes before it starts, matching
 * `ventana_activa` in the migrations.
 */
@Composable
fun instanciaHoraApertura(instancia: Instancia): String =
    horaApertura(instancia.fechaInicio, rememberZone())

/** The same, for a screen that holds the start time but not the whole [Instancia]. */
fun horaApertura(fechaInicio: Instant, zone: ZoneId): String =
    InstanciaFormat.horaCheckIn(fechaInicio, zone)

/** A bare clock time — an arrival on the roster, for instance. */
@Composable
fun InstanciaHora(instant: Instant): String =
    InstanciaFormat.hora(instant, rememberZone())
