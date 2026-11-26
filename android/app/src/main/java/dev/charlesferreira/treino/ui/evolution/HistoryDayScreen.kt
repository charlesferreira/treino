package dev.charlesferreira.treino.ui.evolution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoFonts
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.today.EditSetSheet
import dev.charlesferreira.treino.ui.today.EditTimeSheet
import dev.charlesferreira.treino.ui.today.DeleteDaySheet
import dev.charlesferreira.treino.ui.today.NoteSheet
import dev.charlesferreira.treino.ui.workouts.DangerColor

/**
 * `hist-day`: one day of the log. The workout, its duration, series and volume, the note, and
 * each exercise's sets with the unit spelled out. "Editar" turns the sets, times and note into
 * buttons that open the same sheets as Hoje (edit or delete a set, edit the note).
 */
@Composable
fun HistoryDayScreen(date: String, onBack: () -> Unit) {
    val container = appContainer()
    val vm = viewModel(key = "history-$date") { HistoryDayViewModel(container.repository, date, container.workoutTimer) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var editSet by rememberSaveable { mutableStateOf<String?>(null) }
    var editTime by rememberSaveable { mutableStateOf<String?>(null) }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val ui = state

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = nav + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Voltar" },
                contentAlignment = Alignment.Center,
            ) { Icon(TreinoIcons.Back, tint = TreinoColors.Text, size = 24.dp) }
            TText(PtBr.dayTitle(date), style = TreinoType.TitleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            if (ui != null && (ui.exercises.isNotEmpty() || ui.note != null)) {
                TText(
                    if (editing) "Pronto" else "Editar",
                    style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600),
                    color = TreinoColors.AccentLight,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { editing = !editing }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
        if (ui == null) return@Column
        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TText(
                ui.workoutName,
                style = TreinoType.Title.copy(fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.minutes?.let { Tag("$it min", mono = true) }
                Tag("${ui.seriesCount}${seriesLabel(ui.seriesCount)}", mono = true)
                ui.volumeKg?.let { VolumeTag(it) }
            }
        }
        if (ui.note != null) {
            Card(
                modifier = Modifier.then(if (editing) Modifier.clickable { noteOpen = true } else Modifier),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(TreinoIcons.Note, tint = TreinoColors.TextFaint, size = 18.dp, modifier = Modifier.padding(top = 1.dp))
                    TText(ui.note, style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TreinoColors.TextDim, modifier = Modifier.weight(1f))
                    if (editing) Icon(TreinoIcons.ChevronRight, tint = TreinoColors.TextFaint, size = 18.dp)
                }
            }
        } else if (editing) {
            GhostButton("Nota do dia", onClick = { noteOpen = true }, icon = TreinoIcons.Note, height = 44.dp, fontSize = 14.sp, iconSize = 17.dp)
        }
        if (ui.exercises.isNotEmpty()) {
            Card {
                ui.exercises.forEachIndexed { i, ex ->
                    if (i > 0) RowDivider()
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TText(
                                ex.exercise.name,
                                // The design's spans sit in 15 px lines (21 px), so the rows are a bit taller.
                                style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600),
                                modifier = Modifier.weight(1f),
                            )
                            ex.unit?.let {
                                TText(it, style = TreinoType.Label.copy(fontWeight = FontWeight.W400), color = TreinoColors.TextFaint, maxLines = 1)
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(if (editing) 8.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(if (editing) 8.dp else 4.dp),
                        ) {
                            ex.sets.forEach { s -> Entry(setText(s), editing) { editSet = s.id } }
                            ex.times.forEach { t -> Entry(timeText(t), editing) { editTime = t.id } }
                        }
                    }
                }
            }
        }
        // Not in the design: the smallest fit, while editing.
        if (editing) GhostButton("Apagar dia", onClick = { confirmDelete = true }, contentColor = DangerColor)
    }

    val sets: List<SetEntry> = ui?.exercises?.flatMap { it.sets }.orEmpty()
    val times: List<TimeEntry> = ui?.exercises?.flatMap { it.times }.orEmpty()
    val exercises = ui?.exercises?.associate { it.exercise.id to it.exercise }.orEmpty()
    sets.firstOrNull { it.id == editSet }?.let { set ->
        exercises[set.exerciseId]?.let { exercise ->
            EditSetSheet(
                exercise = exercise,
                set = set,
                onSave = { kg, reps ->
                    vm.updateSet(set.id, kg, reps)
                    editSet = null
                },
                onDelete = {
                    vm.deleteSet(set.id)
                    editSet = null
                },
                onDismiss = { editSet = null },
            )
        }
    }
    times.firstOrNull { it.id == editTime }?.let { time ->
        exercises[time.exerciseId]?.let { exercise ->
            EditTimeSheet(
                exercise = exercise,
                time = time,
                onSave = {
                    vm.updateTime(time, it)
                    editTime = null
                },
                onDelete = {
                    vm.deleteTime(time.id)
                    editTime = null
                },
                onDismiss = { editTime = null },
            )
        }
    }
    if (confirmDelete) {
        DeleteDaySheet(
            title = "Apagar este dia?",
            action = "Apagar dia",
            onConfirm = {
                confirmDelete = false
                vm.deleteDay(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (noteOpen) {
        NoteSheet(
            ui?.note,
            onSave = {
                vm.saveNote(it)
                noteOpen = false
            },
            onDismiss = { noteOpen = false },
        )
    }
}

/** A set ("18 × 10") in mono 14; while editing, a #24272C button that opens its sheet. */
@Composable
private fun Entry(text: String, editing: Boolean, onClick: () -> Unit) {
    val style = TreinoType.mono(14.sp).copy(lineHeight = 22.sp)
    if (!editing) {
        TText(text, style = style, maxLines = 1)
    } else {
        Box(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TreinoColors.Surface)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { TText(text, style = style, maxLines = 1) }
    }
}

/** `.tag.m` with the volume labelled: "volume" in sans, the number in mono. */
@Composable
private fun VolumeTag(kg: Double) {
    Box(
        Modifier
            .height(24.dp)
            .background(TreinoColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            buildAnnotatedString {
                append("volume ")
                withStyle(SpanStyle(fontFamily = TreinoFonts.JetBrainsMono, fontFeatureSettings = "tnum")) { append(volumeText(kg)) }
            },
            style = TreinoType.Tag,
            maxLines = 1,
        )
    }
}
