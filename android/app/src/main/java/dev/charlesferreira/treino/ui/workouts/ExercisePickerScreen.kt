package dev.charlesferreira.treino.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.charlesferreira.treino.appContainer
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.SectionLabel
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `p-picker`. With a [workoutId] it is "Adicionar exercício": search by name and aliases, the
 * catalog by group, "Criar “…”" for a new one; a pick adds it to the workout and [onPicked] gets
 * the new item. Without, it is "Exercícios" (Todos os exercícios): each row shows its equipment
 * and opens the equipment sheet.
 */
@Composable
fun ExercisePickerScreen(workoutId: String?, onBack: () -> Unit, onPicked: (String) -> Unit) {
    val container = appContainer()
    val vm = viewModel(key = "picker-$workoutId") { ExercisePickerViewModel(container.repository, workoutId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val browse = workoutId == null
    val focus = remember { FocusRequester() }
    val bottom = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding()

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val pick: (String) -> Unit = { id ->
        // The keyboard goes away: the equipment sheet or the workout comes next.
        focusManager.clearFocus()
        if (browse) editing = id else vm.pick(id, onPicked)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderButton(TreinoIcons.Back, "Voltar", 24, onBack)
                TText(if (browse) "Exercícios" else "Adicionar exercício", style = TreinoType.TitleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        item { SearchField(query, { vm.query.value = it }, Modifier.focusRequester(focus)) }
        val ui = state
        if (ui != null) {
            ui.create?.let { name ->
                item {
                    dev.charlesferreira.treino.ui.components.Card {
                        CreateRow("Criar “$name”", subtitle = "exercício novo") { vm.create(name, pick) }
                    }
                }
            }
            ui.sections.forEach { section ->
                item(key = "sec-${section.label}") {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel(section.label, Modifier.padding(top = 8.dp))
                        dev.charlesferreira.treino.ui.components.Card {
                            section.rows.forEachIndexed { i, row -> PickerRow(row, divider = i > 0) { pick(row.id) } }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (!browse) runCatching { focus.requestFocus() } }

    val ex = editing?.let { id -> exercises.firstOrNull { it.id == id } }
    if (ex != null) {
        EquipmentSheet(
            exercise = ex,
            prescription = null,
            onSave = {
                vm.saveExercise(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** `.inp` with the search icon. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().height(48.dp),
        textStyle = TreinoType.Body,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
        cursorBrush = SolidColor(TreinoColors.Accent),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().background(TreinoColors.Surface, shape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(TreinoIcons.Search, tint = TreinoColors.TextFaint, size = 18.dp)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) TText("Buscar exercício", color = TreinoColors.TextFaint, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** `.row`: 15 sp name; the equipment or "tempo" at the end, faint. */
@Composable
private fun PickerRow(row: PickerRowUi, divider: Boolean, onClick: () -> Unit) {
    if (divider) RowDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TText(row.name, modifier = Modifier.weight(1f), maxLines = 2)
        row.trailing?.let {
            TText(it, style = TreinoType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W400, color = TreinoColors.TextFaint), maxLines = 1)
        }
    }
}
