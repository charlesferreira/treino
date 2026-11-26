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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.ui.components.Avatar
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `h-start`: before starting. The day's workout (the rotation's or the one chosen), its exercises
 * with the target and the last session's top load, "Nota do dia" and "Começar treino".
 */
@Composable
fun StartScreen(
    ui: TodayUi,
    onSwitch: () -> Unit,
    onProfile: () -> Unit,
    onNote: () -> Unit,
    onStart: () -> Unit,
    onCreateWorkout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = TabBarDefaults.ContentClearance + 54.dp + 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    TText(ui.dateLabel, style = TreinoType.Label)
                    if (ui.workout != null) {
                        WorkoutTitleButton(ui.workout.name, onSwitch)
                    } else {
                        TText("Sem treino", style = TreinoType.Title, maxLines = 1)
                    }
                }
                Avatar(ui.profileName, onClick = onProfile)
            }
            if (ui.workout == null) {
                TText(
                    "Crie um treino na aba Treinos.",
                    style = TreinoType.Body,
                    color = TreinoColors.TextDim,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            } else {
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("${ui.items.size} exercícios")
                    Tag("${ui.setCount} séries", mono = true)
                    ui.lastDoneDate?.let { Tag("último em ${PtBr.shortDate(it)}") }
                }
                if (ui.items.isNotEmpty()) {
                    Card(contentPadding = PaddingValues(vertical = 4.dp)) {
                        ui.items.forEachIndexed { i, item -> StartRow(item, divider = i > 0) }
                    }
                }
                GhostButton(
                    "Nota do dia",
                    onClick = onNote,
                    icon = TreinoIcons.Note,
                    height = 44.dp,
                    fontSize = 14.sp,
                    iconSize = 17.dp,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = TabBarDefaults.bottomOffset() + 80.dp),
        ) {
            if (ui.workout == null) {
                PrimaryButton("Criar treino", onClick = onCreateWorkout, icon = TreinoIcons.Plus)
            } else {
                PrimaryButton("Começar treino", onClick = onStart, icon = TreinoIcons.Play)
            }
        }
    }
}

/** The 30 sp workout name with the chevron: opens "Trocar o treino". */
@Composable
fun WorkoutTitleButton(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Trocar treino" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TText(name, style = TreinoType.Title, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Icon(TreinoIcons.ChevronDown, tint = TreinoColors.TextFaint)
    }
}

/** `.row`: number, name, target and the last session's top load. */
@Composable
private fun StartRow(item: ItemUi, divider: Boolean) {
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TText("${item.number}", style = TreinoType.mono(13.sp, color = TreinoColors.TextFaint), modifier = Modifier.width(18.dp), maxLines = 1)
        TText(item.exercise.name, style = TreinoType.Row, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        TText(item.target, style = TreinoType.mono(12.sp, color = TreinoColors.TextFaint), maxLines = 1)
        TText(
            loggedSummary(item.lastSets, item.lastTimes).orEmpty(),
            style = TreinoType.mono(13.sp, color = TreinoColors.TextDim),
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
