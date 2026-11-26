package dev.charlesferreira.treino.ui.evolution

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoFonts
import dev.charlesferreira.treino.ui.theme.TreinoType

/** The 1 dp line between rows of a card. */
@Composable
fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))

/**
 * The Dias / Exercícios selector: #1A1C20 pill, radius 22, 4 dp padding and gap; the 36 dp
 * options, radius 18, 14 sp semibold; the chosen one orange with ink text.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(TreinoColors.Card, RoundedCornerShape(22.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) TreinoColors.Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                TText(
                    label,
                    style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
                    color = if (on) TreinoColors.Ink else TreinoColors.TextDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * `.fil`: 13 sp semibold pill; on = light with dark text. [height], [padding] and [background]
 * differ between `b-progress` (34, 14, card) and `b-exercise` (32, 12, surface).
 */
@Composable
fun FilterChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    height: Dp = 34.dp,
    padding: Dp = 14.dp,
    background: Color = TreinoColors.Card,
) {
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (on) TreinoColors.Text else background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            label,
            style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
            color = if (on) TreinoColors.Background else TreinoColors.TextDim,
            maxLines = 1,
        )
    }
}

/** `.fact`: 26 dp, radius 8, #24272C; dim 12 sp text with the value in mono, light. */
@Composable
fun FactChip(fact: Fact) {
    Box(
        Modifier
            .height(26.dp)
            .background(TreinoColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            buildAnnotatedString {
                append(fact.before)
                fact.value?.let { v ->
                    withStyle(SpanStyle(fontFamily = TreinoFonts.JetBrainsMono, color = TreinoColors.Text, fontFeatureSettings = "tnum", letterSpacing = (-0.02).em)) {
                        append(v)
                    }
                }
                append(fact.after)
            },
            style = TreinoType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W400, lineHeight = 16.sp),
            maxLines = 1,
        )
    }
}

/** The 64 × 26 polyline of `b-progress`, stroke 2. */
@Composable
fun SparklineView(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    val points = remember(values) { Sparkline.points(values) }
    Canvas(modifier.size(Sparkline.WIDTH.dp, Sparkline.HEIGHT.dp)) {
        if (points.size < 2) return@Canvas
        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x.dp.toPx(), p.y.dp.toPx()) else lineTo(p.x.dp.toPx(), p.y.dp.toPx()) }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * The `b-exercise` chart, drawn on a Canvas 150 dp high: gridlines at 6% white, values in mono
 * 10 sp faint over three of them, a 10% orange area under the line, the line (3 dp, round joins),
 * hollow points (r 4, stroke 2.5) and the last one filled (r 6), dates under every other point.
 */
@Composable
fun ProgressChart(values: List<Double>, dates: List<String>, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TreinoType.mono(10.sp, color = TreinoColors.TextFaint).copy(lineHeight = 12.sp, letterSpacing = 0.sp)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartScale.HEIGHT.dp)
            .semantics { contentDescription = description },
    ) {
        val g = ChartScale.of(values, dates, size.width.toDp().value)
        fun x(v: Float) = v.dp.toPx()
        fun y(v: Float) = v.dp.toPx()
        listOf(ChartScale.TOP, ChartScale.MID, ChartScale.LOW, ChartScale.BASE).forEach { line ->
            drawLine(TreinoColors.Border, Offset(0f, y(line)), Offset(size.width, y(line)), 1.dp.toPx())
        }
        g.yLabels.forEach { label ->
            val m = measurer.measure(label.text, style)
            // SVG text sits on its baseline 5 above the line.
            drawText(m, topLeft = Offset(0f, y(label.at - 5f) - m.firstBaseline))
        }
        if (g.points.isEmpty()) return@Canvas
        val line = Path().apply {
            g.points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.x), y(p.y)) else lineTo(x(p.x), y(p.y)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(g.points.last().x), y(ChartScale.BASE))
            lineTo(x(g.points.first().x), y(ChartScale.BASE))
            close()
        }
        drawPath(area, TreinoColors.Accent.copy(alpha = 0.10f), style = Fill)
        if (g.points.size > 1) {
            drawPath(line, TreinoColors.Accent, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
        }
        g.points.dropLast(1).forEach { p ->
            val c = Offset(x(p.x), y(p.y))
            drawCircle(TreinoColors.Card, 4.dp.toPx(), c)
            drawCircle(TreinoColors.Accent, 4.dp.toPx(), c, style = Stroke(width = 2.5.dp.toPx()))
        }
        g.points.last().let { drawCircle(TreinoColors.Accent, 6.dp.toPx(), Offset(x(it.x), y(it.y))) }
        g.xLabels.forEach { label ->
            val m = measurer.measure(label.text, style)
            val left = (x(label.at) - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
            drawText(m, topLeft = Offset(left, y(ChartScale.DATE_Y) - m.firstBaseline))
        }
    }
}
