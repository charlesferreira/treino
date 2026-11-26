package dev.charlesferreira.treino.ui.evolution

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Avatar
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.SectionLabel
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoFonts
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * The Evolução tab: the title, the Dias / Exercícios selector, and under it `b-history` (the
 * month card and the days) or `b-progress` (the 30-day tiles, the body filter and the exercises).
 */
@Composable
fun EvolutionScreen(onProfile: () -> Unit, onDay: (String) -> Unit, onExercise: (String) -> Unit) {
    val container = appContainer()
    val vm = viewModel { EvolutionViewModel(container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(BodyFilter.ALL) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = TabBarDefaults.ContentClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TText("Evolução", style = TreinoType.Title, modifier = Modifier.weight(1f), maxLines = 1)
            Avatar(state?.profileName.orEmpty(), onClick = onProfile)
        }
        Segmented(listOf("Dias", "Exercícios"), tab, { tab = it }, Modifier.padding(horizontal = 6.dp))
        val ui = state ?: return@Column
        if (tab == 0) {
            MonthCard(ui.month)
            if (ui.days.isEmpty()) {
                Empty("Os treinos registrados aparecem aqui.")
            } else {
                // One card per month; the current one is already named by the month card.
                val months = ui.days.groupBy { it.monthKey }.values.toList()
                months.forEachIndexed { m, days ->
                    if (m > 0 || days.first().monthKey != ui.month.key) {
                        SectionLabel(days.first().monthLabel, Modifier.padding(top = 6.dp))
                    }
                    Card(cornerRadius = 20.dp) {
                        days.forEachIndexed { i, day ->
                            if (i > 0) RowDivider()
                            DayRow(day, onClick = { onDay(day.date) })
                        }
                    }
                }
            }
        } else {
            val ex = ui.exercises
            OverviewCard(ex.overview)
            val current = if (filter in ex.filters) filter else BodyFilter.ALL
            if (ex.filters.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ex.filters.forEach { f -> FilterChip(f.label, f == current, onClick = { filter = f }) }
                }
            }
            val rows = ex.rows.filter { current.matches(it.group) }
            if (rows.isEmpty()) {
                Empty("Os exercícios com carga aparecem aqui.")
            } else {
                Card(cornerRadius = 20.dp) {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) RowDivider()
                        ExerciseRow(row, onClick = { onExercise(row.exerciseId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    TText(text, style = TreinoType.Body, color = TreinoColors.TextDim, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
}

private val Label11 = TreinoType.Label.copy(fontSize = 11.sp, lineHeight = 15.4.sp)

/** The month card: name, "14 treinos", the weekday letters and three weeks of cells. */
@Composable
private fun MonthCard(month: MonthUi) {
    Card(cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            TText(month.title, style = TreinoType.Body.copy(fontWeight = FontWeight.W700), modifier = Modifier.weight(1f), maxLines = 1)
            TText(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = TreinoFonts.JetBrainsMono, color = TreinoColors.Text, fontFeatureSettings = "tnum")) {
                        append("${month.count}")
                    }
                    append(if (month.count == 1) " treino" else " treinos")
                },
                style = TreinoType.Label,
                maxLines = 1,
            )
        }
        Week {
            listOf("S", "T", "Q", "Q", "S", "S", "D").forEach {
                TText(it, style = Label11, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        month.weeks.forEach { week ->
            Week {
                week.forEach { cell -> CalendarDay(cell, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Week(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** `.wk i`: 26 dp, radius 7. `.g` orange, `.c` orange ring, `.t` light ring (today). */
@Composable
private fun CalendarDay(cell: CalendarCell, modifier: Modifier) {
    val shape = RoundedCornerShape(7.dp)
    val fill = when (cell.mark) {
        DayMark.LOAD -> TreinoColors.Accent
        DayMark.TIME -> Color.Transparent
        DayMark.NONE -> TreinoColors.Surface
    }
    val ring = when {
        cell.today -> TreinoColors.Text
        cell.mark == DayMark.TIME -> TreinoColors.Accent
        else -> null
    }
    Box(
        modifier
            .height(26.dp)
            .background(fill, shape)
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier),
    )
}

/** `.day`: the date column, the workout, the facts, and on the latest day its top loads. */
@Composable
private fun DayRow(day: DayRowUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TText(day.day, style = TreinoType.mono(22.sp, weight = FontWeight.W700).copy(lineHeight = 24.sp), maxLines = 1)
            TText(day.weekday, style = TreinoType.Label.copy(fontWeight = FontWeight.W400), color = TreinoColors.TextFaint, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TText(day.title, style = TreinoType.Body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.W700), maxLines = 1)
            if (day.facts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.facts.forEach { FactChip(it) }
                }
            }
            if (day.lines.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TText(
                                line.name,
                                style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                color = TreinoColors.TextDim,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            TText(
                                (if (line.up) "↑ " else "") + "máx ${PtBr.kg(line.maxKg)}",
                                style = TreinoType.mono(13.sp, color = if (line.up) TreinoColors.AccentLight else TreinoColors.Text).copy(lineHeight = 18.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Subiram, Treinos and Volume over 30 days. */
@Composable
private fun OverviewCard(o: OverviewUi) {
    Card(cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Subiram", "${o.risen}", "em 30 dias", TreinoColors.Accent)
            Tile("Treinos", "${o.workouts}", "em 30 dias")
            Tile("Volume", o.volumeChangePercent?.let(::percentText) ?: "–", "vs 30 dias antes")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(label: String, value: String, sub: String, color: Color = TreinoColors.Text) {
    Column(Modifier.weight(1f)) {
        TText(label, style = TreinoType.Label, maxLines = 1)
        TText(value, style = TreinoType.mono(26.sp, weight = FontWeight.W700, color = color).copy(lineHeight = 36.4.sp), maxLines = 1)
        TText(sub, style = Label11.copy(fontWeight = FontWeight.W400), color = TreinoColors.TextFaint, maxLines = 1)
    }
}

/** `.er`: name and fact, the sparkline, the latest top load ("por halter" under it). */
@Composable
private fun ExerciseRow(row: ExerciseRowUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TText(row.name, style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600), maxLines = 2)
            TText(
                row.fact,
                style = TreinoType.mono(12.sp, color = if (row.up) TreinoColors.AccentLight else TreinoColors.TextFaint).copy(lineHeight = 17.sp),
                maxLines = 1,
            )
        }
        SparklineView(row.spark, if (row.up) TreinoColors.Accent else TreinoColors.TextFaint)
        Column(Modifier.width(66.dp), horizontalAlignment = Alignment.End) {
            TText(PtBr.kg(row.currentKg), style = TreinoType.mono(17.sp).copy(lineHeight = 24.sp), maxLines = 1)
            if (row.perDumbbell) {
                TText("por halter", style = Label11.copy(fontWeight = FontWeight.W400), color = TreinoColors.TextFaint, maxLines = 1)
            }
        }
    }
}
