package com.buk.bukin.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.buk.bukin.ui.MiAsistenciaRoute
import com.buk.bukin.ui.NameEntryRoute
import com.buk.bukin.ui.NombreStep
import com.buk.bukin.ui.RolePickerScreen
import com.buk.bukin.ui.SessionPickerRoute

/**
 * Root navigation. Glue only — every screen is owned by its feature module, except the ones
 * both roles need (the name form, the session list, the attendance history), which live in
 * `:app` for the same reason the role picker does: features may never depend on each other.
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
    var avatarPath by remember { mutableStateOf(IdentityPreferences(context).avatarPath) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        // Shared axis. Forward slides in from the trailing edge and scales up a touch;
        // back reverses it. **No fades** — `fadeIn`/`fadeOut` are banned in this codebase,
        // and a cross-dissolve between two screens is the single clearest tell that a
        // transition was picked from a default rather than designed.
        // Shared axis X, travelling the **full** width. An earlier version slid a fifth of
        // the width and scaled: the outgoing screen was still mostly on-screen when it was
        // cut, which reads as content vanishing rather than moving. Still no fade — a
        // cross-dissolve between screens is the clearest tell of a default transition.
        transitionSpec = {
            slideInHorizontally(tween(SHARED_AXIS_MS)) { it }
                .togetherWith(slideOutHorizontally(tween(SHARED_AXIS_MS)) { -it / 3 })
        },
        popTransitionSpec = {
            slideInHorizontally(tween(SHARED_AXIS_MS)) { -it / 3 }
                .togetherWith(slideOutHorizontally(tween(SHARED_AXIS_MS)) { it })
        },
        // The back gesture drives the same motion under the user's finger.
        predictivePopTransitionSpec = {
            slideInHorizontally(tween(SHARED_AXIS_MS)) { -it / 3 }
                .togetherWith(slideOutHorizontally(tween(SHARED_AXIS_MS)) { it })
        },
        entryProvider = entryProvider {
            entry<BukInKey.Onboarding> {
                // Four steps, the last of which is the name. Replace rather than push:
                // onboarding is shown once, and backing into it from what follows would be
                // asking the same thing twice.
                OnboardingRoute(
                    onFinished = { backStack.replaceAllWith(BukInKey.RolePicker) },
                    // `:app` owns identity, so it supplies the step. This is what lets the
                    // name form be one composable with two hosts without a feature module
                    // reaching into the app module.
                    nameStep = { onDone ->
                        NombreStep(
                            onIdentified = { colaborador ->
                                identidad = colaborador
                                onDone()
                            },
                        )
                    },
                )
            }

            entry<BukInKey.NameEntry> {
                NameEntryRoute(
                    onIdentified = { colaborador ->
                        identidad = colaborador
                        backStack.removeLastOrNull()
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<BukInKey.RolePicker> {
                RolePickerScreen(
                    onCollaborator = { backStack.add(BukInKey.SessionPicker(isHost = false)) },
                    onHost = { backStack.add(BukInKey.SessionPicker(isHost = true)) },
                    onDiagnostics = { backStack.add(BukInKey.Diagnostics) },
                )
            }

            entry<BukInKey.SessionPicker> { key ->
                SessionPickerRoute(
                    isHost = key.isHost,
                    nombre = identidad?.nombre.orEmpty(),
                    avatarPath = avatarPath,
                    onOpenCheckIn = { backStack.add(BukInKey.CheckIn(it)) },
                    onOpenHost = { backStack.add(BukInKey.Host(it)) },
                    onChangeName = { backStack.add(BukInKey.NameEntry) },
                    onOpenAsistencia = { backStack.add(BukInKey.MiAsistencia) },
                    onAvatarPicked = { path ->
                        IdentityPreferences(context).avatarPath = path
                        avatarPath = path
                    },
                )
            }

            entry<BukInKey.MiAsistencia> {
                MiAsistenciaRoute(onBack = { backStack.removeLastOrNull() })
            }

            entry<BukInKey.CheckIn> { key ->
                CheckInRoute(
                    instanciaId = key.instanciaId,
                    // Re-read on entry rather than reuse the value captured when the nav
                    // display was first composed. The session picker reconciles the stored
                    // id against the server before it lists anything, and a phone holding an
                    // id that no longer exists — every phone, after the database is reseeded
                    // — would otherwise carry the dead one all the way into the write.
                    colaboradorId = rememberColaboradorId(),
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
                    hostId = rememberColaboradorId(),
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<BukInKey.Diagnostics> {
                BleDiagnosticsRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}

/** The stored collaborator id, read fresh each time an entry is composed. */
@Composable
private fun rememberColaboradorId(): String {
    val context = LocalContext.current
    return remember(context) { IdentityPreferences(context).colaborador?.id.orEmpty() }
}

private fun <T : NavKey> NavBackStack<T>.replaceAllWith(key: T) {
    clear()
    add(key)
}

private const val SHARED_AXIS_MS = 320
