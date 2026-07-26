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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukInFooter
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccess
import com.buk.bukin.domain.model.Colaborador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Registering someone by hand.
 *
 * This is the documented fallback for a phone that is dead, incompatible, or simply out of
 * battery, and it is what closes the biggest hole in a BLE-only design — without it, anyone
 * whose hardware fails has no way to be marked present at all.
 *
 * No code is checked because no radio is involved. The host vouching is the evidence, so
 * the row records who vouched in `atestiguado_por_id`.
 */
class ManualRegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private var instanciaId = 0

    private val _colaboradores = MutableStateFlow<List<Colaborador>>(emptyList())
    val colaboradores: StateFlow<List<Colaborador>> = _colaboradores.asStateFlow()

    fun bind(id: Int) {
        if (instanciaId == id) return
        instanciaId = id
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            BukInRepository.listarColaboradores(instanciaId)
                .onSuccess { _colaboradores.value = it }
        }
    }

    fun registrar(colaboradorId: String, hostId: String) {
        viewModelScope.launch {
            BukInRepository.registrarManual(instanciaId, colaboradorId, hostId)
            // Optimism is not worth it here: the host wants to see it stuck.
            refresh()
        }
    }
}

@Composable
fun ManualRegistrationRoute(
    instanciaId: Int,
    hostId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManualRegistrationViewModel = viewModel(),
) {
    LaunchedEffect(instanciaId) { viewModel.bind(instanciaId) }
    val colaboradores by viewModel.colaboradores.collectAsStateWithLifecycle()

    ManualRegistrationScreen(
        colaboradores = colaboradores,
        onRegistrar = { viewModel.registrar(it, hostId) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ManualRegistrationScreen(
    colaboradores: List<Colaborador>,
    onRegistrar: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var busqueda by rememberSaveable { mutableStateOf("") }

    // Filtering on device rather than in a query: the whole list is already here, and a
    // round trip per keystroke would be slower and no more correct.
    val visibles = remember(colaboradores, busqueda) {
        if (busqueda.isBlank()) {
            colaboradores
        } else {
            colaboradores.filter { it.nombre.contains(busqueda.trim(), ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
    ) {
        Spacer(Modifier.height(BukSpacing.md))
        Text(
            text = stringResource(R.string.manual_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BukSpacing.sm))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text(stringResource(R.string.manual_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(BukSpacing.sm))

        if (visibles.isEmpty()) {
            Text(
                text = stringResource(R.string.manual_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BukSpacing.xs),
        ) {
            items(visibles, key = { it.id }) { colaborador ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = BukSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = colaborador.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (colaborador.yaRegistrado) {
                        Text(
                            text = stringResource(R.string.manual_done),
                            style = MaterialTheme.typography.labelLarge,
                            color = BukSuccess,
                        )
                    } else {
                        TextButton(onClick = { onRegistrar(colaborador.id) }) {
                            Text(stringResource(R.string.manual_register))
                        }
                    }
                }
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

@Preview(showBackground = true)
@Composable
private fun ManualRegistrationPreview() {
    BukInTheme {
        ManualRegistrationScreen(
            colaboradores = listOf(
                Colaborador("1", "Ana Restrepo", yaRegistrado = true),
                Colaborador("2", "Carlos Mejía"),
                Colaborador("3", "Diana Osorio"),
            ),
            onRegistrar = {},
            onBack = {},
        )
    }
}
