package com.buk.bukin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.buk.bukin.diagnostics.BleDiagnosticsRoute
import com.buk.bukin.feature.checkin.CheckInRoute
import com.buk.bukin.feature.host.HostRoute
import com.buk.bukin.feature.host.ManualRegistrationRoute
import com.buk.bukin.feature.host.RosterRoute
import com.buk.bukin.feature.onboarding.OnboardingRoute
import com.buk.bukin.ui.IdentityPreferences
import com.buk.bukin.ui.NameEntryRoute
import com.buk.bukin.ui.RolePickerScreen
import com.buk.bukin.ui.SessionPickerRoute

/**
 * Root navigation. Glue only — every screen is owned by its feature module, except the two
 * that both roles need (name entry and the session list), which live in `:app` for the same
 * reason the role picker does: features may never depend on each other.
 *
 * @param startKey where the app opens, decided once in `MainActivity`.
 */
@Composable
fun BukInNavDisplay(
    startKey: BukInKey,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(startKey)
    val context = LocalContext.current

    // Read once and kept here so re-entering the name screen updates every downstream
    // screen without each of them touching SharedPreferences on its own.
    var identidad by remember { mutableStateOf(IdentityPreferences(context).colaborador) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<BukInKey.Onboarding> {
                // Replace rather than push: onboarding is shown once, and backing into it
                // from what follows would be asking the same thing twice.
                OnboardingRoute(
                    onFinished = { backStack.replaceAllWith(BukInKey.NameEntry) },
                )
            }

            entry<BukInKey.NameEntry> {
                NameEntryRoute(
                    onIdentified = { colaborador ->
                        identidad = colaborador
                        backStack.replaceAllWith(BukInKey.RolePicker)
                    },
                )
            }

            entry<BukInKey.RolePicker> {
                RolePickerScreen(
                    onCollaborator = {
                        backStack.add(BukInKey.SessionPicker(isHost = false))
                    },
                    onHost = { backStack.add(BukInKey.SessionPicker(isHost = true)) },
                    onDiagnostics = { backStack.add(BukInKey.Diagnostics) },
                )
            }

            entry<BukInKey.SessionPicker> { key ->
                SessionPickerRoute(
                    isHost = key.isHost,
                    nombre = identidad?.nombre.orEmpty(),
                    onOpenCheckIn = { backStack.add(BukInKey.CheckIn(it)) },
                    onOpenHost = { backStack.add(BukInKey.Host(it)) },
                    onChangeName = { backStack.add(BukInKey.NameEntry) },
                )
            }

            entry<BukInKey.CheckIn> { key ->
                CheckInRoute(
                    instanciaId = key.instanciaId,
                    colaboradorId = identidad?.id.orEmpty(),
                    // Check-in used to be a dead end. With a session list in front of it,
                    // getting back to pick another session has to be possible.
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<BukInKey.Host> { key ->
                HostRoute(
                    instanciaId = key.instanciaId,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenRoster = { backStack.add(BukInKey.Roster(key.instanciaId)) },
                    onOpenManual = {
                        backStack.add(BukInKey.ManualRegistration(key.instanciaId))
                    },
                )
            }

            entry<BukInKey.Roster> { key ->
                RosterRoute(
                    instanciaId = key.instanciaId,
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<BukInKey.ManualRegistration> { key ->
                ManualRegistrationRoute(
                    instanciaId = key.instanciaId,
                    hostId = identidad?.id.orEmpty(),
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<BukInKey.Diagnostics> {
                BleDiagnosticsRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

private fun <T : NavKey> NavBackStack<T>.replaceAllWith(key: T) {
    clear()
    add(key)
}
