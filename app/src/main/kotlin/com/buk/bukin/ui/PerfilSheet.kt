package com.buk.bukin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.component.Avatar
import com.buk.bukin.designsystem.component.BukMinTouchTarget
import com.buk.bukin.designsystem.component.bukPressable
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSurface

/**
 * Everything about you, behind the avatar.
 *
 * This is the "internal panel or profile menu" the feedback asked for, and the entry point
 * to the thing it actually wanted: a way to check that a past punch-in registered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilSheet(
    nombre: String,
    avatarPath: String?,
    onDismiss: () -> Unit,
    onChangeName: () -> Unit,
    onOpenAsistencia: () -> Unit,
    onAvatarPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val pickAvatar = rememberAvatarPicker(onPicked = onAvatarPicked)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = BukSurface,
        shape = com.buk.bukin.designsystem.theme.BukShape.xxl,
    ) {
        Column(Modifier.padding(horizontal = BukSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(nombre = nombre, photoPath = avatarPath, onClick = pickAvatar)
                Spacer(Modifier.width(BukSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = nombre,
                        style = MaterialTheme.typography.titleMedium,
                        color = BukInk,
                    )
                    Text(
                        text = stringResource(R.string.perfil_foto),
                        style = MaterialTheme.typography.bodySmall,
                        color = BukInkMuted,
                    )
                }
            }

            Spacer(Modifier.height(BukSpacing.lg))
            PerfilItem(stringResource(R.string.perfil_mi_asistencia), onOpenAsistencia)
            PerfilItem(stringResource(R.string.perfil_cambiar_nombre), onChangeName)
            Spacer(Modifier.height(BukSpacing.xl))
        }
    }
}

@Composable
private fun PerfilItem(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BukMinTouchTarget)
            .bukPressable(onClick = onClick, ripple = true, onClickLabel = label),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = BukInk,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PerfilItemPreview() {
    BukInTheme {
        Column(Modifier.padding(BukSpacing.lg)) {
            PerfilItem("Mi asistencia") {}
            PerfilItem("Cambiar mi nombre") {}
        }
    }
}
