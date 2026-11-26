package dev.charlesferreira.treino.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

/** The text style [dev.charlesferreira.treino.ui.components.TText] falls back to. */
val LocalTextStyle = staticCompositionLocalOf { TreinoType.Body }

/** Direction B. No Material theme: the components draw the design directly. */
@Composable
fun TreinoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextStyle provides TreinoType.Body,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = TreinoColors.Accent,
            backgroundColor = TreinoColors.AccentSoft,
        ),
        content = content,
    )
}

@Composable
fun ProvideTextStyle(style: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides style, content = content)
}
