package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `h-switch`: "Treino de hoje". The workouts in rotation order; today's is bold with the check.
 * Picking one saves it as the day's choice (`chooseWorkout`).
 */
@Composable
fun SwitchWorkoutSheet(
    options: List<SwitchOption>,
    onChoose: (String) -> Unit,
    /** Null in a workout under way, where Treinos is out of reach. */
    onManage: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        SheetTitle("Treino de hoje")
        Card(
            modifier = Modifier.heightIn(max = 420.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, option ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onChoose(option.workout.id) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            TText(
                                option.workout.name,
                                style = TreinoType.Body.copy(fontWeight = if (option.selected) FontWeight.W700 else FontWeight.W500),
                                maxLines = 1,
                            )
                            option.subtitle?.let {
                                TText(it, style = TreinoType.Body.copy(fontSize = TreinoType.Label.fontSize, lineHeight = TreinoType.Label.lineHeight), color = TreinoColors.TextFaint, maxLines = 1)
                            }
                        }
                        if (option.selected) DoneMark()
                    }
                }
            }
        }
        onManage?.let { GhostButton("Gerenciar treinos", onClick = it, icon = TreinoIcons.Lines) }
    }
}
