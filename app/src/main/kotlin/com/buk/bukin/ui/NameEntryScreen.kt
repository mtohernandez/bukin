package com.buk.bukin.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.data.BukInRepository
import com.buk.bukin.designsystem.component.BukScreen
import com.buk.bukin.designsystem.component.NombreForm
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.domain.model.Colaborador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Identity, such as it is.
 *
 * The name is sent to `identificar_colaborador`, which is find-or-create on the normalized
 * name, so reinstalling returns the same person rather than a duplicate.
 *
 * This view model has **two hosts**: this screen, and onboarding step 4. The form is shared
 * and so is the logic — first run absorbing the name question must not mean a second copy
 * of the call that issues an identity.
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

/**
 * The standalone change-name screen.
 *
 * It survives unit 11 rather than being folded away: the session list offers "No soy X" and
 * has to land somewhere, so the form is used in two places and the destination stays.
 */
@Composable
fun NameEntryRoute(
    onIdentified: (Colaborador) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: NameEntryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var nombre by rememberSaveable { mutableStateOf("") }

    BukScreen(modifier = modifier, onBack = onBack, footer = true, footerMicrocopy = false) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NombreForm(
                nombre = nombre,
                onNombreChange = { nombre = it },
                enviando = uiState.enviando,
                error = uiState.error,
                onSubmit = { viewModel.identificar(nombre, onIdentified) },
            )
            Spacer(Modifier.height(BukSpacing.xl))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun NameEntryPreview() {
    BukInTheme { NameEntryRoute(onIdentified = {}) }
}
