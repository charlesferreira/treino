package dev.charlesferreira.treino.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
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
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.SectionLabel
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TextInput
import dev.charlesferreira.treino.ui.components.Toggle
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/*
 * Phase 1 screens: enough to walk the navigation and see the theme with real data. Phase 3
 * replaces each one with the screen of the design.
 */

@Composable
fun OnboardingScreen(onCreateWorkout: () -> Unit, onBackup: () -> Unit) {
    val container = appContainer()
    val vm = viewModel { OnboardingViewModel(container.repository) }
    var name by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Column(
            Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 76.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(64.dp).background(TreinoColors.Accent, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(TreinoIcons.Dumbbell, tint = TreinoColors.Ink, size = 32.dp, strokeWidth = 2.4f) }
            TText("Bora treinar.", style = TreinoType.Display)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TText("Como você se chama?", style = TreinoType.Label.copy(fontSize = 14.sp, lineHeight = 20.sp))
                TextInput(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp,
                    fontSize = 17.sp,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton("Criar meu primeiro treino", onClick = { vm.start(name, onCreateWorkout) })
            GhostButton("Já tenho um backup", onClick = onBackup, icon = TreinoIcons.Upload, height = 54.dp)
        }
    }
}

/** Header of the sub screens (`p-profile`): back button and an 18 sp bold title. */
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Voltar" },
            contentAlignment = Alignment.Center,
        ) { Icon(TreinoIcons.Back, tint = TreinoColors.Text, size = 24.dp) }
        TText(title, style = TreinoType.TitleSmall, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

/** A sub screen still to be built (Phase 2 and 3): the header and its title. */
@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubScreenHeader(title, onBack)
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onLoadReview: () -> Unit, onBackup: () -> Unit, onTimerSpike: () -> Unit) {
    val container = appContainer()
    val vm = viewModel { ProfileViewModel(container.repository) }
    val s by vm.settings.collectAsStateWithLifecycle()
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
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(s.profileName, size = 64.dp, accent = true)
            TText(s.profileName.ifEmpty { "Sem nome" }, style = TreinoType.Body.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.W700))
        }
        SectionLabel("Cargas")
        Card { CardRow("Revisar cargas antigas", onClick = onLoadReview, trailing = { Chevron() }) }
        SectionLabel("Descanso")
        Card {
            CardRow("Som no fim", subtitle = "segue o volume de mídia", trailing = { Toggle(s.sound, vm::setSound) })
            CardRow("Vibração no fim", subtitle = "também no silencioso", divider = true, trailing = { Toggle(s.vibration, vm::setVibration) })
            CardRow("Tela sempre ligada no treino", divider = true, trailing = { Toggle(s.keepScreenOn, vm::setKeepScreenOn) })
        }
        SectionLabel("Dados")
        Card { CardRow("Backup", onClick = onBackup, trailing = { Chevron() }) }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Debug")
            Card {
                CardRow("Timer (spike da Fase 0)", onClick = onTimerSpike, trailing = { Chevron() })
                CardRow("Criar treino de exemplo", subtitle = "Superior B, Pernas A e corridas, 9 semanas", divider = true, onClick = vm::createSampleWorkout)
            }
        }
    }
}

@Composable
private fun Chevron() = Icon(TreinoIcons.ChevronRight, tint = TreinoColors.TextFaint, size = 18.dp)
