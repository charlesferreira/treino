package dev.charlesferreira.treino.ui.today

import androidx.compose.runtime.Composable
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TreinoIcons
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

/**
 * "Terminar treino" with sets still to do. With some done it asks before ending; with none, the
 * only sensible end is to throw the day away. Not in the design.
 */
@Composable
fun FinishSheet(done: Int, total: Int, onFinish: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        if (done == 0) {
            SheetTitle("Nenhuma série registrada")
            TText("O treino de hoje some. Não pode ser desfeito.", style = TreinoType.Body, color = TreinoColors.TextDim)
            GhostButton("Descartar treino", onClick = onDiscard, contentColor = DangerColor)
        } else {
            val left = total - done
            SheetTitle(if (left == 1) "Falta 1 série" else "Faltam $left séries")
            PrimaryButton("Terminar treino", onClick = onFinish, icon = TreinoIcons.Check)
        }
        GhostButton("Continuar treino", onClick = onDismiss)
    }
}

/**
 * "Ainda treinando?": a workout with no set for hours. Ending it puts the end at the last set, so
 * the duration stays true. Not in the design.
 */
@Composable
fun StaleSheet(lastAt: Long, onFinish: () -> Unit, onContinue: () -> Unit) {
    val time = java.time.Instant.ofEpochMilli(lastAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val hhmm = "%02d:%02d".format(time.hour, time.minute)
    Sheet(onContinue) {
        SheetTitle("Ainda treinando?")
        TText("Último registro às $hhmm.", style = TreinoType.Body, color = TreinoColors.TextDim)
        PrimaryButton("Terminar às $hhmm", onClick = onFinish, icon = TreinoIcons.Check)
        GhostButton("Continuar treino", onClick = onContinue)
    }
}
