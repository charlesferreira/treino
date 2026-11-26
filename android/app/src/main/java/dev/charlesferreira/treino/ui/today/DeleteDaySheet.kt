package dev.charlesferreira.treino.ui.today

import androidx.compose.runtime.Composable
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.workouts.DangerColor

/**
 * Confirms deleting a day's workout (Hoje's "Apagar treino de hoje", the history day's "Apagar
 * dia"). Not in the design; laid out like "Apagar treino" of Treinos.
 */
@Composable
fun DeleteDaySheet(title: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        SheetTitle(title)
        TText(
            "As séries, os tempos e a nota do dia somem. Não pode ser desfeito.",
            style = TreinoType.Body,
            color = TreinoColors.TextDim,
        )
        GhostButton(action, onClick = onConfirm, contentColor = DangerColor)
    }
}
