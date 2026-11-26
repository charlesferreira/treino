package dev.charlesferreira.treino.ui.evolution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.charlesferreira.treino.ui.workouts.EquipmentSheet
import dev.charlesferreira.treino.ui.workouts.equipmentChip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `b-exercise`: an exercise's top load, the chart of the last sessions (top load, volume or best
 * set), the best set, the most reps at the load before the top one, and every session.
 */
@Composable
fun ExerciseProgressScreen(exerciseId: String, onBack: () -> Unit) {
    val container = appContainer()
    val vm = viewModel(key = "progress-$exerciseId") { ExerciseProgressViewModel(container.repository, exerciseId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableStateOf(Metric.MAX) }
    var equipment by rememberSaveable { mutableStateOf(false) }
    val prescription by vm.prescription.collectAsStateWithLifecycle()
    val ui = state

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = TabBarDefaults.ContentClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TreinoColors.Card, CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Voltar" },
                contentAlignment = Alignment.Center,
            ) { Icon(TreinoIcons.Back, tint = TreinoColors.Text) }
            TText(
                ui?.exercise?.name.orEmpty(),
                style = TreinoType.TitleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            // The equipment tag opens the equipment sheet; "Equipamento" when there is none.
            if (ui != null) {
                val tag = ui.equipmentTag ?: equipmentChip(ui.exercise)
                val icon = when (ui.exercise.equipment) {
                    Equipment.DUMBBELL, Equipment.BARBELL -> TreinoIcons.Dumbbell
                    Equipment.MACHINE, Equipment.CABLE -> TreinoIcons.Machine
                    else -> null
                }
                Tag(
                    tag,
                    icon = icon,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { equipment = true }
                        .semantics { contentDescription = "Equipamento: $tag" },
                )
            }
        }
        if (ui == null) return@Column
        val chart = ui.chartSessions
        Card(
            cornerRadius = 20.dp,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    TText(ui.maxLabel, style = TreinoType.Label, maxLines = 1)
                    TText(
                        buildAnnotatedString {
                            append(ui.maxKg?.let(PtBr::number) ?: "–")
                            withStyle(SpanStyle(fontSize = 18.sp, color = TreinoColors.TextFaint)) { append(" kg") }
                        },
                        style = TreinoType.mono(44.sp, weight = FontWeight.W700).copy(lineHeight = 46.sp),
                        maxLines = 1,
                    )
                }
                ui.change?.let {
                    TText(
                        it,
                        style = TreinoType.mono(13.sp, color = if (ui.changeUp) TreinoColors.AccentLight else TreinoColors.TextFaint),
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric.entries.forEach { m ->
                    FilterChip(m.label, m == metric, onClick = { metric = m }, height = 32.dp, padding = 12.dp, background = TreinoColors.Surface)
                }
            }
            val values = chart.map { it.value(metric) }
            ProgressChart(
                values = values,
                dates = chart.map { it.date },
                description = "${metric.label} por sessão: " +
                    chart.joinToString("; ") { "${PtBr.number(it.value(metric))} kg em ${PtBr.shortDate(it.date)}" },
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.best?.let { HighlightTile("Melhor série", it) }
            ui.mostReps?.let { HighlightTile("Mais reps com ${PtBr.number(ui.mostRepsKg!!)}", it) }
        }
        if (ui.sessions.isNotEmpty()) {
            Card(cornerRadius = 20.dp) {
                ui.sessions.asReversed().forEachIndexed { i, s ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TText(PtBr.shortDate(s.date), style = TreinoType.mono(12.sp, color = TreinoColors.TextFaint), modifier = Modifier.width(52.dp), maxLines = 1)
                        TText(keepSetsWhole(sessionSetsText(s.sets)), style = TreinoType.mono(14.sp).copy(lineHeight = 20.sp), modifier = Modifier.weight(1f))
                        TText(
                            (if (s.up) "↑ " else "") + PtBr.number(s.maxKg),
                            style = TreinoType.mono(14.sp, color = if (s.up) TreinoColors.AccentLight else TreinoColors.TextDim),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    val ex = ui?.exercise
    if (equipment && ex != null) {
        EquipmentSheet(
            exercise = ex,
            prescription = prescription,
            onSave = {
                vm.saveExercise(it)
                equipment = false
            },
            onDismiss = { equipment = false },
        )
    }
}

@Composable
private fun RowScope.HighlightTile(label: String, value: DatedSets) {
    Card(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        TText(label, style = TreinoType.Label, maxLines = 1)
        TText(keepSetsWhole(value.text), style = TreinoType.mono(18.sp).copy(lineHeight = 25.sp), maxLines = 2)
        TText(PtBr.shortDate(value.date), style = TreinoType.Label.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.W400), color = TreinoColors.TextFaint, maxLines = 1)
    }
}

/** Lines break only after the commas: "180 × 10" never splits. */
private fun keepSetsWhole(text: String) = text.replace(" × ", "\u00A0×\u00A0")
