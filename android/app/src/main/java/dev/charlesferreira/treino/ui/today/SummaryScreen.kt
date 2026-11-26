package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
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
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `b-summary`: the workout done. Duration, sets, volume (labelled as volume) and reps, each with
 * the workout's previous session under it, and the exercises whose top load went up. Facts only.
 */
@Composable
fun SummaryScreen(date: String, onClose: () -> Unit) {
    val container = appContainer()
    val vm = viewModel(key = "summary-$date") { SummaryViewModel(container.repository, date) }
    val state by vm.state.collectAsStateWithLifecycle()
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    val ui = state ?: return
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = maxOf(28.dp, nav + 8.dp)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = bottom + 54.dp + 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    TText("Treino concluído", style = TreinoType.Label, color = TreinoColors.AccentLight)
                    TText(ui.workoutName, style = TreinoType.Display)
                    TText(ui.dateLine, style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TreinoColors.TextDim)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TreinoColors.Card, CircleShape)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = "Fechar" },
                    contentAlignment = Alignment.Center,
                ) { Icon(TreinoIcons.Close, tint = TreinoColors.Text) }
            }
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val prev = ui.previousDate?.let(PtBr::shortDate)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(
                        label = "Duração",
                        value = ui.minutes?.toString() ?: "-",
                        suffix = if (ui.minutes != null) " min" else null,
                        compare = prev?.let { p -> ui.previousMinutes?.let { "$p: $it" } },
                    )
                    Tile(
                        label = "Séries",
                        value = "${ui.setsDone}",
                        suffix = "/${ui.setsTotal}",
                        compare = prev?.let { p -> ui.previousSets?.let { "$p: $it" } },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val change = ui.volumeChangePercent
                    Tile(
                        label = "Volume, carga × reps",
                        value = PtBr.number(ui.volumeKg),
                        suffix = " kg",
                        compare = if (prev != null && change != null) "${if (change > 0) "+" else if (change < 0) "−" else ""}${kotlin.math.abs(change)}% vs $prev" else null,
                        compareColor = if ((change ?: 0) > 0) TreinoColors.AccentLight else TreinoColors.TextFaint,
                    )
                    Tile(
                        label = "Reps",
                        value = "${ui.reps}",
                        suffix = null,
                        compare = prev?.let { p -> ui.previousReps?.let { "$p: $it" } },
                    )
                }
            }
            if (ui.lifts.isNotEmpty()) Lifts(ui.lifts)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(TreinoColors.Card)
                    .clickable(role = Role.Button) { noteOpen = true },
                contentAlignment = Alignment.Center,
            ) { TText("Nota", style = TreinoType.ButtonGhost, maxLines = 1) }
            PrimaryButton("Concluir", onClick = onClose, modifier = Modifier.weight(2f))
        }
    }
    if (noteOpen) {
        NoteSheet(
            ui.note,
            onSave = {
                vm.saveNote(it)
                noteOpen = false
            },
            onDismiss = { noteOpen = false },
        )
    }
}

/** `.tile`: label, the number in mono 30 bold with a faint 15 suffix, and the comparison. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(
    label: String,
    value: String,
    suffix: String?,
    compare: String?,
    compareColor: Color = TreinoColors.TextFaint,
) {
    Card(
        modifier = Modifier.weight(1f),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TText(label, style = TreinoType.Label, maxLines = 1)
        TText(
            buildAnnotatedString {
                append(value)
                if (suffix != null) {
                    withStyle(SpanStyle(fontSize = 15.sp, color = TreinoColors.TextFaint)) { append(suffix) }
                }
            },
            style = TreinoType.mono(30.sp, weight = FontWeight.W700),
            maxLines = 1,
        )
        if (compare != null) TText(compare, style = TreinoType.mono(12.sp, color = compareColor), maxLines = 1)
    }
}

/** "Subiu de carga" with the count badge, one row per exercise: "14 → 16 kg" and the two bars. */
@Composable
private fun Lifts(lifts: List<LiftUi>) {
    Card(cornerRadius = 20.dp, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TText("Subiu de carga", style = TreinoType.Body.copy(fontWeight = FontWeight.W700))
            Box(
                Modifier
                    .height(24.dp)
                    .widthIn(min = 24.dp)
                    .background(TreinoColors.Accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                TText(
                    "${lifts.size}",
                    style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.W700),
                    color = TreinoColors.Ink,
                    maxLines = 1,
                )
            }
        }
        lifts.forEachIndexed { i, lift ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TText(lift.name, style = TreinoType.Row, maxLines = 1, modifier = Modifier.weight(1f))
                    TText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TreinoColors.TextFaint)) { append("${PtBr.number(lift.fromKg)} →") }
                            append(" ${PtBr.kg(lift.toKg)}")
                        },
                        style = TreinoType.mono(14.sp),
                        maxLines = 1,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val full = maxWidth * 0.8f
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            Modifier
                                .width(full * (lift.fromKg / lift.toKg).toFloat().coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(TreinoColors.SurfaceHigh, RoundedCornerShape(3.dp)),
                        )
                        Box(Modifier.width(full).height(6.dp).background(TreinoColors.Accent, RoundedCornerShape(3.dp)))
                    }
                }
                TText(lift.note, style = TreinoType.Body.copy(fontSize = 11.sp, lineHeight = 15.sp), color = TreinoColors.TextFaint, maxLines = 1)
            }
        }
    }
}
