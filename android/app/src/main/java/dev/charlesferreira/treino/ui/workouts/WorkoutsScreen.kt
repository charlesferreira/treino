package dev.charlesferreira.treino.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.today.Sheet
import dev.charlesferreira.treino.ui.today.SheetTitle

/** The grip and the "more" dots: #5A5D64 in the design. */
internal val GripColor = Color(0xFF5A5D64)

/**
 * `p-workouts`: the rotation (number, name, "próximo", what it has), reordered by the grip; "+"
 * creates a workout; "Todos os exercícios" opens the exercise list.
 */
@Composable
fun WorkoutsScreen(onProfile: () -> Unit, onWorkout: (String) -> Unit, onExercises: () -> Unit) {
    val container = appContainer()
    val vm = viewModel { WorkoutsViewModel(container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = TabBarDefaults.contentClearance()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TText("Treinos", style = TreinoType.Title, modifier = Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TreinoColors.Surface, CircleShape)
                    .clickable(role = Role.Button) { creating = true }
                    .semantics { contentDescription = "Novo treino" },
                contentAlignment = Alignment.Center,
            ) { Icon(TreinoIcons.Plus, tint = TreinoColors.Text) }
            Avatar(state?.profileName.orEmpty(), modifier = Modifier.padding(start = 8.dp), onClick = onProfile)
        }
        val ui = state ?: return@Column
        SectionLabel("Sequência")
        Card {
            if (ui.rows.isEmpty()) {
                CreateRow("Novo treino", subtitle = null) { creating = true }
            } else {
                ReorderableColumn(ui.rows, key = { it.id }, onMove = vm::reorder) { row, scope ->
                    SequenceRow(row, divider = scope.index > 0, handle = scope.handle, onClick = { onWorkout(row.id) })
                }
            }
        }
        SectionLabel("Exercícios")
        Card {
            CardRow(
                "Todos os exercícios",
                subtitle = "${ui.exerciseCount}, com equipamento de cada um",
                onClick = onExercises,
                trailing = { Icon(TreinoIcons.ChevronRight, tint = TreinoColors.TextFaint, size = 18.dp) },
            )
        }
    }

    if (creating) {
        NameSheet(
            title = "Novo treino",
            initial = "",
            onSave = { name ->
                creating = false
                vm.create(name, onWorkout)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SequenceRow(row: SequenceRowUi, divider: Boolean, handle: Modifier, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(TreinoColors.Card)) {
        if (divider) RowDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(onClick = onClick)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TText(
                row.number.toString(),
                style = TreinoType.mono(15.sp, color = TreinoColors.TextFaint),
                modifier = Modifier.width(16.dp),
                maxLines = 1,
            )
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TText(
                        row.name,
                        style = TreinoType.Body.copy(fontWeight = FontWeight.W600),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.upcoming) Tag("próximo", accent = true)
                }
                TText(row.summary, style = TreinoType.Label.copy(fontWeight = FontWeight.W400, color = TreinoColors.TextFaint), maxLines = 1)
            }
            GripHandle(handle)
        }
    }
}

/** The 20 dp grip of the design, inside a 48 dp touch target that ends 16 dp from the edge. */
@Composable
internal fun GripHandle(handle: Modifier) {
    Box(
        handle.padding(end = 2.dp).size(width = 48.dp, height = 52.dp).semantics { contentDescription = "Arrastar para reordenar" },
        contentAlignment = Alignment.Center,
    ) { Icon(TreinoIcons.Grip, tint = GripColor) }
}

@Composable
internal fun RowDivider() {
    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(TreinoColors.Border))
}

/** The accent "+" row (`p-picker`'s "Criar"). */
@Composable
internal fun CreateRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(TreinoIcons.Plus, tint = TreinoColors.Accent)
        Column(Modifier.weight(1f)) {
            TText(title, style = TreinoType.Body.copy(fontWeight = FontWeight.W600), maxLines = 1)
            if (subtitle != null) TText(subtitle, style = TreinoType.Label.copy(fontWeight = FontWeight.W400, color = TreinoColors.TextFaint))
        }
    }
}

/** A one line name ("Novo treino", "Renomear"), like the PWA's `openTextSheet`. */
@Composable
internal fun NameSheet(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    Sheet(onDismiss) {
        SheetTitle(title)
        TextInput(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
        PrimaryButton("Salvar", onClick = { onSave(text) }, enabled = text.isNotBlank())
        // Inside the sheet: it is composed by the root, after this screen.
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
