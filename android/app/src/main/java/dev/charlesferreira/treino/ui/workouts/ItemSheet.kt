package dev.charlesferreira.treino.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.Stepper
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TextInput
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.today.Sheet
import dev.charlesferreira.treino.ui.today.SheetTitle

/**
 * `p-item`: the exercise's prescription in this workout. Kind (load or time, stored in the
 * exercise), sets, the reps or minutes range, rest in steps of 15 s, notes. The equipment tag
 * opens the equipment sheet. "Remover do treino" removes at once: an item has no history.
 */
@Composable
fun ItemSheet(
    name: String,
    chip: String?,
    draft: ItemDraft,
    onChange: (ItemDraft) -> Unit,
    onEquipment: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SheetTitle(name) }
            if (chip != null) {
                Tag(
                    chip,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onEquipment)
                        .semantics { contentDescription = "Equipamento: $chip" },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Carga × reps", draft.kind == ExerciseKind.LOAD, Modifier.weight(1f)) { onChange(draft.copy(kind = ExerciseKind.LOAD)) }
            Chip("Tempo", draft.kind == ExerciseKind.TIME, Modifier.weight(1f)) { onChange(draft.copy(kind = ExerciseKind.TIME)) }
        }
        FieldRow("Séries") {
            Stepper(
                draft.sets.toString(),
                onMinus = { onChange(draft.setsMinus()) },
                onPlus = { onChange(draft.setsPlus()) },
                modifier = Modifier.width(170.dp).height(44.dp),
                minusLabel = "−",
                plusLabel = "+",
                minusDescription = "Menos uma série",
                plusDescription = "Mais uma série",
                minusEnabled = draft.sets > 1,
            )
        }
        val invalid = draft.range is RangeInput.Invalid
        if (draft.kind == ExerciseKind.TIME) {
            FieldRow("Minutos") {
                RangeFields(draft.minutesMin, draft.minutesMax, invalid, decimals = true) { a, b ->
                    onChange(draft.copy(minutesMin = a, minutesMax = b))
                }
            }
        } else {
            FieldRow("Repetições") {
                RangeFields(draft.repsMin, draft.repsMax, invalid, decimals = false) { a, b ->
                    onChange(draft.copy(repsMin = a, repsMax = b))
                }
            }
        }
        FieldRow("Descanso") {
            Stepper(
                restText(draft.restSec),
                onMinus = { onChange(draft.restMinus()) },
                onPlus = { onChange(draft.restPlus()) },
                modifier = Modifier.width(170.dp).height(44.dp),
                minusLabel = "−15",
                plusLabel = "+15",
                minusDescription = "Menos 15 segundos",
                plusDescription = "Mais 15 segundos",
                minusEnabled = draft.restSec > 0,
            )
        }
        TextInput(
            value = draft.notes,
            onValueChange = { onChange(draft.copy(notes = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Notas",
            height = 44.dp,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PrimaryButton("Salvar", onClick = onSave, enabled = draft.valid)
        Box(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onRemove).padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            TText("Remover do treino", style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600), color = DangerColor)
        }
    }
}

/** `.chip`: 36 dp, radius 18, 14 sp semibold; on = accent with ink. */
@Composable
internal fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (on) TreinoColors.Accent else TreinoColors.Surface, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            text,
            style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600),
            color = if (on) TreinoColors.Ink else TreinoColors.TextSoft,
            maxLines = 1,
        )
    }
}

/** Label (`.lbl` at 14 sp) on the left, the control on the right. */
@Composable
private fun FieldRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TText(label, style = TreinoType.Label.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.weight(1f))
        control()
    }
}

/** Two `.inp` boxes (56 + 2 × 14 padding = 84 wide, 44 high) in mono, "a" between them. Red outline while the range is invalid. */
@Composable
private fun RangeFields(min: String, max: String, invalid: Boolean, decimals: Boolean, onChange: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberBox(min, invalid, decimals, "Mínimo") { onChange(it, max) }
        TText("a", color = TreinoColors.TextFaint)
        NumberBox(max, invalid, decimals, "Máximo") { onChange(min, it) }
    }
}

@Composable
private fun NumberBox(value: String, invalid: Boolean, decimals: Boolean, description: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val allowed: (Char) -> Boolean = { it.isDigit() || (decimals && (it == ',' || it == '.')) }
    BasicTextField(
        value = value,
        onValueChange = { t -> onChange(t.filter(allowed).take(5)) },
        modifier = Modifier.size(width = 84.dp, height = 44.dp).semantics { contentDescription = description },
        textStyle = TreinoType.mono(15.sp).copy(textAlign = TextAlign.Center),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        cursorBrush = SolidColor(TreinoColors.Accent),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TreinoColors.Surface, shape)
                    .then(if (invalid) Modifier.border(1.dp, DangerColor, shape) else Modifier)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}
