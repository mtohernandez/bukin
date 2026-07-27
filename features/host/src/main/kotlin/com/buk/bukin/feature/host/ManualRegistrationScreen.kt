package com.buk.bukin.feature.host

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.buk.bukin.designsystem.component.BukMinTouchTarget
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.BukSkeleton
import com.buk.bukin.designsystem.component.BukSkeletonHost
import com.buk.bukin.designsystem.component.bukPressable
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSuccessInk
import com.buk.bukin.designsystem.theme.BukSurface
import com.buk.bukin.domain.model.Colaborador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Registering someone by hand.
 *
 * This is the documented fallback for a phone that is dead, incompatible, or simply out of
 * battery, and it is what closes the biggest hole in a BLE-only design — without it, anyone
 * whose hardware fails has no way to be marked present at all. It is also where every dead
 * end in the help sheet terminates, which is why it has to actually work.
 *
 * No code is checked because no radio is involved. The host vouching is the evidence, so
 * the row records who vouched in `atestiguado_por_id`.
 */
class ManualRegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private var instanciaId = 0

    private val _colaboradores = MutableStateFlow<List<Colaborador>?>(null)
    val colaboradores: StateFlow<List<Colaborador>?> = _colaboradores.asStateFlow()

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
    colaboradores: List<Colaborador>?,
    onRegistrar: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var busqueda by rememberSaveable { mutableStateOf("") }

    // Filtering on device rather than in a query: the whole list is already here, and a
    // round trip per keystroke would be slower and no more correct.
    val visibles = remember(colaboradores, busqueda) {
        val all = colaboradores.orEmpty()
        if (busqueda.isBlank()) {
            all
        } else {
            all.filter { it.nombre.contains(busqueda.trim(), ignoreCase = true) }
        }
    }

    BukScreen(
        modifier = modifier,
        title = stringResource(R.string.manual_title),
        onBack = onBack,
    ) {
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text(stringResource(R.string.manual_search)) },
            singleLine = true,
            shape = BukShape.lg,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(BukSpacing.md))

        BukSkeletonHost {
            when {
                colaboradores == null -> Column(
                    verticalArrangement = Arrangement.spacedBy(BukSpacing.sm),
                ) {
                    repeat(SkeletonRows) {
                        BukSkeleton(
                            Modifier
                                .fillMaxWidth()
                                .height(RowHeight),
                            shape = BukShape.xl,
                        )
                    }
                }

                visibles.isEmpty() -> Text(
                    text = stringResource(R.string.manual_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BukInkMuted,
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BukSpacing.sm),
                ) {
                    items(visibles, key = { it.id }) { ColaboradorRow(it, onRegistrar) }
                }
            }
        }
    }
}

@Composable
private fun ColaboradorRow(colaborador: Colaborador, onRegistrar: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BukShape.xl)
            .background(BukSurface)
            .padding(horizontal = BukSpacing.md, vertical = BukSpacing.sm2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(nombre = colaborador.nombre)
        Spacer(Modifier.width(BukSpacing.sm2))
        Text(
            text = colaborador.nombre,
            style = MaterialTheme.typography.titleSmall,
            color = BukInk,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(BukSpacing.sm))

        if (colaborador.yaRegistrado) {
            Text(
                text = stringResource(R.string.manual_done),
                style = MaterialTheme.typography.labelLarge,
                color = BukSuccessInk,
            )
        } else {
            val label = stringResource(R.string.manual_register)
            Box(
                modifier = Modifier
                    .heightIn(min = BukMinTouchTarget)
                    .clip(BukShape.full)
                    .background(BukBlue.copy(alpha = 0.10f))
                    .bukPressable(onClick = { onRegistrar(colaborador.id) }, onClickLabel = label)
                    .padding(horizontal = BukSpacing.md, vertical = BukSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = BukBlue,
                )
            }
        }
    }
}

private val RowHeight = 68.dp
private const val SkeletonRows = 6

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
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

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun ManualRegistrationLoadingPreview() {
    BukInTheme { ManualRegistrationScreen(colaboradores = null, onRegistrar = {}, onBack = {}) }
}
