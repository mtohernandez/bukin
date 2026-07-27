package com.buk.bukin.designsystem.component

import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlueGradient
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukOnBlueFaint
import com.buk.bukin.designsystem.theme.BukOnBlueMuted
import com.buk.bukin.designsystem.theme.BukOnBlueScrim
import com.buk.bukin.designsystem.theme.BukShape
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSurface
import com.buk.bukin.designsystem.theme.bukGutter
import com.buk.bukin.domain.model.Instancia

/**
 * The signature component, persistent across every collaborator state.
 *
 * The card this replaced read as a form. Duration was given the same 26sp weight as the
 * start time, so the eye could not tell which number mattered; the stub row restated the
 * end time, which the duration had already implied; and two hand-drawn glyphs competed at
 * unrelated stroke weights. Three things changed:
 *
 * - **Duration is demoted into the subtitle.** One line, under the course name, where a
 *   supporting fact belongs.
 * - **The stub carries the only genuinely new fact on the card: when the door opens.**
 *   Nothing is stated twice.
 * - **The pill carries state**, not the decorative word "Ticket". Same geometry, real
 *   information.
 *
 * Stateless by design — it takes an [Instancia] and renders it.
 */
@Composable
fun TicketCard(
    instancia: Instancia,
    modifier: Modifier = Modifier,
    onHelpClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TicketBody(instancia)
        if (onHelpClick != null) {
            Spacer(Modifier.height(BukSpacing.sm))
            HelpStrip(onClick = onHelpClick)
        }
    }
}

/** Where the notches bite, measured from the bottom edge. */
private val TearFromBottom = 56.dp

@Composable
private fun TicketBody(instancia: Instancia, modifier: Modifier = Modifier) {
    val zone = rememberZone()
    val shape = remember(Unit) { TicketShape(tearFromBottom = TearFromBottom) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(BukBlueGradient)),
    ) {
        Column(Modifier.padding(horizontal = BukSpacing.md2, vertical = BukSpacing.md2)) {
            Text(
                text = instancia.cursoNombre,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(BukSpacing.xs))
            Text(
                // "Lunes 13 de julio · 2 horas" — the date, and the duration that used to
                // shout from its own column.
                text = stringResource(
                    R.string.ticket_subtitulo,
                    InstanciaFormat.fecha(instancia.fechaInicio, zone),
                    duracionLegible(instancia.duracionMinutos),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = BukOnBlueMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(BukSpacing.lg))

            Row(Modifier.fillMaxWidth()) {
                TimeBlock(
                    time = InstanciaFormat.horaCorta(instancia.fechaInicio, zone),
                    label = stringResource(R.string.ticket_inicio),
                    modifier = Modifier.weight(1f),
                )
                TimeBlock(
                    time = InstanciaFormat.horaCorta(instancia.fechaFin, zone),
                    label = stringResource(R.string.ticket_fin),
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End,
                )
            }
        }

        // The tear. The notches in the silhouette are what say "ticket"; the dots are the
        // texture that follows the line between them, not the thing carrying the meaning.
        DottedTearLine(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BukSpacing.md2),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TearFromBottom)
                .padding(horizontal = BukSpacing.md2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.ticket_entrada_desde,
                    InstanciaFormat.horaCheckInCorta(instancia.fechaInicio, zone),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = BukOnBlueMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(BukSpacing.sm))
            EstadoPill(instancia)
        }
    }
}

@Composable
private fun TimeBlock(
    time: String,
    label: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = BukOnBlueMuted,
        )
    }
}

/**
 * State, not decoration.
 *
 * **This was specced as a glass surface and ships as a translucent solid.** `Modifier.blur`
 * in Compose blurs the element's *own* content, not what is behind it — there is no
 * backdrop-blur modifier — so applying it here blurred the label into an unreadable smear
 * on the device rather than frosting the gradient underneath. Verified on the Galaxy A54,
 * which is API 36 and well past the API 31 gate, so this is not the minSdk no-op.
 *
 * The spec's own rule settles what to do about it: every glass surface must be designed as
 * a translucent solid first, and if it only looks finished with the blur it is wrong. It
 * looks finished without.
 */
@Composable
private fun EstadoPill(instancia: Instancia) {
    val label = when {
        instancia.asistencia -> R.string.ticket_estado_marcada
        instancia.inscrito -> R.string.ticket_estado_inscrito
        else -> R.string.ticket_estado_sin_inscripcion
    }
    Box(
        Modifier
            .clip(BukShape.full)
            .background(BukOnBlueScrim),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = BukSpacing.sm2, vertical = BukSpacing.xs2),
        )
    }
}

@Composable
private fun DottedTearLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(BukSpacing.xxs).clearAndSetSemantics {}) {
        val radius = 1.dp.toPx()
        val gap = 6.dp.toPx()
        var x = radius
        while (x < size.width) {
            drawCircle(color = BukOnBlueFaint, radius = radius, center = Offset(x, size.height / 2f))
            x += gap
        }
    }
}

/**
 * "Necesito ayuda".
 *
 * A **real control** at a full 48dp touch height with an unmistakable affordance. It was a
 * no-op from session 1 until session 5: `TicketCard` declared `onHelpClick: () -> Unit = {}`
 * and the check-in screen never passed one, so tapping it did nothing on every screen for
 * three sessions. The parameter is nullable now — a caller that has no help to offer has to
 * say so and gets no strip, rather than silently getting a dead one.
 */
@Composable
private fun HelpStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ticket_help)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BukMinTouchTarget)
            .clip(BukShape.lg)
            .background(BukSurface)
            .bukPressable(onClick = onClick, onClickLabel = label)
            .padding(horizontal = BukSpacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BukIcons.ChevronUp,
            contentDescription = null,
            tint = BukInkMuted,
            modifier = Modifier.size(BukSpacing.md2),
        )
        Spacer(Modifier.width(BukSpacing.xs2))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = BukInkMuted,
        )
    }
}

/**
 * The ticket, before the row arrives.
 *
 * Check-in used to render no ticket at all while `instancia == null`, so the header landed
 * late and shoved the whole layout down under the user — the single most visible layout
 * shift in the app, on its most important screen. This occupies the same bounds as the real
 * card: same shape, same notch, same internal rhythm.
 */
@Composable
fun TicketCardSkeleton(modifier: Modifier = Modifier) {
    val shape = remember { TicketShape(tearFromBottom = TearFromBottom) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BukSurface),
    ) {
        Column(Modifier.padding(horizontal = BukSpacing.md2, vertical = BukSpacing.md2)) {
            BukSkeletonLine(0.62f, height = 22.dp)
            Spacer(Modifier.height(BukSpacing.sm))
            BukSkeletonLine(0.44f, height = 16.dp)
            Spacer(Modifier.height(BukSpacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { BukSkeletonLine(1f, height = 40.dp, modifier = Modifier.width(88.dp)) }
                Column { BukSkeletonLine(1f, height = 40.dp, modifier = Modifier.width(88.dp)) }
            }
            Spacer(Modifier.height(BukSpacing.md2))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TearFromBottom)
                .padding(horizontal = BukSpacing.md2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BukSkeletonLine(1f, height = 16.dp, modifier = Modifier.width(120.dp))
            BukSkeletonLine(1f, height = 24.dp, modifier = Modifier.width(84.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6)
@Composable
private fun TicketCardPreview() {
    BukInTheme {
        Box(
            Modifier
                .background(BukField)
                .padding(bukGutter),
        ) {
            TicketCard(instancia = previewInstancia(), onHelpClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE3E8F6, fontScale = 2.0f)
@Composable
private fun TicketCardLargeTypePreview() {
    BukInTheme {
        Box(
            Modifier
                .background(BukField)
                .padding(bukGutter),
        ) {
            TicketCard(instancia = previewInstancia(), onHelpClick = {})
        }
    }
}
