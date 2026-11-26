package dev.charlesferreira.treino.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.nav.Overlay
import dev.charlesferreira.treino.ui.nav.overlayActive
import dev.charlesferreira.treino.ui.nav.scrimMotion
import dev.charlesferreira.treino.ui.nav.sheetMotion
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `.scrim` + `.sheet` of the design: scrim at 55% black over everything (tab bar included), the
 * sheet at the bottom with radius 28 on top, #1A1C20, 1 dp top border at 8% white, padding
 * 10/18/34, 14 dp between children, the 40×5 grab first. Tapping the scrim or back closes it.
 */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Overlay {
        BackHandler(enabled = overlayActive(), onBack = onDismiss)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .scrimMotion()
                    .background(TreinoColors.Scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            // Above the keyboard when it is open, else above the navigation bar.
            val inset = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding()
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .sheetMotion()
                    .clip(shape)
                    .background(TreinoColors.Card, shape)
                    .drawBehind {
                        drawLine(TreinoColors.BorderStrong, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx())
                    }
                    // Swallow taps so they don't reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = maxOf(34.dp, inset + 16.dp)),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .background(TreinoColors.SurfaceHigh, RoundedCornerShape(3.dp)),
                )
                content()
            }
        }
    }
}

/** The 18 sp bold title of a sheet. */
@Composable
fun SheetTitle(text: String) {
    TText(text, style = TreinoType.TitleSmall)
}

/** "Nota do dia": the day's note. Not in the design; built from its sheet, input and button. */
@Composable
fun NoteSheet(note: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(note.orEmpty()) }
    Sheet(onDismiss) {
        SheetTitle("Nota do dia")
        val shape = RoundedCornerShape(14.dp)
        val style = TreinoType.Body.copy(fontSize = 15.sp, lineHeight = 21.sp)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            textStyle = style,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(TreinoColors.Accent),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 96.dp).background(TreinoColors.Surface, shape).padding(14.dp),
                ) { inner() }
            },
        )
        PrimaryButton("Salvar", onClick = { onSave(text) })
    }
}

/** The 22 dp accent circle with a check (`.done`). */
@Composable
fun DoneMark(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).background(TreinoColors.Accent, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        dev.charlesferreira.treino.ui.components.Icon(
            dev.charlesferreira.treino.ui.components.TreinoIcons.Check,
            tint = TreinoColors.Ink,
            size = 13.dp,
            strokeWidth = 3f,
        )
    }
}
