package dev.charlesferreira.treino.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcon
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

enum class Tab(val label: String, val icon: TreinoIcon) {
    TODAY("Hoje", TreinoIcons.Dumbbell),
    EVOLUTION("Evolução", TreinoIcons.Chart),
    WORKOUTS("Treinos", TreinoIcons.List),
}

object TabBarDefaults {
    val Height = 62.dp
    val Side = 20.dp

    /** The design's bottom gap; it grows to clear a tall navigation bar (3 buttons). */
    val Bottom = 24.dp

    /** What a tab screen leaves free at the bottom (the design's primary button sits at 104). */
    val ContentClearance = 104.dp

    @Composable
    fun bottomOffset(): Dp {
        val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return maxOf(Bottom, nav + 8.dp)
    }
}

/**
 * `.tabbar`: floating pill 20 dp from the sides and 24 from the bottom, 62 high, radius 31, #1F2125,
 * 1 dp border at 8% white, shadow `0 10px 30px rgba(0,0,0,.5)`. Place it at the bottom of a Box.
 */
@Composable
fun TabBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(31.dp)
    Row(
        modifier = modifier
            .padding(start = TabBarDefaults.Side, end = TabBarDefaults.Side, bottom = TabBarDefaults.bottomOffset())
            .fillMaxWidth()
            .height(TabBarDefaults.Height)
            .dropShadow(shape, Shadow(radius = 30.dp, color = Color.Black.copy(alpha = 0.5f), offset = DpOffset(0.dp, 10.dp)))
            .clip(shape)
            .background(TreinoColors.SurfaceBar, shape)
            .border(1.dp, TreinoColors.BorderStrong, shape)
            .padding(horizontal = 8.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            val color = if (on) TreinoColors.Accent else TreinoColors.TextFaint
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .semantics { this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Icon(tab.icon, tint = color)
                TText(tab.label, style = TreinoType.Tab, color = color, maxLines = 1)
            }
        }
    }
}
