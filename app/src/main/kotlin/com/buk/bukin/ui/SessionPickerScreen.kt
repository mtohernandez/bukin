package com.buk.bukin.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukInFooter
import com.buk.bukin.designsystem.component.instanciaSubtitulo
import com.buk.bukin.designsystem.component.instanciaHoraApertura
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.domain.model.Instancia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The session list, shared by both roles.
 *
 * It lives in `:app` rather than a feature module for the same reason the role picker does:
 * both `:features:checkin` and `:features:host` need it, and features may never depend on
 * each other. It owns no domain logic — every decision it renders (`activa`, `inscrito`,
 * `asistencia`) is computed by the server.
 *
 * A collaborator signs into a session ahead of time and then waits for its hour. Whether a
 * session can be checked into is decided by the clock, not by whether a host has pressed a
 * button — which is why `activa` and `abierta` are separate flags.
 */
class SessionPickerViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val cargando: Boolean = true,
        val error: Boolean = false,
        val instancias: List<Instancia> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val colaboradorId: String?
        get() = IdentityPreferences(getApplication()).colaborador?.id

    fun refresh() {
        _uiState.update { it.copy(cargando = true, error = false) }
        viewModelScope.launch {
            BukInRepository.listarInstancias(colaboradorId).fold(
                onSuccess = { lista ->
                    _uiState.update { it.copy(cargando = false, instancias = lista) }
                },
                onFailure = { _uiState.update { it.copy(cargando = false, error = true) } },
            )
        }
    }

    /** Signing in ahead of time. Refreshes so the row immediately reads "estás inscrito". */
    fun inscribir(instanciaId: Int) {
        val id = colaboradorId ?: return
        viewModelScope.launch {
            BukInRepository.inscribir(instanciaId, id)
            refresh()
        }
    }

    fun crearInstancia(nombre: String, duracionMinutos: Int, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            BukInRepository.crearInstancia(nombre, duracionMinutos).fold(
                onSuccess = { onCreated(it) },
                onFailure = { _uiState.update { s -> s.copy(error = true) } },
            )
        }
    }
}

@Composable
fun SessionPickerRoute(
    isHost: Boolean,
    nombre: String,
    onOpenCheckIn: (Int) -> Unit,
    onOpenHost: (Int) -> Unit,
    onChangeName: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionPickerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-fetch every time this screen comes back to the front: someone returning from a
    // check-in must see their own arrival reflected, and `activa` is time-sensitive.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    SessionPickerScreen(
        isHost = isHost,
        nombre = nombre,
        uiState = uiState,
        onRetry = viewModel::refresh,
        onInscribir = viewModel::inscribir,
        onCheckIn = onOpenCheckIn,
        onHost = onOpenHost,
        onCrear = { n, d -> viewModel.crearInstancia(n, d, onOpenHost) },
        onChangeName = onChangeName,
        modifier = modifier,
    )
}

@Composable
fun SessionPickerScreen(
    isHost: Boolean,
    nombre: String,
    uiState: SessionPickerViewModel.UiState,
    onRetry: () -> Unit,
    onInscribir: (Int) -> Unit,
    onCheckIn: (Int) -> Unit,
    onHost: (Int) -> Unit,
    onCrear: (String, Int) -> Unit,
    onChangeName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var creando by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
    ) {
        Spacer(Modifier.height(BukSpacing.md))
        Text(
            text = stringResource(
                if (isHost) R.string.sesiones_title_host else R.string.sesiones_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BukSpacing.sm))

        if (isHost) {
            Button(onClick = { creando = true }) {
                Text(stringResource(R.string.sesiones_crear))
            }
        } else {
            TextButton(onClick = onChangeName) {
                Text(
                    text = stringResource(R.string.sesiones_cambiar_nombre, nombre),
                    color = BukInkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(BukSpacing.sm))

        when {
            uiState.error -> Column {
                Text(
                    text = stringResource(R.string.sesiones_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BukInkMuted,
                )
                Spacer(Modifier.height(BukSpacing.sm))
                Button(onClick = onRetry) { Text(stringResource(R.string.sesiones_retry)) }
            }

            uiState.instancias.isEmpty() && !uiState.cargando -> Text(
                text = stringResource(R.string.sesiones_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BukSpacing.sm),
            ) {
                items(uiState.instancias, key = { it.id }) { instancia ->
                    SessionRow(
                        instancia = instancia,
                        isHost = isHost,
                        onInscribir = { onInscribir(instancia.id) },
                        onCheckIn = { onCheckIn(instancia.id) },
                        onHost = { onHost(instancia.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(BukSpacing.sm))
        BukInFooter()
        Spacer(Modifier.height(BukSpacing.md))
    }

    if (creando) {
        CrearSesionDialog(
            onDismiss = { creando = false },
            onConfirm = { nombreCurso, duracion ->
                creando = false
                onCrear(nombreCurso, duracion)
            },
        )
    }
}

@Composable
private fun SessionRow(
    instancia: Instancia,
    isHost: Boolean,
    onInscribir: () -> Unit,
    onCheckIn: () -> Unit,
    onHost: () -> Unit,
) {
    // Host can always open a room; a collaborator's action depends on the clock. Only one of
    // these is ever true.
    //
    // `activa` is checked before `inscrito` on purpose. Someone who turns up to a session
    // they never signed up for is exactly what a walk-in is, and refusing to let them mark
    // attendance until they first enrol would be bureaucracy the room does not have time
    // for. The server records them as WALK_IN; signing in ahead of time is what makes
    // someone PRE_INSCRITO.
    val accion: Pair<Int, (() -> Unit)?> = when {
        isHost -> R.string.sesiones_abrir to onHost
        instancia.asistencia -> R.string.sesiones_ya_marcaste to null
        instancia.activa -> R.string.sesiones_marcar to onCheckIn
        instancia.fechaFin.isBefore(Instant.now()) -> R.string.sesiones_cerrada to null
        !instancia.inscrito -> R.string.sesiones_inscribirme to onInscribir
        else -> R.string.sesiones_esperando to null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (accion.second != null) Modifier.clickable { accion.second?.invoke() } else Modifier),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(BukSpacing.md)) {
            Text(
                text = instancia.cursoNombre,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(BukSpacing.xs))
            Text(
                text = instanciaSubtitulo(instancia),
                style = MaterialTheme.typography.bodySmall,
                color = BukInkMuted,
            )
            Spacer(Modifier.height(BukSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (accion.first == R.string.sesiones_esperando) {
                        stringResource(accion.first, instanciaHoraApertura(instancia))
                    } else {
                        stringResource(accion.first)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (accion.second != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        BukInkMuted
                    },
                )
            }
        }
    }
}

@Composable
private fun CrearSesionDialog(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var duracion by rememberSaveable { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sesiones_crear_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.sesiones_crear_nombre)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(BukSpacing.sm))
                OutlinedTextField(
                    value = duracion,
                    onValueChange = { new -> duracion = new.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.sesiones_crear_duracion)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nombre, duracion.toIntOrNull() ?: 60) },
                enabled = nombre.isNotBlank(),
            ) {
                Text(stringResource(R.string.sesiones_crear_confirmar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sesiones_cancelar)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionPickerPreview() {
    BukInTheme {
        SessionPickerScreen(
            isHost = false,
            nombre = "Ana Restrepo",
            uiState = SessionPickerViewModel.UiState(
                cargando = false,
                instancias = listOf(
                    Instancia(
                        id = 1,
                        cursoNombre = "Manejo de alimentos",
                        duracionMinutos = 120,
                        fechaInicio = Instant.parse("2026-07-13T15:00:00Z"),
                        fechaFin = Instant.parse("2026-07-13T17:00:00Z"),
                        activa = true,
                        abierta = true,
                        inscrito = true,
                        asistencia = false,
                    ),
                ),
            ),
            onRetry = {},
            onInscribir = {},
            onCheckIn = {},
            onHost = {},
            onCrear = { _, _ -> },
            onChangeName = {},
        )
    }
}
