package dev.charlesferreira.treino.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.BuildConfig
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.ui.components.Avatar
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.CardRow
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.SectionLabel
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TextInput
import dev.charlesferreira.treino.ui.components.Toggle
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.screens.SubScreenHeader
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.today.Sheet
import dev.charlesferreira.treino.ui.today.SheetTitle

/**
 * `p-profile`: name and avatar (tap to change the name, as the PWA does), the load review with
 * the number of sessions to check, the rest switches and the backup. Debug builds add the timer
 * spike and the sample workout at the end.
 */
@Composable
fun ProfileScreen(onBack: () -> Unit, onLoadReview: () -> Unit, onBackup: () -> Unit, onTimerSpike: () -> Unit) {
    val container = appContainer()
    val vm = viewModel { ProfileViewModel(container.repository) }
    val ui by vm.state.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    val state = ui ?: return
    val s = state.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubScreenHeader("Perfil", onBack)
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClickLabel = "Mudar o nome") { editingName = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(s.profileName, size = 64.dp, accent = true)
            Column {
                TText(
                    s.profileName.ifEmpty { "Quem está treinando?" },
                    style = TreinoType.Body.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.W700),
                    color = if (s.profileName.isEmpty()) TreinoColors.TextDim else TreinoColors.Text,
                    maxLines = 1,
                )
                state.trainingSince?.let {
                    TText(it, style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TreinoColors.TextFaint)
                }
            }
        }
        SectionLabel("Cargas")
        Card {
            CardRow(
                "Revisar cargas antigas",
                subtitle = reviewSubtitle(state.pendingReview),
                onClick = onLoadReview,
                trailing = { if (state.pendingReview > 0) Tag(state.pendingReview.toString(), accent = true) else Chevron() },
            )
        }
        SectionLabel("Descanso")
        Card {
            CardRow("Som no fim", subtitle = "segue o volume de mídia", trailing = { Toggle(s.sound, vm::setSound) })
            CardRow("Vibração no fim", subtitle = "também no silencioso", divider = true, trailing = { Toggle(s.vibration, vm::setVibration) })
            CardRow("Tela sempre ligada no treino", divider = true, trailing = { Toggle(s.keepScreenOn, vm::setKeepScreenOn) })
        }
        SectionLabel("Dados")
        Card { CardRow("Backup", subtitle = state.backup, onClick = onBackup, trailing = { Chevron() }) }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Debug")
            Card {
                CardRow("Timer (spike da Fase 0)", onClick = onTimerSpike, trailing = { Chevron() })
                CardRow("Criar treino de exemplo", subtitle = "Superior B, Pernas A e corridas, 9 semanas", divider = true, onClick = vm::createSampleWorkout)
            }
        }
    }
    if (editingName) {
        NameSheet(s.profileName, onSave = { vm.setName(it); editingName = false }, onDismiss = { editingName = false })
    }
}

/** "Seu nome": the PWA's text sheet, with the sheet, input and button of the design. */
@Composable
private fun NameSheet(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(name) }
    val save = { if (text.isNotBlank()) onSave(text) }
    Sheet(onDismiss) {
        SheetTitle("Seu nome")
        TextInput(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Como te chamam",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        PrimaryButton("Salvar", onClick = save, enabled = text.isNotBlank())
    }
}

@Composable
internal fun Chevron() = Icon(TreinoIcons.ChevronRight, tint = TreinoColors.TextFaint, size = 18.dp)
