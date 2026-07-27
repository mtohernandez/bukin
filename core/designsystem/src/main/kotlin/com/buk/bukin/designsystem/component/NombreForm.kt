package com.buk.bukin.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukOpacity
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing

/**
 * "¿Cómo te llamas?" — the whole of identity in v1.
 *
 * **One composable, two hosts.** It is onboarding step 4, and it is the standalone
 * change-name screen the session list reaches through "No soy %1$s". Extracting it is what
 * makes first run a single flow without deleting a destination that is still needed.
 *
 * Deliberately not a list of everyone's names to pick from. Showing one person the roster
 * of their colleagues so they can tap whichever they like is worse than asking them to
 * type — it hands out the names and still proves nothing. Typing proves nothing either,
 * which is the documented v1 cut, but it does not also leak.
 *
 * @param titulo when false the form drops its own heading, because the host is already
 *   providing one — an onboarding page has a headline of its own.
 */
@Composable
fun NombreForm(
    nombre: String,
    onNombreChange: (String) -> Unit,
    enviando: Boolean,
    error: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    titulo: Boolean = true,
    submitLabel: String = stringResource(R.string.nombre_continue),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (titulo) {
            Text(
                text = stringResource(R.string.nombre_title),
                style = MaterialTheme.typography.headlineMedium,
                color = BukInk,
            )
            Spacer(Modifier.height(BukSpacing.sm))
            Text(
                text = stringResource(R.string.nombre_body),
                style = MaterialTheme.typography.bodyMedium,
                color = BukInkMuted,
            )
            Spacer(Modifier.height(BukSpacing.lg))
        }

        OutlinedTextField(
            value = nombre,
            onValueChange = onNombreChange,
            label = { Text(stringResource(R.string.nombre_label)) },
            singleLine = true,
            enabled = !enviando,
            isError = error,
            shape = BukShape.lg,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BukBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
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

        Spacer(Modifier.height(BukSpacing.md2))

        val enabled = nombre.isNotBlank() && !enviando
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BukMinTouchTarget)
                .height(ButtonHeight)
                .clip(BukShape.lg)
                .background(if (enabled) BukBlue else BukBlue.copy(alpha = BukOpacity.DISABLED))
                .bukPressable(onClick = onSubmit, enabled = enabled),
            contentAlignment = Alignment.Center,
        ) {
            // A real in-flight state. The old screen admitted in a comment that its entire
            // in-progress affordance was a disabled button, which tells a person that
            // something is wrong rather than that something is happening.
            Text(
                text = if (enviando) stringResource(R.string.nombre_guardando) else submitLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

private val ButtonHeight = 56.dp

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun NombreFormPreview() {
    BukInTheme {
        Column(
            Modifier
                .background(BukField)
                .padding(BukSpacing.md2),
        ) {
            NombreForm("Ana Restrepo", {}, enviando = false, error = false, onSubmit = {})
            Spacer(Modifier.height(BukSpacing.lg))
            NombreForm("Ana Restrepo", {}, enviando = true, error = false, onSubmit = {}, titulo = false)
        }
    }
}
