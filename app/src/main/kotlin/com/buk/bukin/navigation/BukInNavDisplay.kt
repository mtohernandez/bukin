package com.buk.bukin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.buk.bukin.feature.checkin.CheckInRoute
import com.buk.bukin.feature.onboarding.OnboardingRoute
import com.buk.bukin.ui.HostPlaceholderScreen
import com.buk.bukin.ui.RolePickerScreen

/**
 * Root navigation. Glue only — every screen is owned by its feature module.
 *
 * @param startKey where the app opens, decided once in `MainActivity` from whether
 * onboarding has been seen.
 * @param showDebugStateControl session 1 only: no radio exists yet, so the four
 * collaborator states need a way to be walked by hand on a device.
 */
@Composable
fun BukInNavDisplay(
    startKey: BukInKey,
    modifier: Modifier = Modifier,
    showDebugStateControl: Boolean = false,
) {
    val backStack = rememberNavBackStack(startKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<BukInKey.Onboarding> {
                // Replace rather than push: onboarding is shown once, and backing into it
                // from the role picker would be asking the same thing twice.
                OnboardingRoute(onFinished = { backStack.replaceAllWith(BukInKey.RolePicker) })
            }
            entry<BukInKey.RolePicker> {
                RolePickerScreen(
                    onCollaborator = { backStack.add(BukInKey.CheckIn) },
                    onHost = { backStack.add(BukInKey.Host) },
                )
            }
            entry<BukInKey.CheckIn> {
                CheckInRoute(showDebugStateControl = showDebugStateControl)
            }
            entry<BukInKey.Host> {
                HostPlaceholderScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

private fun <T : NavKey> NavBackStack<T>.replaceAllWith(key: T) {
    clear()
    add(key)
}
