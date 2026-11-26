package dev.charlesferreira.treino.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * `.stp`: pill stepper, 52 dp, #24272C, the two 52 dp buttons in bold 14 sp #D6D2CB and the value
 * in mono 15 sp. [minusLabel]/[plusLabel] are the visible texts ("−2", "+1"); the descriptions
 * are for TalkBack ("Menos 2 kg").
 */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusLabel: String = "−1",
    plusLabel: String = "+1",
    minusDescription: String? = null,
    plusDescription: String? = null,
    minusEnabled: Boolean = true,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier.height(52.dp).clip(shape).background(TreinoColors.Surface, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(minusLabel, minusDescription, minusEnabled, onMinus)
        TText(
            value,
            modifier = Modifier.weight(1f),
            style = TreinoType.mono(valueFontSize),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepperButton(plusLabel, plusDescription, true, onPlus)
    }
}

@Composable
private fun StepperButton(label: String, description: String?, enabled: Boolean, onClick: () -> Unit) {
    // At least 52 dp square; a longer label ("−1 placa") widens it into a pill.
    Box(
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = 52.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (label.length > 3) 12.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            label,
            style = TreinoType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.W700),
            color = if (enabled) TreinoColors.TextSoft else TreinoColors.TextFaint,
            maxLines = 1,
        )
    }
}

/**
 * `.cell`: 40 dp high, radius 10, #24272C, mono 17 sp. [highlighted] draws the 2 dp accent inset ring
 * of the field being edited. Width comes from the caller (72 for kg, 60 for reps in `.tbl`).
 */
@Composable
fun Cell(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(TreinoColors.Surface, shape)
            .then(if (highlighted) Modifier.border(2.dp, TreinoColors.Accent, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        TText(text, style = TreinoType.mono(17.sp), maxLines = 1)
    }
}

/** `.tg`: 46×28 switch, knob 22, accent when on. */
@Composable
fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) TreinoColors.Accent else TreinoColors.SurfaceHigh, label = "track")
    val knob by animateColorAsState(if (checked) TreinoColors.Ink else TreinoColors.TextSoft, label = "knob")
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, label = "x")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .width(46.dp)
            .height(28.dp)
            .clip(shape)
            .background(track, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 3.dp)
                .size(22.dp)
                .background(knob, CircleShape),
        )
    }
}


/** `.inp`: 48 dp (54 on onboarding), radius 14, #24272C, 15 sp text, accent cursor. */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    height: androidx.compose.ui.unit.Dp = 48.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    val shape = RoundedCornerShape(14.dp)
    val style = TreinoType.Body.copy(fontSize = fontSize, lineHeight = fontSize * 1.3f)
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(height),
        textStyle = style,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(TreinoColors.Accent),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().background(TreinoColors.Surface, shape).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) TText(placeholder, style = style, color = TreinoColors.TextFaint, maxLines = 1)
                inner()
            }
        },
    )
}
