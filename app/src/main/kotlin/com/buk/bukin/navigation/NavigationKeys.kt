package com.buk.bukin.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination in the app. A sealed hierarchy so kotlinx.serialization handles the
 * polymorphism for the saveable back stack without a registration table.
 *
 * Keys carry the instancia they refer to. There is no ambient "current session" — the
 * session a screen is about is part of the route, which is what stops a check-in screen
 * from ever being ambiguous about which room it is trying to enter.
 */
@Serializable
sealed interface BukInKey : NavKey {

    /** Four steps, the last of which asks for a name. First run is one flow. */
    @Serializable data object Onboarding : BukInKey

    /**
     * The standalone change-name screen.
     *
     * It survives onboarding absorbing the name question, because the session list offers
     * "No soy X" and that has to land somewhere. One form, two hosts.
     */
    @Serializable data object NameEntry : BukInKey

    /**
     * The receipt: every past session this collaborator attended.
     *
     * Presentation over data the app is already fetching — `listar_instancias` has no
     * `WHERE` clause and returns every instance with an `asistencia` flag. No RPC, no
     * migration, no new network call.
     */
    @Serializable data object MiAsistencia : BukInKey

    @Serializable data object RolePicker : BukInKey

    /** The session list. Same screen for both roles; the actions differ. */
    @Serializable data class SessionPicker(val isHost: Boolean) : BukInKey

    @Serializable data class CheckIn(val instanciaId: Int) : BukInKey

    @Serializable data class Host(val instanciaId: Int) : BukInKey

    /** The host's live roster of arrivals. */
    @Serializable data class Roster(val instanciaId: Int) : BukInKey

    /** Registering someone by hand when their phone cannot do it. */
    @Serializable data class ManualRegistration(val instanciaId: Int) : BukInKey

    /** Debug surface: what the radio actually sees. Reached from the role picker. */
    @Serializable data object Diagnostics : BukInKey
}
