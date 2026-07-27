package com.buk.bukin.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlue
import com.buk.bukin.designsystem.theme.BukBlueGradient
import com.buk.bukin.designsystem.theme.BukBorder
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInk
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukOnBlueMuted
import com.buk.bukin.designsystem.theme.BukOnBlueScrim
import com.buk.bukin.designsystem.theme.BukOpacity
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukStroke
import com.buk.bukin.designsystem.theme.BukSuccessInk
import com.buk.bukin.designsystem.theme.BukSurface
import com.buk.bukin.domain.model.Instancia

/**
 * A session in the list, as a ticket.
 *
 * It shares [TicketShape] with `TicketCard` — same notch, same tear, same radius — so
 * tapping a row into the check-in screen is a **continuation** rather than a screen swap.
 * The object the person tapped is the object they arrive at.
 *
 * **State drives the surface, not just a label colour.** What this replaces was a plain
 * Material `Card` whose entire affordance was a blue word, with every non-actionable state
 * rendered as the same card with a grey word instead — so "happening right now", "you
 * already marked this" and "this finished last Tuesday" were the same object three times.
 */
@Composable
fun InstanceCard(
    instancia: Instancia,
    state: InstanceCardState,
    actionLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val zone = rememberZone()
    val shape = remember { TicketShape(tearFromBottom = TearFromBottom, notchRadius = NotchRadius, corner = Corner) }
    val active = state == InstanceCardState.ActiveNow

    val edge = when (state) {
        InstanceCardState.ActiveNow -> null
        InstanceCardState.Enrolled -> BukBlue.copy(alpha = 0.35f)
        InstanceCardState.Marked -> BukSuccessInk.copy(alpha = 0.55f)
        InstanceCardState.NotEnrolled -> BukBorder
        InstanceCardState.Finished -> BukBorder
    }
    val onSurface = if (active) Color.White else BukInk
    val onSurfaceMuted = if (active) BukOnBlueMuted else BukInkMuted

    Column(
        modifier = modifier
            .fillMaxWidth()
            // A finished session is present and legible and plainly not tappable. It does
            // not press, because a surface that animates and then does nothing is a lie.
            .alpha(if (state == InstanceCardState.Finished) BukOpacity.DISABLED else 1f)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(Brush.linearGradient(BukBlueGradient))
                } else {
                    Modifier.background(BukSurface)
                },
            )
            .then(if (edge != null) Modifier.border(BorderStroke(BukStroke.emphasis, edge), shape) else Modifier)
            .then(
                if (onClick != null) {
                    // Ripple: a list row *is* a menu item, which is the one place it belongs.
                    Modifier.bukPressable(onClick = onClick, ripple = true, onClickLabel = actionLabel)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.padding(horizontal = BukSpacing.md2, vertical = BukSpacing.md)) {
            Text(
                text = instancia.cursoNombre,
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(BukSpacing.xs))
            Text(
                text = stringResource(
                    R.string.ticket_subtitulo,
                    InstanciaFormat.fecha(instancia.fechaInicio, zone),
                    duracionLegible(instancia.duracionMinutos),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        InstanceTear(active)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TearFromBottom)
                .padding(horizontal = BukSpacing.md2, vertical = BukSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                // Short form, as on the ticket: at 320dp the meridiem cost enough width
                // that the action chip beside it ellipsised.
                text = InstanciaFormat.horaCorta(instancia.fechaInicio, zone),
                style = MaterialTheme.typography.displayMedium,
                color = onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(BukSpacing.sm))
            ActionChip(label = actionLabel, state = state, active = active)
        }
    }
}

/** Which of the five surfaces this row is. */
enum class InstanceCardState {
    /** Happening now. The only card in the list with the brand gradient. */
    ActiveNow,

    /** Signed in, waiting for the hour. */
    Enrolled,

    /** Not signed in yet. */
    NotEnrolled,

    /** Attendance already recorded. */
    Marked,

    /** Over. No action, and it does not pretend to be tappable. */
    Finished,
}

@Composable
private fun ActionChip(label: String, state: InstanceCardState, active: Boolean) {
    val container = when {
        active -> BukOnBlueScrim
        state == InstanceCardState.Marked -> BukSuccessInk.copy(alpha = 0.12f)
        state == InstanceCardState.NotEnrolled -> BukBlue.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val content = when {
        active -> Color.White
        state == InstanceCardState.Marked -> BukSuccessInk
        state == InstanceCardState.NotEnrolled -> BukBlue
        else -> BukInkMuted
    }

    Box(
        Modifier
            .clip(BukShape.full)
            .background(container)
            .padding(horizontal = BukSpacing.sm2, vertical = BukSpacing.xs2),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InstanceTear(active: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BukSpacing.md2)
            .height(BukStroke.hairline)
            .background(if (active) BukOnBlueScrim else BukBorder),
    )
}

private val TearFromBottom = 52.dp
private val NotchRadius = 8.dp
private val Corner = 20.dp

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun InstanceCardPreview() {
    BukInTheme {
        Column(
            Modifier
                .background(BukField)
                .padding(BukSpacing.md2),
            verticalArrangement = Arrangement.spacedBy(BukSpacing.sm2),
        ) {
            InstanceCard(
                instancia = previewInstancia(),
                state = InstanceCardState.ActiveNow,
                actionLabel = stringResource(R.string.sesiones_marcar),
                onClick = {},
            )
            InstanceCard(
                instancia = previewInstancia(),
                state = InstanceCardState.Enrolled,
                actionLabel = stringResource(R.string.sesiones_inscrito),
            )
            InstanceCard(
                instancia = previewInstancia(),
                state = InstanceCardState.Marked,
                actionLabel = stringResource(R.string.asistencia_registrada),
            )
            InstanceCard(
                instancia = previewInstancia(),
                state = InstanceCardState.Finished,
                actionLabel = stringResource(R.string.sesiones_cerrada),
            )
        }
    }
}
