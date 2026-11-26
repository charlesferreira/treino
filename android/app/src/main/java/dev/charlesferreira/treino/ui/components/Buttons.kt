package dev.charlesferreira.treino.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/** `.pbtn`: orange pill, 54 dp, bold ink text, optional icon at stroke 2.6. Full width by default. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: TreinoIcon? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = TreinoType.Button.fontSize,
    iconSize: Dp? = null,
    iconStroke: Float = 2.6f,
) {
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        iconStroke = iconStroke,
        enabled = enabled,
        height = height,
        background = TreinoColors.Accent,
        style = TreinoType.Button.copy(fontSize = fontSize),
        iconSize = iconSize,
    )
}

/** `.gbtn`: grey pill, 50 dp, semibold text ("Nota do dia", "Já tenho um backup"). */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: TreinoIcon? = null,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = TreinoType.ButtonGhost.fontSize,
    iconSize: Dp? = null,
    contentColor: androidx.compose.ui.graphics.Color = TreinoType.ButtonGhost.color,
) {
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        iconStroke = 2f,
        enabled = enabled,
        height = height,
        background = TreinoColors.Surface,
        style = TreinoType.ButtonGhost.copy(fontSize = fontSize, color = contentColor),
        iconSize = iconSize,
    )
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: TreinoIcon?,
    iconStroke: Float,
    enabled: Boolean,
    height: Dp,
    background: androidx.compose.ui.graphics.Color,
    style: TextStyle,
    iconSize: Dp? = null,
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(background, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, tint = style.color, size = iconSize ?: if (height >= 54.dp) 20.dp else 18.dp, strokeWidth = iconStroke)
        TText(text, style = style, maxLines = 1)
    }
}
