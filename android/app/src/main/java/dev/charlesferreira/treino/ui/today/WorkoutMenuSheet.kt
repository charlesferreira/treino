package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.workouts.DangerColor

/**
 * ⋮ of the workout under way, where the avatar was: what the day still needs while the tabs are
 * gone. Built like the workout's ⋮ in Treinos (not in the design).
 */
@Composable
fun WorkoutMenuSheet(
    workoutName: String,
    hasNote: Boolean,
    onNote: () -> Unit,
    onSwitch: () -> Unit,
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        SheetTitle(workoutName)
        MenuRow(if (hasNote) "Editar nota do dia" else "Nota do dia", onClick = onNote)
        MenuRow("Trocar treino", onClick = onSwitch)
        MenuRow("Adicionar exercício", onClick = onAddExercise)
        MenuRow("Terminar treino", onClick = onFinish)
        // The one destructive action, last and apart.
        Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
        MenuRow("Apagar treino de hoje", color = DangerColor, onClick = onDelete)
    }
}

@Composable
private fun MenuRow(text: String, color: Color = TreinoColors.Text, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TreinoColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { TText(text, style = TreinoType.ButtonGhost.copy(color = color), maxLines = 1) }
}
