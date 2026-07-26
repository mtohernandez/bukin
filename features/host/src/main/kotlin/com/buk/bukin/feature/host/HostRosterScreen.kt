package com.buk.bukin.feature.host

import android.app.Application
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.buk.bukin.designsystem.component.InstanciaHora
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccess
import com.buk.bukin.domain.model.Asistente
import com.buk.bukin.domain.model.MetodoConfirmacion
import com.buk.bukin.domain.model.Origen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant

/**
 * The host's live roster.
 *
 * Polling rather than a realtime subscription: fewer moving parts, no extra dependency, and
 * a session lasts minutes. `SharingStarted.WhileSubscribed` is also what stops the polling
 * when the screen leaves the foreground — one operator instead of a timer and a teardown.
 *
 * This is the one query whose cost grows with attendance. At 300 people it returns 300 rows
 * every three seconds, which is why `listar_asistencia` selects only what this list renders.
 */
class RosterViewModel(application: Application) : AndroidViewModel(application) {

    private val instanciaId = MutableStateFlow(0)

    fun bind(id: Int) {
        instanciaId.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val asistentes: StateFlow<List<Asistente>> =
        instanciaId
            .flatMapLatest { id ->
                flow {
                    while (true) {
                        BukInRepository.listarAsistencia(id).onSuccess { emit(it) }
                        delay(POLL_MILLIS)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    private companion object {
        /** Fast enough that an arrival appears while the person is still at the door. */
        const val POLL_MILLIS = 3_000L
    }
}

@Composable
fun RosterRoute(
    instanciaId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RosterViewModel = viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(instanciaId) { viewModel.bind(instanciaId) }
    val asistentes by viewModel.asistentes.collectAsStateWithLifecycle()
    RosterScreen(asistentes = asistentes, onBack = onBack, modifier = modifier)
}

@Composable
fun RosterScreen(
    asistentes: List<Asistente>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val llegados = asistentes.count { it.fechaLlegada != null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
    ) {
        Spacer(Modifier.height(BukSpacing.md))
        Text(
            text = stringResource(R.string.roster_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BukSpacing.xs))
        Text(
            text = stringResource(R.string.roster_count, llegados, asistentes.size),
            style = MaterialTheme.typography.bodyMedium,
            color = BukInkMuted,
        )
        Spacer(Modifier.height(BukSpacing.md))

        if (asistentes.isEmpty()) {
            Text(
                text = stringResource(R.string.roster_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BukSpacing.xs),
        ) {
            items(asistentes, key = { it.colaboradorId }) { asistente ->
                AsistenteRow(asistente)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.host_back), color = BukInkMuted)
        }
        BukInFooter()
        Spacer(Modifier.height(BukSpacing.md))
    }
}

@Composable
private fun AsistenteRow(asistente: Asistente) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BukSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = asistente.nombre, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(BukSpacing.xs))
            Text(
                text = detalle(asistente),
                style = MaterialTheme.typography.bodySmall,
                color = BukInkMuted,
            )
        }
        asistente.fechaLlegada?.let {
            Text(
                text = InstanciaHora(it),
                style = MaterialTheme.typography.labelLarge,
                color = BukSuccess,
            )
        }
    }
}

/** The method it was recorded by, plus a flag for someone who never signed in beforehand. */
@Composable
private fun detalle(asistente: Asistente): String {
    if (asistente.fechaLlegada == null) return stringResource(R.string.roster_pendiente)
    val metodo = when (asistente.metodo) {
        MetodoConfirmacion.MANUAL -> stringResource(R.string.roster_metodo_manual)
        else -> stringResource(R.string.roster_metodo_ble)
    }
    return if (asistente.origen == Origen.WALK_IN) {
        "$metodo · ${stringResource(R.string.roster_walk_in)}"
    } else {
        metodo
    }
}

@Preview(showBackground = true)
@Composable
private fun RosterPreview() {
    BukInTheme {
        RosterScreen(
            asistentes = listOf(
                Asistente(
                    "1", "Ana Restrepo", Instant.parse("2026-07-13T15:02:00Z"),
                    MetodoConfirmacion.BLE, Origen.PRE_INSCRITO,
                ),
                Asistente(
                    "2", "Carlos Mejía", Instant.parse("2026-07-13T15:04:00Z"),
                    MetodoConfirmacion.MANUAL, Origen.WALK_IN,
                ),
                Asistente("3", "Diana Osorio", null, null, Origen.PRE_INSCRITO),
            ),
            onBack = {},
        )
    }
}
