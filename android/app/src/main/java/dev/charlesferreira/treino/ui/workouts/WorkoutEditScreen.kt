package dev.charlesferreira.treino.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.today.Sheet
import dev.charlesferreira.treino.ui.today.SheetTitle

/** "Remover do treino" red. */
internal val DangerColor = Color(0xFFFF8A7A)

private sealed interface EditSheet {
    data object Menu : EditSheet
    data object Rename : EditSheet
    data object ConfirmDelete : EditSheet
    data class Item(val itemId: String) : EditSheet
    data class Equipment(val itemId: String) : EditSheet
}

/**
 * `p-workout-edit`: the workout's items (tap = `p-item`, grip = reorder), "Adicionar exercício",
 * and in "⋮" rename, duplicate and delete. [openItem] is the item just added by the picker,
 * whose sheet opens on arrival.
 */
@Composable
fun WorkoutEditScreen(
    workoutId: String,
    openItem: String?,
    onOpenItemShown: () -> Unit,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val container = appContainer()
    val vm = viewModel(key = "workout-$workoutId") { WorkoutEditViewModel(container.repository, workoutId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exists by vm.exists.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<EditSheet?>(null) }
    var draft by remember { mutableStateOf<ItemDraft?>(null) }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(exists) { if (!exists) onBack() }
    LaunchedEffect(openItem) {
        if (openItem != null) {
            sheet = EditSheet.Item(openItem)
            draft = null
            onOpenItemShown()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = nav + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderButton(TreinoIcons.Back, "Voltar", 24, onBack)
            TText(state?.workout?.name.orEmpty(), style = TreinoType.TitleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            HeaderButton(TreinoIcons.More, "Mais opções", 20) { sheet = EditSheet.Menu }
        }
        val ui = state ?: return@Column
        if (ui.rows.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.exercisesTag?.let { Tag(it) }
                ui.setsTag?.let { Tag(it, mono = true) }
                ui.durationTag?.let { Tag(it) }
            }
            Card {
                ReorderableColumn(ui.rows, key = { it.item.id }, onMove = vm::reorderItems) { row, scope ->
                    ItemRow(row, divider = scope.index > 0, handle = scope.handle) {
                        draft = null
                        sheet = EditSheet.Item(row.item.id)
                    }
                }
            }
        }
        GhostButton("Adicionar exercício", onClick = onAddExercise, icon = TreinoIcons.Plus, iconSize = 18.dp)
    }

    val ui = state ?: return
    when (val s = sheet) {
        null -> Unit
        EditSheet.Menu -> Sheet(onDismiss = { sheet = null }) {
            SheetTitle(ui.workout.name)
            MenuAction("Renomear") { sheet = EditSheet.Rename }
            MenuAction("Duplicar treino") {
                sheet = null
                vm.duplicate(onOpenWorkout)
            }
            MenuAction("Apagar treino", color = DangerColor) { sheet = EditSheet.ConfirmDelete }
        }
        EditSheet.Rename -> NameSheet(
            title = "Nome do treino",
            initial = ui.workout.name,
            onSave = {
                vm.rename(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        EditSheet.ConfirmDelete -> Sheet(onDismiss = { sheet = null }) {
            SheetTitle("Apagar “${ui.workout.name}”?")
            TText("Os dias em que ele foi feito continuam no histórico.", style = TreinoType.Body, color = TreinoColors.TextDim)
            GhostButton("Apagar treino", onClick = {
                sheet = null
                vm.delete(onBack)
            }, contentColor = DangerColor)
        }
        is EditSheet.Item -> {
            val row = ui.rows.firstOrNull { it.item.id == s.itemId }
            if (row != null) {
                val d = draft ?: ItemDraft.of(row.item, row.exercise)
                ItemSheet(
                    name = row.name,
                    chip = row.exercise?.let(::equipmentChip),
                    draft = d,
                    onChange = { draft = it },
                    onEquipment = { sheet = EditSheet.Equipment(row.item.id) },
                    onSave = {
                        vm.saveItem(row.item, d)
                        sheet = null
                    },
                    onRemove = {
                        vm.deleteItem(row.item.id)
                        sheet = null
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
        is EditSheet.Equipment -> {
            val row = ui.rows.firstOrNull { it.item.id == s.itemId }
            val ex = row?.exercise
            if (row != null && ex != null) {
                val d = draft ?: ItemDraft.of(row.item, ex)
                EquipmentSheet(
                    exercise = ex,
                    prescription = d.prescription(),
                    onSave = { updated ->
                        vm.saveEquipment(updated, row.item, d)
                        sheet = null
                    },
                    onDismiss = { sheet = EditSheet.Item(row.item.id) },
                )
            }
        }
    }
}

@Composable
private fun ItemRow(row: ItemRowUi, divider: Boolean, handle: Modifier, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(TreinoColors.Card)) {
        if (divider) RowDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(onClick = onClick)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                TText(row.name, style = TreinoType.Row.copy(fontWeight = FontWeight.W600), maxLines = 2)
                TText(row.line, style = TreinoType.mono(12.sp, color = TreinoColors.TextFaint), maxLines = 1)
            }
            GripHandle(handle)
        }
    }
}

@Composable
internal fun HeaderButton(icon: dev.charlesferreira.treino.ui.components.TreinoIcon, description: String, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint = TreinoColors.Text, size = sizeDp.dp) }
}

/** A line of the "⋮" sheet: 52 dp, 15 sp semibold. */
@Composable
private fun MenuAction(text: String, color: Color = TreinoColors.Text, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(TreinoColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { TText(text, style = TreinoType.ButtonGhost.copy(color = color), maxLines = 1) }
}
