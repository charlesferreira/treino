package dev.charlesferreira.treino.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/*
 * `dumbbell-review` is drawn in direction A (lime, Barlow). Here it keeps its layout and texts in
 * the colors and fonts of B, like the equipment sheet: lime → the accent, Barlow → JetBrains Mono.
 * The amber of what was written stays (#FFB454), the one color B has no role for.
 */
private val Amber = Color(0xFFFFB454)
private val AmberRow = Color(0x0DFFB454)
private val Line = Color(0x14FFFFFF)
private val Grid = Color(0x12FFFFFF)

/**
 * Review of old dumbbell loads, one exercise at a time. Each flagged session asks "Por halter" or
 * "Era o par"; nothing is written until "Aplicar". [onBack] also runs when nothing is left.
 */
@Composable
fun LoadReviewScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm = viewModel { LoadReviewViewModel(container.repository) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val state = ui ?: return
    val ex = state.current

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = if (ex != null) 160.dp else 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = "Voltar" },
                    contentAlignment = Alignment.Center,
                ) { Icon(TreinoIcons.Back, tint = TreinoColors.Text, size = 20.dp) }
                TText(
                    "Revisar cargas de halteres",
                    style = TreinoType.Body.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.W700),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                state.position?.let {
                    TText(it, style = small, color = TreinoColors.TextDim, modifier = Modifier.padding(end = 8.dp))
                }
            }
            if (ex == null) {
                TText(
                    "Nenhuma carga para conferir.",
                    style = TreinoType.Body,
                    color = TreinoColors.TextDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TText(ex.name, style = TreinoType.Title.copy(lineHeight = 33.sp))
                    TText(ex.subtitle, style = small, color = TreinoColors.TextDim)
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                    ReviewChart(ex, state.choices)
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Amber, "Anotado")
                        Legend(TreinoColors.Accent, "Com as correções")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                ex.rows.forEach { row -> ReviewRowView(row, state.choices[row.sessionId], vm::choose) }
            }
        }
        if (ex != null) {
            val label = applyLabel(ex.flaggedSessionIds.mapNotNull { state.choices[it] })
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(TreinoColors.Background)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(label ?: "Aplicar correções", onClick = vm::apply, enabled = label != null, height = 56.dp)
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button, onClick = vm::keepAll),
                    contentAlignment = Alignment.Center,
                ) {
                    TText("Manter tudo como está", style = TreinoType.ButtonGhost, color = TreinoColors.TextSoft)
                }
            }
        }
    }
    // Everything decided: back to Perfil.
    LaunchedEffect(ex == null) {
        if (ex == null && vm.decidedAny) onBack()
    }
}

private val small = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).background(color))
        TText(text, style = TreinoType.Body.copy(fontSize = 12.sp, lineHeight = 17.sp), color = TreinoColors.TextDim)
    }
}

@Composable
private fun ReviewRowView(row: ReviewRow, choice: LoadChoice?, onChoose: (String, LoadChoice) -> Unit) {
    val flag = row.flag
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (flag != null) AmberRow else Color.Transparent)
            .padding(start = 20.dp, end = 20.dp, top = if (flag != null) 12.dp else 10.dp, bottom = if (flag != null) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TText(row.date, style = small, color = TreinoColors.TextFaint, modifier = Modifier.width(52.dp), maxLines = 1)
            TText(row.sets, style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TreinoColors.TextDim, modifier = Modifier.weight(1f))
            TText(row.load, style = TreinoType.mono(20.sp), color = if (flag != null) Amber else TreinoColors.Text, maxLines = 1)
        }
        if (flag != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(flag.keepLabel, choice == LoadChoice.KEEP, Modifier.weight(1f)) { onChoose(row.sessionId, LoadChoice.KEEP) }
                Choice(flag.halveLabel, choice == LoadChoice.HALVE, Modifier.weight(1f)) { onChoose(row.sessionId, LoadChoice.HALVE) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

/** `.ch`: 44 dp, radius 12; on = accent at 12% with the accent border and text. */
@Composable
private fun Choice(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (on) Color(0x1FFF7A45) else TreinoColors.Card, shape)
            .border(1.dp, if (on) TreinoColors.Accent else Color(0x1AFFFFFF), shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            text,
            style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600),
            color = if (on) TreinoColors.Accent else TreinoColors.TextSoft,
            maxLines = 1,
        )
    }
}

/**
 * The 350 × 130 chart: dashed amber = as written, with amber dots on the flagged sessions; solid
 * accent = with the corrections (a session marked "Por halter" keeps its load). Labels: the
 * highest load written and the corrected one, on their grid lines.
 */
@Composable
private fun ReviewChart(ex: ReviewExercise, choices: Map<String, LoadChoice>) {
    val written = ex.points.map { it.kg }
    val corrected = correctedLine(ex.points, choices)
    val min = (written + corrected).min()
    val max = (written + corrected).max()
    val labelKg = ex.points.filter { it.flagged }.maxOf { it.kg }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = TreinoType.Body.fontFamily, fontSize = 11.sp, color = TreinoColors.TextFaint)
    val description = "Cargas anotadas: ${written.joinToString(", ") { PtBr.number(it) }} kg. " +
        "Com as correções: ${corrected.joinToString(", ") { PtBr.number(it) }} kg"
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = description }) {
        val sx = size.width / 350f
        val sy = size.height / 130f
        fun x(i: Int) = (30f + if (written.size > 1) i * 310f / (written.size - 1) else 155f) * sx
        fun y(v: Double) = reviewChartY(v, min, max) * sy

        val gridYs = listOf(y(labelKg), y(labelKg / 2), 110f * sy)
        gridYs.forEach { gy -> drawLine(Grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()) }
        listOf(labelKg, labelKg / 2).forEach { v ->
            val text = measurer.measure(PtBr.number(v), labelStyle)
            drawText(text, topLeft = Offset(0f, y(v) - 4.dp.toPx() - text.size.height))
        }

        fun path(values: List<Double>) = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        drawPath(
            path(written), Amber,
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
        )
        drawPath(path(corrected), TreinoColors.Accent, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
        ex.points.forEachIndexed { i, p ->
            if (p.flagged && corrected[i] != p.kg) drawCircle(Amber, 5.dp.toPx(), Offset(x(i), y(p.kg)))
        }
        corrected.forEachIndexed { i, v ->
            val c = Offset(x(i), y(v))
            drawCircle(TreinoColors.Background, 4.dp.toPx(), c)
            drawCircle(TreinoColors.Accent, 4.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
        }
    }
}
