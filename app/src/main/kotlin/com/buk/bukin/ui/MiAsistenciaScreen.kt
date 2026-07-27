package com.buk.bukin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.BukSkeleton
import com.buk.bukin.designsystem.component.BukSkeletonHost
import com.buk.bukin.designsystem.component.NoticeCard
import com.buk.bukin.designsystem.component.instanciaSubtitulo
import com.buk.bukin.designsystem.theme.BukBorder
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukStroke
import com.buk.bukin.designsystem.theme.BukSuccessInk
import com.buk.bukin.designsystem.theme.BukSurface
import com.buk.bukin.domain.model.Instancia
import java.time.Instant

/**
 * The receipt.
 *
 * This answers the loudest unaddressed complaint in `docs/feedback.md`:
 *
 * > *"There is no internal panel or profile menu where a worker can verify their history of
 * > past punch-ins or check if a punch synchronized correctly."*
 *
 * **It needs no backend work at all.** `listar_instancias` has no `WHERE` clause — it
 * already returns every instance, past and future, ordered by `fecha_inicio desc`, each
 * carrying this collaborator's `inscrito` and `asistencia` flags. The data was on the device
 * the whole time and the app was flattening it into one undifferentiated list. If this file
 * ever finds itself editing `supabase/`, something has been misread.
 *
 * The point of it is availability: "did it register?" has to be answerable at any time, not
 * only in the two seconds after the check mark.
 */
@Composable
fun MiAsistenciaRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionPickerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    MiAsistenciaScreen(
        instancias = uiState.instancias,
        cargando = uiState.cargando,
        error = uiState.error,
        onRetry = viewModel::refresh,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun MiAsistenciaScreen(
    instancias: List<Instancia>,
    cargando: Boolean,
    error: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val registradas = remember(instancias) { instancias.filter { it.asistencia } }

    BukScreen(
        modifier = modifier,
        title = stringResource(R.string.asistencia_title),
        onBack = onBack,
    ) {
        BukSkeletonHost {
            when {
                error -> NoticeCard(
                    title = stringResource(R.string.sesiones_error),
                    body = stringResource(R.string.error_save_failed_body),
                    actionLabel = stringResource(R.string.sesiones_retry),
                    onAction = onRetry,
                )

                cargando && instancias.isEmpty() -> Column(
                    verticalArrangement = Arrangement.spacedBy(BukSpacing.sm2),
                ) {
                    repeat(3) {
                        BukSkeleton(
                            Modifier
                                .fillMaxWidth()
                                .height(RowHeight),
                            shape = BukShape.xl,
                        )
                    }
                }

                registradas.isEmpty() -> EmptyState()

                else -> {
                    Text(
                        text = if (registradas.size == 1) {
                            stringResource(R.string.asistencia_subtitulo, registradas.size)
                        } else {
                            stringResource(R.string.asistencia_subtitulo_plural, registradas.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BukInkMuted,
                    )
                    Spacer(Modifier.height(BukSpacing.md))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(BukSpacing.sm2),
                    ) {
                        items(registradas, key = { it.id }) { AsistenciaRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AsistenciaRow(instancia: Instancia) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BukShape.xl)
            .background(BukSurface)
            .padding(BukSpacing.md2),
    ) {
        Text(
            text = instancia.cursoNombre,
            style = MaterialTheme.typography.titleMedium,
            color = BukInk,
        )
        Spacer(Modifier.height(BukSpacing.xs))
        Text(
            text = instanciaSubtitulo(instancia),
            style = MaterialTheme.typography.bodySmall,
            color = BukInkMuted,
        )
        Spacer(Modifier.height(BukSpacing.sm2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(stringResource(R.string.asistencia_registrada))
            // Someone who attended a session they never enrolled in is a walk-in, and the
            // data already distinguishes it. Saying so is the difference between a list and
            // a record.
            if (!instancia.inscrito) {
                Spacer(Modifier.height(BukSpacing.xs))
                Text(
                    text = stringResource(R.string.asistencia_walk_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = BukInkMuted,
                    modifier = Modifier.padding(start = BukSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun Badge(label: String) {
    Box(
        Modifier
            .clip(BukShape.full)
            .background(BukSuccessInk.copy(alpha = 0.12f))
            .padding(horizontal = BukSpacing.sm2, vertical = BukSpacing.xs2),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = BukSuccessInk,
        )
    }
}

/** Not a bare sentence: what is missing, and what to do about it. */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = BukSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(EmptyArtHeight)
                .clip(BukShape.xxl)
                .background(BukSurface),
        )
        Spacer(Modifier.height(BukSpacing.lg))
        Text(
            text = stringResource(R.string.asistencia_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = BukInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BukSpacing.sm))
        Text(
            text = stringResource(R.string.asistencia_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = BukInkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private val RowHeight = 108.dp
private val EmptyArtHeight = 120.dp

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun MiAsistenciaPreview() {
    BukInTheme {
        MiAsistenciaScreen(
            instancias = listOf(
                Instancia(
                    id = 1,
                    cursoNombre = "Manejo de alimentos",
                    duracionMinutos = 120,
                    fechaInicio = Instant.parse("2026-07-13T15:00:00Z"),
                    fechaFin = Instant.parse("2026-07-13T17:00:00Z"),
                    activa = false,
                    abierta = false,
                    inscrito = true,
                    asistencia = true,
                ),
                Instancia(
                    id = 2,
                    cursoNombre = "Seguridad en el trabajo",
                    duracionMinutos = 90,
                    fechaInicio = Instant.parse("2026-06-02T14:00:00Z"),
                    fechaFin = Instant.parse("2026-06-02T15:30:00Z"),
                    activa = false,
                    abierta = false,
                    inscrito = false,
                    asistencia = true,
                ),
            ),
            cargando = false,
            error = false,
            onRetry = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun MiAsistenciaEmptyPreview() {
    BukInTheme {
        MiAsistenciaScreen(
            instancias = emptyList(),
            cargando = false,
            error = false,
            onRetry = {},
            onBack = {},
        )
    }
}
