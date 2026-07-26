package com.buk.bukin.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination in the app. A sealed hierarchy so kotlinx.serialization handles the
 * polymorphism for the saveable back stack without a registration table.
 */
@Serializable
sealed interface BukInKey : NavKey {

    @Serializable data object Onboarding : BukInKey

    @Serializable data object RolePicker : BukInKey

    @Serializable data object CheckIn : BukInKey

    /** Placeholder until session 2 builds `:features:host`. */
    @Serializable data object Host : BukInKey
}
