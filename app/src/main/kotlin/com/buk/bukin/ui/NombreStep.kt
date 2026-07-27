package com.buk.bukin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.NombreForm
import com.buk.bukin.domain.model.Colaborador

/**
 * Onboarding step 4, hosted by `:app` and handed to `:features:onboarding` as a slot.
 *
 * The module graph is the whole reason this file exists: a feature may not depend on the
 * app, but identity lives in the app, and **the alternative — a second copy of the call
 * that issues an identity — is worse than a slot parameter.** So the pager owns the page
 * and this owns the logic, and `NameEntryViewModel` is reused exactly as the standalone
 * screen uses it.
 */
@Composable
fun NombreStep(
    onIdentified: (Colaborador) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NameEntryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var nombre by rememberSaveable { mutableStateOf("") }

    NombreForm(
        nombre = nombre,
        onNombreChange = { nombre = it },
        enviando = uiState.enviando,
        error = uiState.error,
        onSubmit = { viewModel.identificar(nombre, onIdentified) },
        modifier = modifier,
        // The page already carries the headline and the explanation.
        titulo = false,
        submitLabel = stringResource(R.string.onboarding_finish),
    )
}
