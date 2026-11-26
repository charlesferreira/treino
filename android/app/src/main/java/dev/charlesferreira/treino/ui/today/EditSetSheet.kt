package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.Steps
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.Stepper
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** The design's delete red (`#FF8A7A`). */
private val Danger = Color(0xFFFF8A7A)

/** `h-edit-set`: change the load and reps of a logged set, or delete it. */
@Composable
fun EditSetSheet(
    exercise: Exercise,
    set: SetEntry,
    onSave: (Double, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by rememberSaveable(set.id) { mutableDoubleStateOf(set.weightKg) }
    var reps by rememberSaveable(set.id) { mutableIntStateOf(set.reps) }
    Sheet(onDismiss) {
        SheetHeading("${exercise.name}, série ${set.position + 1}", set.loggedAt)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                value = PtBr.kg(weight),
                onMinus = { weight = Steps.down(weight, exercise) },
                onPlus = { weight = Steps.up(weight, exercise) },
                modifier = Modifier.weight(1f),
                minusLabel = "−${stepButton(exercise)}",
                plusLabel = "+${stepButton(exercise)}",
                caption = stepCaption(exercise),
                minusDescription = "Menos ${stepDescription(exercise)}",
                plusDescription = "Mais ${stepDescription(exercise)}",
            )
            Stepper(
                value = "$reps",
                onMinus = { reps = max(0, reps - 1) },
                onPlus = { reps += 1 },
                modifier = Modifier.weight(1f),
                minusDescription = "Menos 1 rep",
                plusDescription = "Mais 1 rep",
            )
        }
        PrimaryButton("Salvar", onClick = { onSave(weight, reps) })
        GhostButton("Apagar série", onClick = onDelete, icon = TreinoIcons.Trash, contentColor = Danger)
    }
}

/** A logged time entry: minutes only (km and place stay), or delete it. Not in the design. */
@Composable
fun EditTimeSheet(
    exercise: Exercise,
    time: TimeEntry,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by rememberSaveable(time.id) { mutableDoubleStateOf(time.minutes) }
    Sheet(onDismiss) {
        SheetHeading("${exercise.name}, ${time.position + 1}º registro", time.loggedAt)
        Stepper(
            value = PtBr.number(minutes),
            onMinus = { minutes = max(0.0, minutes - MINUTES_STEP) },
            onPlus = { minutes += MINUTES_STEP },
            modifier = Modifier.fillMaxWidth(),
            minusLabel = "−5",
            plusLabel = "+5",
            valueFontSize = 17.sp,
        )
        PrimaryButton("Salvar", onClick = { onSave(minutes) })
        GhostButton("Apagar registro", onClick = onDelete, icon = TreinoIcons.Trash, contentColor = Danger)
    }
}

@Composable
private fun SheetHeading(title: String, loggedAt: Long?) {
    Column {
        SheetTitle(title)
        loggedAt?.let {
            TText("registrada às ${hourMinute(it)}", style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TreinoColors.TextFaint)
        }
    }
}

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/** "19:12" in the device's zone. */
fun hourMinute(epochMs: Long): String = HH_MM.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
