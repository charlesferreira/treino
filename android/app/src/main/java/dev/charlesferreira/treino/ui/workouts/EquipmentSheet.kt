package dev.charlesferreira.treino.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.Stepper
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Toggle
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.Overlay
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `dumbbell-equipment`, in the colors of direction B: equipment, "Volume conta 2 halteres" for
 * dumbbells, the plate weight for machines, the step of + and −, and the prescription of the item
 * it was opened from ([prescription], null hides it). [onSave] gets the exercise with the changes.
 */
@Composable
fun EquipmentSheet(
    exercise: Exercise,
    prescription: List<Pair<String, String>>?,
    onSave: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(exercise.id) { mutableStateOf(EquipmentDraft.of(exercise)) }
    Overlay {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TreinoColors.Scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = top + 16.dp)
                    .clip(shape)
                    .background(TreinoColors.Card, shape)
                    .drawBehind { drawLine(TreinoColors.BorderStrong, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx()) }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = maxOf(34.dp, bottom + 16.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .background(TreinoColors.SurfaceHigh, RoundedCornerShape(3.dp)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TText(exercise.name, style = TreinoType.Heading, modifier = Modifier.weight(1f), maxLines = 2)
                    Box(
                        Modifier
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TreinoColors.Surface, CircleShape)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .semantics { contentDescription = "Fechar" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(TreinoIcons.Close, tint = TreinoColors.Text) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Eyebrow("Equipamento")
                        OptionGrid(EQUIPMENT_OPTIONS.map { (eq, label) -> Triple(label, eq == draft.equipment) { draft = draft.select(eq) } })
                    }
                    Column {
                        when (draft.equipment) {
                            Equipment.DUMBBELL -> Field {
                                Column(Modifier.weight(1f)) {
                                    TText("Volume conta 2 halteres", style = TreinoType.Body.copy(fontWeight = FontWeight.W600))
                                    TText("Carga anotada por halter", style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TreinoColors.TextFaint)
                                }
                                Toggle(draft.countsTwoDumbbells, { draft = draft.copy(countsTwoDumbbells = it) })
                            }
                            Equipment.MACHINE -> Field {
                                TText("Peso da placa", style = TreinoType.Body.copy(fontWeight = FontWeight.W600), modifier = Modifier.weight(1f))
                                Stepper(
                                    PtBr.kg(draft.plateKg),
                                    onMinus = { draft = draft.plateMinus() },
                                    onPlus = { draft = draft.platePlus() },
                                    modifier = Modifier.width(170.dp).height(44.dp),
                                    minusLabel = "−",
                                    plusLabel = "+",
                                    minusDescription = "Menos meio quilo",
                                    plusDescription = "Mais meio quilo",
                                    minusEnabled = draft.plateKg > PLATE_STEP,
                                )
                            }
                            else -> Unit
                        }
                        Column(
                            Modifier.fillMaxWidth().bottomBorder().padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TText("Passo do + e −", style = TreinoType.Body.copy(fontWeight = FontWeight.W600))
                            OptionGrid(draft.stepOptions().map { o -> Triple(o.label, o.selected) { draft = draft.chooseStep(o.kg) } })
                        }
                    }
                    if (prescription != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Eyebrow("Prescrição")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                prescription.forEach { (label, value) ->
                                    Column(
                                        Modifier.weight(1f).background(TreinoColors.Surface, RoundedCornerShape(12.dp)).padding(10.dp),
                                    ) {
                                        TText(label, style = TreinoType.Label.copy(fontWeight = FontWeight.W400, color = TreinoColors.TextFaint), maxLines = 1)
                                        TText(value, style = TreinoType.mono(24.sp), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                PrimaryButton("Salvar", onClick = { onSave(draft.applyTo(exercise)) })
            }
        }
    }
}

/** The design's `.eyebrow` in direction B: the `.sec` heading without its side padding. */
@Composable
private fun Eyebrow(text: String) {
    TText(text.uppercase(), style = TreinoType.Section, maxLines = 1)
}

/** `.field`: min 56 dp, 4 dp side padding, 1 dp line under it. */
@Composable
private fun Field(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).bottomBorder().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private fun Modifier.bottomBorder(): Modifier = drawBehind {
    drawLine(TreinoColors.BorderStrong, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
}

/** Three columns of `.opt` buttons (48 dp, radius 12); on = accent outline and text on accent at 14%. */
@Composable
private fun OptionGrid(options: List<Triple<String, Boolean, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { (label, on, onClick) ->
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(shape)
                            .background(if (on) TreinoColors.AccentSoft else TreinoColors.Surface, shape)
                            .border(1.dp, if (on) TreinoColors.Accent else TreinoColors.BorderStrong, shape)
                            .clickable(role = Role.RadioButton, onClick = onClick)
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        TText(
                            label,
                            style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600),
                            color = if (on) TreinoColors.AccentLight else TreinoColors.TextSoft,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
