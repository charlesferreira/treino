package dev.charlesferreira.treino.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/** Port of `initials` in `src/plan.ts`: "Charles Ferreira" → "CF", "Charles" → "CH", blank → "". */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return (parts.first().take(1) + parts.last().take(1)).uppercase()
}

/**
 * Round avatar with the initials. 40 dp in the headers (#24272C, bold 14 sp), 64 dp on the
 * Profile ([accent]: orange with ink text, 22 sp). The text scales with [size].
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (accent) TreinoColors.Accent else TreinoColors.Surface, CircleShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics { contentDescription = "Perfil" }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            initials(name),
            style = TreinoType.Body.copy(
                fontSize = (size.value * 0.35f).sp,
                lineHeight = (size.value * 0.35f * 1.2f).sp,
                fontWeight = FontWeight.W700,
            ),
            color = if (accent) TreinoColors.Ink else TreinoColors.Text,
            maxLines = 1,
        )
    }
}
