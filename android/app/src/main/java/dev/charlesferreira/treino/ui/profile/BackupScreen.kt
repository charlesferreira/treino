package dev.charlesferreira.treino.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.SectionLabel
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.screens.SubScreenHeader
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `p-backup`: export (format 3, saved where the person picks through the system's "Salvar como"),
 * import (the system file picker, then the preview of what the file brings, then "Importar"
 * replaces everything). [onImported] runs after the data was replaced.
 */
@Composable
fun BackupScreen(onBack: () -> Unit, onImported: () -> Unit) {
    val container = appContainer()
    val resolver = LocalContext.current.contentResolver
    val vm = viewModel { BackupViewModel(container.repository, resolver) }
    val ui by vm.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::pick)
    }

    val state = ui ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubScreenHeader("Backup", onBack)
        Card(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TText("Último backup", style = TreinoType.Body.copy(fontWeight = FontWeight.W600))
                TText(state.lastBackup, style = TreinoType.Mono, color = TreinoColors.TextDim)
            }
            PrimaryButton(
                "Exportar dados",
                onClick = { exportLauncher.launch(vm.exportFileName()) },
                icon = TreinoIcons.Download,
                height = 48.dp,
                fontSize = 15.sp,
                iconSize = 20.dp,
                iconStroke = 2f,
            )
            GhostButton(
                "Importar arquivo",
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                icon = TreinoIcons.Upload,
                height = 46.dp,
            )
            state.error?.let { TText(it, style = small, color = TreinoColors.AccentLight) }
        }
        state.chosen?.let { chosen ->
            SectionLabel("Arquivo escolhido")
            ChosenFileCard(chosen, importing = state.importing, onImport = { vm.confirmImport(onImported) })
        }
    }
}

private val small = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun ChosenFileCard(chosen: ChosenFile, importing: Boolean, onImport: () -> Unit) {
    Card(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            TText(chosen.name, style = TreinoType.mono(14.sp), maxLines = 1)
            when (chosen) {
                is ChosenFile.Reading -> TText("lendo o arquivo…", style = caption, color = TreinoColors.TextFaint)
                is ChosenFile.Ready -> TText(chosen.ui.source, style = caption, color = TreinoColors.TextFaint)
                is ChosenFile.Failed -> Unit
            }
        }
        when (chosen) {
            is ChosenFile.Reading -> Unit
            is ChosenFile.Failed -> TText(chosen.message, style = small, color = TreinoColors.AccentLight)
            is ChosenFile.Ready -> {
                chosen.ui.stats.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (n, label) -> Stat(n, label, Modifier.weight(1f)) }
                    }
                }
                TText(chosen.ui.summary, style = small, color = TreinoColors.TextDim)
                PrimaryButton("Importar", onClick = onImport, enabled = !importing, height = 48.dp, fontSize = 15.sp)
            }
        }
    }
}

private val caption = TreinoType.Body.copy(fontSize = 12.sp, lineHeight = 17.sp)

/** A box of the grid: the number (mono 20 bold) over its faint 12 sp label. */
@Composable
private fun Stat(number: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(TreinoColors.Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TText(number, style = TreinoType.mono(20.sp, FontWeight.W700), maxLines = 1)
        // Inline in the box's 15 px line, so its line is 21 tall.
        TText(label, style = caption.copy(lineHeight = 21.sp), color = TreinoColors.TextFaint, maxLines = 1)
    }
}
