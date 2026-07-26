package com.buk.bukin.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.buk.bukin.designsystem.R
import com.buk.bukin.designsystem.theme.BukBlueGradient
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.designsystem.theme.BukInkMuted
import com.buk.bukin.designsystem.theme.BukInkPill
import com.buk.bukin.designsystem.theme.BukOnBlueFaint
import com.buk.bukin.designsystem.theme.BukOnBlueMuted
import com.buk.bukin.designsystem.theme.BukSpacing
import com.buk.bukin.designsystem.theme.BukSurface
import com.buk.bukin.designsystem.theme.TicketStubTime
import com.buk.bukin.domain.model.Instancia
import kotlin.math.cos
import kotlin.math.sin

/**
 * The signature component, persistent across all four collaborator states.
 *
 * Stateless by design — it takes an [Instancia] and renders it. The dotted stub separator
 * is what makes this read as a ticket rather than a generic card; it is not decoration.
 */
@Composable
fun TicketCard(
    instancia: Instancia,
    modifier: Modifier = Modifier,
    onHelpClick: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The card draws above the strip (zIndex) while the strip is pulled up underneath
        // it (offset), so only the strip's lower edge shows — a torn stub. Measuring the
        // card's height would be the alternative, and it breaks the moment the user's
        // font scale changes.
        TicketBody(instancia, Modifier.zIndex(1f))
        HelpStrip(
            onClick = onHelpClick,
            modifier = Modifier
                .padding(horizontal = BukSpacing.md)
                .offset(y = -HelpStripTuck),
        )
    }
}

/** How far the help strip hides behind the card. */
private val HelpStripTuck = 22.dp

@Composable
private fun TicketBody(instancia: Instancia, modifier: Modifier = Modifier) {
    val zone = rememberZone()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(BukBlueGradient))
            .padding(horizontal = BukSpacing.md, vertical = BukSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = instancia.cursoNombre,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(BukSpacing.sm))
            TicketPill()
        }

        Spacer(Modifier.height(BukSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = InstanciaFormat.fecha(instancia.fechaInicio, zone),
                    style = MaterialTheme.typography.labelMedium,
                    color = BukOnBlueMuted,
                )
                Spacer(Modifier.height(BukSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClockGlyph(tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(BukSpacing.sm))
                    Text(
                        text = InstanciaFormat.hora(instancia.fechaInicio, zone),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.ticket_duration_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = BukOnBlueMuted,
                )
                Spacer(Modifier.height(BukSpacing.xs))
                Text(
                    text = duracionLegible(instancia.duracionMinutos),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(BukSpacing.md))

        // The stub row: a label either side of a dotted tear line, times underneath.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ticket_checkin_label),
                style = MaterialTheme.typography.labelMedium,
                color = BukOnBlueMuted,
            )
            DottedTearLine(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = BukSpacing.sm),
            )
            Text(
                text = stringResource(R.string.ticket_exit_label),
                style = MaterialTheme.typography.labelMedium,
                color = BukOnBlueMuted,
            )
        }

        Spacer(Modifier.height(BukSpacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = InstanciaFormat.horaCheckIn(instancia.fechaInicio, zone),
                style = TicketStubTime,
                color = Color.White,
            )
            Text(
                text = InstanciaFormat.hora(instancia.fechaFin, zone),
                style = TicketStubTime,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TicketPill() {
    Text(
        text = stringResource(R.string.ticket_badge),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(BukInkPill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun DottedTearLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(2.dp)) {
        val radius = 1.dp.toPx()
        val gap = 6.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawCircle(color = BukOnBlueFaint, radius = radius, center = Offset(x, size.height / 2f))
            x += gap
        }
    }
}

/**
 * The torn strip tucked under the ticket. Only its lower edge is visible, so the top
 * corners stay square and the bottom ones are rounded.
 */
@Composable
private fun HelpStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(BukSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = BukSpacing.md)
            .padding(top = HelpStripTuck + BukSpacing.sm, bottom = BukSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ticket_help),
            style = MaterialTheme.typography.labelMedium,
            color = BukInkMuted,
        )
        Spacer(Modifier.width(BukSpacing.xs + 2.dp))
        SunGlyph(tint = BukInkMuted, modifier = Modifier.size(14.dp))
    }
}

/** Outlined clock. Drawn rather than imported, so no icon pack ships for two glyphs. */
@Composable
private fun ClockGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val stroke = size.minDimension * 0.09f
        val r = (size.minDimension - stroke) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = stroke))
        drawLine(
            color = tint,
            start = c,
            end = Offset(c.x, c.y - r * 0.52f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = c,
            end = Offset(c.x + r * 0.42f, c.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The little sun on the help strip. */
@Composable
private fun SunGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val core = size.minDimension * 0.24f
        val stroke = size.minDimension * 0.09f
        drawCircle(color = tint, radius = core, center = c)
        repeat(8) { i ->
            val a = (Math.PI / 4.0) * i
            val inner = core + stroke * 1.6f
            val outer = size.minDimension / 2f
            drawLine(
                color = tint,
                start = Offset(
                    c.x + (inner * cos(a)).toFloat(),
                    c.y + (inner * sin(a)).toFloat(),
                ),
                end = Offset(
                    c.x + (outer * cos(a)).toFloat(),
                    c.y + (outer * sin(a)).toFloat(),
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TicketCardPreview() {
    BukInTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(BukSpacing.gutter),
        ) {
            TicketCard(instancia = previewInstancia())
        }
    }
}
