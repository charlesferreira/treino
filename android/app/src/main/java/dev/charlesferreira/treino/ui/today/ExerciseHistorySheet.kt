package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.evolution.equipmentTag
import dev.charlesferreira.treino.ui.workouts.EquipmentSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/** One past session of an exercise: "20 nov", "16 kg" (or "35 min") and its sets. */
data class HistoryRow(val date: String, val top: String, val detail: String?, val up: Boolean)

/**
 * Every session of the exercise before [today] (or the last [limit]), newest first. [HistoryRow.up] marks a top
 * load above the session before it.
 */
fun historyRows(days: List<ExerciseDay>, today: String, limit: Int = Int.MAX_VALUE): List<HistoryRow> {
    val past = days.filter { it.date < today && (it.sets.isNotEmpty() || it.times.isNotEmpty()) }.sortedBy { it.date }
    return past.mapIndexed { i, day ->
        val max = day.sets.maxOfOrNull { it.weightKg }
        val before = past.getOrNull(i - 1)?.sets?.maxOfOrNull { it.weightKg }
        HistoryRow(
            date = PtBr.shortDate(day.date),
            top = loggedSummary(day.sets, day.times).orEmpty(),
            detail = day.sets.sortedBy { it.position }.takeIf { it.isNotEmpty() }?.joinToString("   ") { setText(it) },
            up = max != null && before != null && max > before,
        )
    }.takeLast(limit).reversed()
}

/** "Histórico": every past session of the exercise, scrolling, opened from its name in the workout. */
@Composable
fun ExerciseHistorySheet(exerciseId: String, today: String, onDismiss: () -> Unit) {
    val container = appContainer()
    val flow = remember(exerciseId) { container.repository.observeExercise(exerciseId) }
    val exercise by flow.collectAsStateWithLifecycle(null)
    val historyFlow = remember(exerciseId) { container.repository.observeExerciseHistory(exerciseId) }
    val days by historyFlow.collectAsStateWithLifecycle(null)
    var editing by remember(exerciseId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = exercise
    if (editing && current != null) {
        EquipmentSheet(
            exercise = current,
            prescription = null,
            onSave = { updated ->
                scope.launch { container.repository.updateExercise(updated) }
                editing = false
            },
            onDismiss = { editing = false },
        )
        return
    }
    Sheet(onDismiss) {
        SheetTitle(exercise?.name.orEmpty())
        // Equipment, unilateral or bodyweight can be fixed here, mid-workout.
        current?.let { ex ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TreinoColors.Surface, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = { editing = true })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TText("Equipamento", style = TreinoType.Row, modifier = Modifier.weight(1f), maxLines = 1)
                TText(equipmentTag(ex) ?: "Outro", style = TreinoType.Body, color = TreinoColors.TextDim, maxLines = 1)
                Icon(TreinoIcons.ChevronRight, tint = TreinoColors.TextFaint, modifier = Modifier.padding(start = 6.dp))
            }
        }
        val rows = days?.let { historyRows(it, today) } ?: return@Sheet
        if (rows.isEmpty()) {
            TText("Primeira vez.", style = TreinoType.Body, color = TreinoColors.TextDim)
            return@Sheet
        }
        Card(modifier = Modifier.heightIn(max = 440.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, row ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TText(row.date, style = TreinoType.Row, modifier = Modifier.weight(1f), maxLines = 1)
                            TText(
                                if (row.up) "↑ ${row.top}" else row.top,
                                style = TreinoType.mono(15.sp, color = if (row.up) TreinoColors.AccentLight else TreinoColors.Text),
                                maxLines = 1,
                            )
                        }
                        row.detail?.let { TText(it, style = TreinoType.mono(13.sp, color = TreinoColors.TextFaint), maxLines = 1) }
                    }
                }
            }
        }
    }
}
