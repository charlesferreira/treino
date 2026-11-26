package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.Cell
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.Stepper
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/** What the workout screen does; the ViewModel implements it. */
interface WorkoutActions {
    fun onSwitch()
    fun onProfile()
    fun onSelect(item: ItemUi)
    fun onStepWeight(item: ItemUi, up: Boolean)
    fun onStepReps(item: ItemUi, up: Boolean)
    fun onTypeWeight(item: ItemUi, kg: Double)
    fun onTypeReps(item: ItemUi, reps: Int)
    fun onLogSet(item: ItemUi, draft: SetDraft)
    fun onEditSet(set: SetEntry)
    fun onEditTime(item: ItemUi, draft: (TimeDraft) -> TimeDraft)
    fun onLogTime(item: ItemUi, draft: TimeDraft)
    fun onEditLoggedTime(id: String)
    fun onFinish()
}

/**
 * `b-workout`: the workout in progress. The header ([WorkoutHeader]) collapses as the content
 * scrolls; below it the exercises done, the current one (sets table, steppers, "Registrar série N")
 * and the ones still to do, then "Terminar treino".
 */
@Composable
fun WorkoutScreen(
    ui: TodayUi,
    clock: String,
    setDraft: (ItemUi) -> SetDraft,
    timeDraft: (ItemUi) -> TimeDraft,
    actions: WorkoutActions,
    bottomExtra: Dp = 0.dp,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val p by remember { derivedStateOf { HeaderScroll.progress(with(density) { scroll.value.toDp().value }) } }
    val v = HeaderScroll.values(p)
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusOffset = status - HeaderScroll.DESIGN_STATUS_DP.dp
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = statusOffset + HeaderScroll.CONTENT_TOP_DP.dp,
                    bottom = maxOf(TabBarDefaults.bottomOffset() + TabBarDefaults.Height + 18.dp + bottomExtra, ime + 12.dp),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = ui.current
            val done = ui.items.filter { it.complete && it.item.id != current?.item?.id }
            val upcoming = ui.items.filter { !it.complete && it.item.id != current?.item?.id }
            if (done.isNotEmpty()) {
                CompactList(done, actions::onSelect) { item ->
                    DoneMark()
                    RowName(item)
                    TText(
                        loggedSummary(item.sets, item.times).orEmpty(),
                        style = TreinoType.mono(13.sp, color = TreinoColors.TextDim),
                        maxLines = 1,
                    )
                }
            }
            if (current != null) {
                if (current.isTime) {
                    TimeExerciseCard(current, timeDraft(current), actions)
                } else {
                    CurrentExerciseCard(current, setDraft(current), ui.session?.id, actions)
                }
            }
            if (upcoming.isNotEmpty()) {
                CompactList(upcoming, actions::onSelect) { item ->
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        TText("${item.number}", style = TreinoType.mono(13.sp, color = TreinoColors.TextFaint), maxLines = 1)
                    }
                    RowName(item)
                    TText(
                        if (item.done > 0) "${item.done}/${item.total}" else item.target,
                        style = TreinoType.mono(12.sp, color = TreinoColors.TextFaint),
                        maxLines = 1,
                    )
                }
            }
            when {
                ui.ended -> GhostButton("Ver resumo", onClick = actions::onFinish)
                current == null -> PrimaryButton("Terminar treino", onClick = actions::onFinish, icon = TreinoIcons.Check)
                else -> GhostButton("Terminar treino", onClick = actions::onFinish)
            }
        }
        WorkoutHeader(
            v = v,
            statusBar = status,
            statusOffset = statusOffset,
            dateLabel = ui.dateLabel,
            workoutName = ui.workout?.name.orEmpty(),
            clock = clock,
            progress = ui.progress,
            profileName = ui.profileName,
            onSwitch = actions::onSwitch,
            onProfile = actions::onProfile,
        )
    }
}

/** The card of exercises done (and of the ones still to do): 40 dp rows, padding 6/16. */
@Composable
private fun CompactList(items: List<ItemUi>, onSelect: (ItemUi) -> Unit, row: @Composable RowScope.(ItemUi) -> Unit) {
    Card(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clickable(role = Role.Button) { onSelect(item) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) { row(item) }
        }
    }
}

@Composable
private fun RowScope.RowName(item: ItemUi) {
    TText(item.exercise.name, style = TreinoType.Row, modifier = Modifier.weight(1f), maxLines = 1)
}

/** Name, target and tags of the current exercise. */
@Composable
fun ExerciseTitle(item: ItemUi, tags: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TText(item.exercise.name, style = TreinoType.Heading, modifier = Modifier.weight(1f))
            TText(item.target, style = TreinoType.mono(13.sp, color = TreinoColors.TextFaint), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = tags)
    }
}

/** The current exercise: tags, sets table, steppers and "Registrar série N". */
@Composable
private fun CurrentExerciseCard(item: ItemUi, draft: SetDraft, sessionId: String?, actions: WorkoutActions) {
    val exercise = item.exercise
    val next = item.nextPosition()
    val rows = maxOf(item.total, item.sets.size + 1)
    val step = stepLabel(exercise)
    var focused by remember(item.item.id, next) { mutableStateOf(CellFocus.NONE) }
    Card(
        borderColor = TreinoColors.AccentBorder,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExerciseTitle(item) {
            if (exercise.countsTwoDumbbells) Tag("Halteres 2×", icon = TreinoIcons.Dumbbell)
            if (item.item.restSec > 0) Tag(restLabel(item.item.restSec))
            item.maxKg?.let { Tag("máx ${PtBr.kg(it)}") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TableRow {
                val style = TreinoType.Label.copy(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.06.em)
                TText("SÉRIE", style = style, maxLines = 1, modifier = Modifier.width(36.dp))
                TText(item.lastDate?.let { PtBr.shortDate(it).uppercase() }.orEmpty(), style = style, maxLines = 1, modifier = Modifier.weight(1f))
                TText(unitLabel(exercise).uppercase(), style = style, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
                TText("REPS", style = style, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
                Box(Modifier.width(36.dp))
            }
            val lastByPosition = item.lastSets.associateBy { it.position }
            for (position in 0 until rows) {
                val logged = item.sets.getOrNull(position)
                val last = lastByPosition[position]
                when {
                    logged != null -> TableRow(Modifier.clickable(role = Role.Button) { actions.onEditSet(logged) }) {
                        SetNumber(position, accent = false)
                        LastText(last)
                        Cell(PtBr.number(logged.weightKg), Modifier.width(72.dp))
                        Cell("${logged.reps}", Modifier.width(60.dp))
                        Box(Modifier.size(36.dp, 28.dp), contentAlignment = Alignment.CenterStart) {
                            DoneMark(Modifier.padding(start = 3.dp))
                        }
                    }
                    position == next -> TableRow(
                        Modifier
                            .bleed(8.dp)
                            .background(TreinoColors.AccentFaint, RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        SetNumber(position, accent = true)
                        LastText(last)
                        NumberCell(
                            value = PtBr.number(draft.weightKg),
                            width = 72.dp,
                            highlighted = focused != CellFocus.REPS,
                            decimal = true,
                            onFocus = { if (it) focused = CellFocus.WEIGHT else if (focused == CellFocus.WEIGHT) focused = CellFocus.NONE },
                            onValue = { text -> parseDecimal(text)?.let { actions.onTypeWeight(item, it) } },
                        )
                        NumberCell(
                            value = "${draft.reps}",
                            width = 60.dp,
                            highlighted = focused == CellFocus.REPS,
                            decimal = false,
                            onFocus = { if (it) focused = CellFocus.REPS else if (focused == CellFocus.REPS) focused = CellFocus.NONE },
                            onValue = { text -> text.trim().toIntOrNull()?.takeIf { it >= 0 }?.let { actions.onTypeReps(item, it) } },
                        )
                        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                            Box(Modifier.size(28.dp).border(2.dp, TreinoColors.Accent, CircleShape))
                        }
                    }
                    else -> {
                        val prefill = item.prefill(position, sessionId)
                        TableRow(Modifier.alpha(0.55f)) {
                            SetNumber(position, accent = false)
                            LastText(last)
                            Cell(prefill?.let { PtBr.number(it.weightKg) } ?: "–", Modifier.width(72.dp))
                            Cell(prefill?.let { "${it.reps}" } ?: "–", Modifier.width(60.dp))
                            Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.size(28.dp).border(2.dp, TreinoColors.SurfaceHigh, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                value = PtBr.kg(draft.weightKg),
                onMinus = { actions.onStepWeight(item, false) },
                onPlus = { actions.onStepWeight(item, true) },
                modifier = Modifier.weight(1f),
                minusLabel = "−$step",
                plusLabel = "+$step",
                minusDescription = "Menos ${stepDescription(exercise)}",
                plusDescription = "Mais ${stepDescription(exercise)}",
            )
            Stepper(
                value = "${draft.reps}",
                onMinus = { actions.onStepReps(item, false) },
                onPlus = { actions.onStepReps(item, true) },
                modifier = Modifier.weight(1f),
                minusDescription = "Menos 1 rep",
                plusDescription = "Mais 1 rep",
            )
        }
        PrimaryButton("Registrar série ${next + 1}", onClick = { actions.onLogSet(item, draft) }, icon = TreinoIcons.Check)
    }
}

private enum class CellFocus { NONE, WEIGHT, REPS }

/** `.tbl`: 36 | 1fr | 72 | 60 | 36, 8 dp between columns. */
@Composable
private fun TableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SetNumber(position: Int, accent: Boolean) {
    TText(
        "${position + 1}",
        style = TreinoType.mono(16.sp, color = if (accent) TreinoColors.Accent else TreinoColors.Text),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(36.dp),
    )
}

@Composable
private fun RowScope.LastText(last: SetEntry?) {
    TText(
        last?.let(::setText).orEmpty(),
        style = TreinoType.mono(14.sp, color = TreinoColors.TextFaint),
        maxLines = 1,
        modifier = Modifier.weight(1f),
    )
}

/**
 * The current row's load or reps: a `.cell` that takes the keyboard when tapped. The accent ring
 * marks the field being edited (the load until the reps get the focus).
 */
@Composable
private fun NumberCell(
    value: String,
    width: Dp,
    highlighted: Boolean,
    decimal: Boolean,
    onFocus: (Boolean) -> Unit,
    onValue: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(it)
        },
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .onFocusChanged { onFocus(it.isFocused) },
        textStyle = TreinoType.mono(17.sp).copy(textAlign = TextAlign.Center),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        cursorBrush = SolidColor(TreinoColors.Accent),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(TreinoColors.Surface, shape)
                    .then(if (highlighted) Modifier.border(2.dp, TreinoColors.Accent, shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

/** CSS `margin: 0 -[amount]`: the row reaches [amount] past both sides of its parent. */
fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
