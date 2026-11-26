package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.Stepper
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import kotlin.math.max

/**
 * `h-time`: a time exercise. The item's note, the last session ("35 min 5,2 km esteira"),
 * minutes in steps of 5, distance, the place and "Registrar". Today's entries show above the
 * fields, done-marked; tapping one edits it.
 */
@Composable
fun TimeExerciseCard(item: ItemUi, draft: TimeDraft, actions: WorkoutActions) {
    Card(
        borderColor = TreinoColors.AccentBorder,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TText(item.exercise.name, style = TreinoType.Heading.copy(letterSpacing = TreinoType.Body.letterSpacing), modifier = Modifier.weight(1f))
            TText(item.target, style = TreinoType.mono(13.sp, color = TreinoColors.TextFaint), maxLines = 1)
        }
        (item.item.notes ?: item.exercise.notes)?.takeIf { it.isNotBlank() }?.let {
            TText(it, style = TreinoType.Row.copy(fontWeight = TreinoType.Body.fontWeight), color = TreinoColors.TextDim)
        }
        val last = item.lastTimes.maxByOrNull { it.position }
        if (item.lastDate != null && last != null) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TText(PtBr.shortDate(item.lastDate), style = TreinoType.Label, maxLines = 1)
                TText(timeText(last), style = TreinoType.mono(12.sp, color = TreinoColors.TextDim), maxLines = 1)
            }
        }
        item.times.forEach { time ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.onEditLoggedTime(time.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DoneMark()
                TText(timeText(time), style = TreinoType.mono(14.sp), maxLines = 1)
            }
        }
        Field("Minutos") {
            Stepper(
                value = PtBr.number(draft.minutes),
                onMinus = { actions.onEditTime(item) { it.copy(minutes = max(0.0, it.minutes - MINUTES_STEP)) } },
                onPlus = { actions.onEditTime(item) { it.copy(minutes = it.minutes + MINUTES_STEP) } },
                modifier = Modifier.fillMaxWidth(),
                minusLabel = "−5",
                plusLabel = "+5",
                minusDescription = "Menos 5 minutos",
                plusDescription = "Mais 5 minutos",
                valueFontSize = 17.sp,
            )
        }
        Field("Distância") {
            val shape = RoundedCornerShape(14.dp)
            BasicTextField(
                value = draft.km,
                onValueChange = { text -> actions.onEditTime(item) { it.copy(km = text.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                textStyle = TreinoType.mono(15.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(TreinoColors.Accent),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxSize().background(TreinoColors.Surface, shape).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.weight(1f)) { inner() }
                        TText("km", style = TreinoType.Body, color = TreinoColors.TextFaint)
                    }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLACES.forEach { place ->
                Chip(
                    label = place.replaceFirstChar { it.uppercase() },
                    on = draft.place == place,
                    onClick = { actions.onEditTime(item) { it.copy(place = if (it.place == place) null else place) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PrimaryButton("Registrar", onClick = { actions.onLogTime(item, draft) }, icon = TreinoIcons.Check)
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TText(label, style = TreinoType.Label)
        content()
    }
}

/** `.chip`: 36 dp pill, #24272C with #D6D2CB text; on: accent with ink text. */
@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (on) TreinoColors.Accent else TreinoColors.Surface, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            label,
            style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
            color = if (on) TreinoColors.Ink else TreinoColors.TextSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
