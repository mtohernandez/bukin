package com.buk.bukin.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.Avatar
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.BukSkeleton
import com.buk.bukin.designsystem.component.BukSkeletonHost
import com.buk.bukin.designsystem.component.InstanceCard
import com.buk.bukin.designsystem.component.InstanceCardState
import com.buk.bukin.designsystem.component.NoticeCard
import com.buk.bukin.designsystem.component.NoticeSeverity
import com.buk.bukin.designsystem.component.BukMinTouchTarget
import com.buk.bukin.designsystem.component.bukPressable
import com.buk.bukin.designsystem.component.instanciaHoraApertura
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
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
        /**
         * How many rows to draw as skeletons.
         *
         * The **last known count**, so the list does not resize when data lands. A skeleton
         * that is the wrong length is a layout shift with extra steps.
         */
        val ultimoConteo: Int = 3,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val colaboradorId: String?
        get() = IdentityPreferences(getApplication()).colaborador?.id

    fun refresh() {
        _uiState.update { it.copy(cargando = true, error = false) }
        viewModelScope.launch {
            reconciliarIdentidad()
            BukInRepository.listarInstancias(colaboradorId).fold(
                onSuccess = { lista ->
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            instancias = lista,
                            ultimoConteo = lista.size.coerceAtLeast(1),
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(cargando = false, error = true) } },
            )
        }
    }

    /**
     * Re-issues the remembered name and stores whatever id comes back.
     *
     * `identificar_colaborador` is find-or-create on the normalized name, so this is a no-op
     * that returns the same uuid — until the row it refers to is gone. Then it heals.
     *
     * That is not hypothetical: reseeding the database between demos leaves every installed
     * phone holding an id that no longer exists, and the only symptom is a foreign key
     * violation at the moment someone tries to check in. Costs one call on a screen that is
     * already making one, and it happens before any write can fail.
     */
    private suspend fun reconciliarIdentidad() {
        val prefs = IdentityPreferences(getApplication())
        val actual = prefs.colaborador ?: return
        BukInRepository.identificar(actual.nombre).onSuccess { fresco ->
            if (fresco.id != actual.id) prefs.colaborador = fresco
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
    avatarPath: String?,
    onOpenCheckIn: (Int) -> Unit,
    onOpenHost: (Int) -> Unit,
    onChangeName: () -> Unit,
    onOpenAsistencia: () -> Unit,
    onAvatarPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionPickerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-fetch every time this screen comes back to the front: someone returning from a
    // check-in must see their own arrival reflected, and `activa` is time-sensitive.
    LaunchedEffect(Unit) { viewModel.refresh() }

    SessionPickerScreen(
        isHost = isHost,
        nombre = nombre,
        avatarPath = avatarPath,
        uiState = uiState,
        onRetry = viewModel::refresh,
        onInscribir = viewModel::inscribir,
        onCheckIn = onOpenCheckIn,
        onHost = onOpenHost,
        onCrear = { n, d -> viewModel.crearInstancia(n, d, onOpenHost) },
        onChangeName = onChangeName,
        onOpenAsistencia = onOpenAsistencia,
        onAvatarPicked = onAvatarPicked,
        modifier = modifier,
    )
}

@Composable
fun SessionPickerScreen(
    isHost: Boolean,
    nombre: String,
    avatarPath: String?,
    uiState: SessionPickerViewModel.UiState,
    onRetry: () -> Unit,
    onInscribir: (Int) -> Unit,
    onCheckIn: (Int) -> Unit,
    onHost: (Int) -> Unit,
    onCrear: (String, Int) -> Unit,
    onChangeName: () -> Unit,
    onOpenAsistencia: () -> Unit,
    onAvatarPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creando by rememberSaveable { mutableStateOf(false) }
    var perfilAbierto by rememberSaveable { mutableStateOf(false) }
    val ahora = remember { Instant.now() }

    // Past instances stop competing with actionable ones. This also fixes a present-day
    // oddity: a finished session rendered as a card that looked tappable and was not.
    val (historial, proximas) = remember(uiState.instancias, ahora) {
        uiState.instancias.partition { it.fechaFin.isBefore(ahora) }
    }

    BukScreen(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isHost) {
                        stringResource(R.string.sesiones_title_host)
                    } else {
                        stringResource(R.string.sesiones_saludo, nombre)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = BukInk,
                )
                if (isHost) {
                    Text(
                        text = stringResource(R.string.sesiones_title_host_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = BukInkMuted,
                    )
                }
            }
            if (!isHost) {
                // The avatar is the affordance for everything about you: your photo, your
                // name, and your attendance history.
                Avatar(
                    nombre = nombre,
                    photoPath = avatarPath,
                    onClick = { perfilAbierto = true },
                )
            }
        }

        Spacer(Modifier.height(BukSpacing.md))

        if (isHost) {
            Box(
                Modifier
                    .heightIn(min = BukMinTouchTarget)
                    .clip(BukShape.lg)
                    .background(BukBlue)
                    .bukPressable(onClick = { creando = true })
                    .padding(horizontal = BukSpacing.md2, vertical = BukSpacing.sm2),
            ) {
                Text(
                    text = stringResource(R.string.sesiones_crear),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(BukSpacing.md))
        }

        BukSkeletonHost {
            when {
                uiState.error -> NoticeCard(
                    title = stringResource(R.string.sesiones_error),
                    body = stringResource(R.string.error_save_failed_body),
                    severity = NoticeSeverity.Informational,
                    actionLabel = stringResource(R.string.sesiones_retry),
                    onAction = onRetry,
                )

                // Under a second, show nothing at all — a skeleton that flashes for 200 ms
                // is worse than no skeleton. Past that, rows shaped like the real rows.
                uiState.cargando && uiState.instancias.isEmpty() ->
                    SkeletonList(uiState.ultimoConteo)

                uiState.instancias.isEmpty() -> Text(
                    text = stringResource(R.string.sesiones_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BukInkMuted,
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BukSpacing.sm2),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = BukSpacing.md,
                    ),
                ) {
                    if (proximas.isNotEmpty()) {
                        item(key = "h-proximas") { SectionHeader(stringResource(R.string.sesiones_proximas)) }
                        items(proximas, key = { it.id }) { instancia ->
                            SessionRow(instancia, isHost, ahora, onInscribir, onCheckIn, onHost)
                        }
                    }
                    if (historial.isNotEmpty()) {
                        item(key = "h-historial") { SectionHeader(stringResource(R.string.sesiones_historial)) }
                        items(historial, key = { it.id }) { instancia ->
                            SessionRow(instancia, isHost, ahora, onInscribir, onCheckIn, onHost)
                        }
                    }
                }
            }
        }
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

    if (perfilAbierto) {
        PerfilSheet(
            nombre = nombre,
            avatarPath = avatarPath,
            onDismiss = { perfilAbierto = false },
            onChangeName = { perfilAbierto = false; onChangeName() },
            onOpenAsistencia = { perfilAbierto = false; onOpenAsistencia() },
            onAvatarPicked = onAvatarPicked,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BukInkMuted,
        modifier = Modifier.padding(top = BukSpacing.sm, bottom = BukSpacing.xs),
    )
}

@Composable
private fun SkeletonList(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(BukSpacing.sm2)) {
        repeat(count) {
            BukSkeleton(
                Modifier
                    .fillMaxWidth()
                    .height(SkeletonRowHeight),
                shape = BukShape.xl,
            )
        }
    }
}

/** The height an `InstanceCard` settles at, so nothing shifts when the real rows land. */
private val SkeletonRowHeight = 140.dp

@Composable
private fun SessionRow(
    instancia: Instancia,
    isHost: Boolean,
    ahora: Instant,
    onInscribir: (Int) -> Unit,
    onCheckIn: (Int) -> Unit,
    onHost: (Int) -> Unit,
) {
    // `activa` is checked before `inscrito` on purpose, and this order is a session-3 bug
    // fix rather than an accident: someone who turns up to a session they never signed up
    // for *is* the walk-in case, and refusing to let them mark attendance until they first
    // enrol is bureaucracy the room does not have time for. The server records them as
    // WALK_IN; signing in ahead of time is what makes someone PRE_INSCRITO.
    // Do not reorder this while restyling.
    val terminada = instancia.fechaFin.isBefore(ahora)

    val state: InstanceCardState
    val labelRes: Int
    val onClick: (() -> Unit)?

    when {
        isHost -> {
            state = if (instancia.activa) InstanceCardState.ActiveNow else InstanceCardState.NotEnrolled
            labelRes = R.string.sesiones_abrir
            onClick = { onHost(instancia.id) }
        }
        instancia.asistencia -> {
            state = InstanceCardState.Marked
            labelRes = R.string.sesiones_ya_marcaste
            onClick = null
        }
        instancia.activa -> {
            state = InstanceCardState.ActiveNow
            labelRes = R.string.sesiones_marcar
            onClick = { onCheckIn(instancia.id) }
        }
        terminada -> {
            state = InstanceCardState.Finished
            labelRes = R.string.sesiones_cerrada
            onClick = null
        }
        !instancia.inscrito -> {
            state = InstanceCardState.NotEnrolled
            labelRes = R.string.sesiones_inscribirme
            onClick = { onInscribir(instancia.id) }
        }
        else -> {
            state = InstanceCardState.Enrolled
            labelRes = R.string.sesiones_esperando
            onClick = null
        }
    }

    val label = if (labelRes == R.string.sesiones_esperando) {
        stringResource(labelRes, instanciaHoraApertura(instancia))
    } else {
        stringResource(labelRes)
    }

    InstanceCard(
        instancia = instancia,
        state = state,
        actionLabel = label,
        onClick = onClick,
    )
}

@Composable
private fun CrearSesionDialog(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var duracion by rememberSaveable { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BukShape.xxl,
        title = { Text(stringResource(R.string.sesiones_crear_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.sesiones_crear_nombre)) },
                    singleLine = true,
                    shape = BukShape.lg,
                )
                Spacer(Modifier.height(BukSpacing.sm))
                OutlinedTextField(
                    value = duracion,
                    onValueChange = { new -> duracion = new.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.sesiones_crear_duracion)) },
                    singleLine = true,
                    shape = BukShape.lg,
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

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun SessionPickerPreview() {
    BukInTheme {
        SessionPickerScreen(
            isHost = false,
            nombre = "Ana Restrepo",
            avatarPath = null,
            uiState = SessionPickerViewModel.UiState(
                cargando = false,
                instancias = listOf(
                    Instancia(
                        id = 1,
                        cursoNombre = "Manejo de alimentos",
                        duracionMinutos = 120,
                        fechaInicio = Instant.now().plusSeconds(3600),
                        fechaFin = Instant.now().plusSeconds(10800),
                        activa = true,
                        abierta = true,
                        inscrito = true,
                        asistencia = false,
                    ),
                    Instancia(
                        id = 2,
                        cursoNombre = "Seguridad en el trabajo",
                        duracionMinutos = 90,
                        fechaInicio = Instant.now().minusSeconds(200_000),
                        fechaFin = Instant.now().minusSeconds(190_000),
                        activa = false,
                        abierta = false,
                        inscrito = true,
                        asistencia = true,
                    ),
                ),
            ),
            onRetry = {},
            onInscribir = {},
            onCheckIn = {},
            onHost = {},
            onCrear = { _, _ -> },
            onChangeName = {},
            onOpenAsistencia = {},
            onAvatarPicked = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun SessionPickerLoadingPreview() {
    BukInTheme {
        SessionPickerScreen(
            isHost = false,
            nombre = "Ana Restrepo",
            avatarPath = null,
            uiState = SessionPickerViewModel.UiState(cargando = true, ultimoConteo = 3),
            onRetry = {},
            onInscribir = {},
            onCheckIn = {},
            onHost = {},
            onCrear = { _, _ -> },
            onChangeName = {},
            onOpenAsistencia = {},
            onAvatarPicked = {},
        )
    }
}
