package com.buk.bukin.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.BukInFooter
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.domain.model.Colaborador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The whole of identity in v1: a person types their name.
 *
 * Deliberately not a list of everyone's names to pick from. Showing one person the roster of
 * their colleagues so they can tap whichever they like is worse than asking them to type —
 * it hands out the names and still proves nothing. Typing proves nothing either, which is
 * the documented v1 cut, but it does not also leak.
 *
 * The name is sent to `identificar_colaborador`, which is find-or-create on the normalized
 * name, so reinstalling returns the same person rather than a duplicate.
 */
class NameEntryViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val enviando: Boolean = false,
        val error: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun identificar(nombre: String, onDone: (Colaborador) -> Unit) {
        if (nombre.isBlank() || _uiState.value.enviando) return
        _uiState.update { it.copy(enviando = true, error = false) }
        viewModelScope.launch {
            BukInRepository.identificar(nombre).fold(
                onSuccess = { colaborador ->
                    IdentityPreferences(getApplication()).colaborador = colaborador
                    _uiState.update { it.copy(enviando = false) }
                    onDone(colaborador)
                },
                onFailure = { _uiState.update { it.copy(enviando = false, error = true) } },
            )
        }
    }
}

@Composable
fun NameEntryRoute(
    onIdentified: (Colaborador) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NameEntryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var nombre by rememberSaveable { mutableStateOf("") }

    NameEntryScreen(
        nombre = nombre,
        onNombreChange = { nombre = it },
        enviando = uiState.enviando,
        error = uiState.error,
        onContinue = { viewModel.identificar(nombre, onIdentified) },
        modifier = modifier,
    )
}

@Composable
fun NameEntryScreen(
    nombre: String,
    onNombreChange: (String) -> Unit,
    enviando: Boolean,
    error: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = BukSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.nombre_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(BukSpacing.sm))
        Text(
            text = stringResource(R.string.nombre_body),
            style = MaterialTheme.typography.bodyMedium,
            color = BukInkMuted,
        )
        Spacer(Modifier.height(BukSpacing.lg))

        OutlinedTextField(
            value = nombre,
            onValueChange = onNombreChange,
            label = { Text(stringResource(R.string.nombre_label)) },
            singleLine = true,
            enabled = !enviando,
            isError = error,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onContinue() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error) {
            Spacer(Modifier.height(BukSpacing.sm))
            Text(
                text = stringResource(R.string.sesiones_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(BukSpacing.lg))

        // Disabled while in flight is the whole in-progress affordance. A spinner would need
        // a hardcoded size, which only :core:designsystem is allowed to hold.
        Button(
            onClick = onContinue,
            enabled = nombre.isNotBlank() && !enviando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.nombre_continue))
        }

        Spacer(Modifier.height(BukSpacing.xl))
        BukInFooter()
    }
}

@Preview(showBackground = true)
@Composable
private fun NameEntryPreview() {
    BukInTheme {
        NameEntryScreen("Ana Restrepo", {}, enviando = false, error = false, onContinue = {})
    }
}
