package dev.charlesferreira.treino.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TextInput
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.screens.OnboardingViewModel
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `onboarding`, the first use: the name, then "Criar meu primeiro treino" (seeds the catalog and
 * opens Treinos) or "Já tenho um backup" (the Backup screen, how the PWA data comes over).
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
