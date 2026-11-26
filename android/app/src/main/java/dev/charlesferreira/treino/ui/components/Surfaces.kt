package dev.charlesferreira.treino.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/** `.card`: #1A1C20, radius 22, 1 dp border at 6% white. The current exercise uses [TreinoColors.AccentBorder]. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    borderColor: Color = TreinoColors.Border,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(TreinoColors.Card, shape)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * `.tag`: 24 dp high, radius 8, #24272C with #D6D2CB text. [accent] is `.tag.acc` (accent at 14%,
 * #FF9A70 text). [mono] for numbers ("17 séries").
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    icon: TreinoIcon? = null,
    mono: Boolean = false,
) {
    val fg = if (accent) TreinoColors.AccentLight else TreinoColors.TextSoft
    Row(
        modifier = modifier
            .height(24.dp)
            .background(if (accent) TreinoColors.AccentSoft else TreinoColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, tint = fg, size = 14.dp)
        TText(
            text,
            style = if (mono) TreinoType.mono(TreinoType.Tag.fontSize) else TreinoType.Tag,
            color = fg,
            maxLines = 1,
        )
    }
}

/** `.sec`: section heading over a card ("CARGAS", "DESCANSO"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    TText(text.uppercase(), modifier = modifier.padding(horizontal = 6.dp), style = TreinoType.Section, maxLines = 1)
}

/**
 * `.row` inside a card: min 52 dp, 16 dp side padding, 12 dp gap; [divider] draws the 1 dp line
 * above it (`.row+.row`). Title 15 sp medium, optional faint 12 sp subtitle, [trailing] at the end.
 */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        if (divider) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                TText(title, style = TreinoType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W500))
                if (subtitle != null) {
                    TText(subtitle, style = TreinoType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W400), color = TreinoColors.TextFaint)
                }
            }
            trailing?.invoke()
        }
    }
}
