package dev.charlesferreira.treino.ui.today

import androidx.compose.foundation.background
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.WorkoutProgress
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.SegmentedProgressBar
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.components.barSegments
import dev.charlesferreira.treino.ui.components.initials
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/**
 * The workout header (`b-workout` at rest, `b-header-scroll` while scrolling), drawn from
 * [HeaderValues]. It sits over the scrolling content; [statusOffset] is the real status bar
 * inset minus [HeaderScroll.DESIGN_STATUS_DP], added to every top value.
 */
@Composable
fun WorkoutHeader(
    v: HeaderValues,
    /** Frosted glass: the list scrolling under the collapsed header is blurred behind it. */
    haze: HazeState? = null,
    statusBar: Dp,
    statusOffset: Dp,
    dateLabel: String,
    workoutName: String,
    clock: String,
    progress: WorkoutProgress,
    profileName: String,
    onSwitch: () -> Unit,
    onProfile: () -> Unit,
    /** Workout mode: the avatar's place holds ⋮ (the day's options) and [onProfile] opens them. */
    menu: Boolean = false,
) {
    val count = countText(progress)
    // The rows sit at the bottom (justify-content: flex-end). Collapsed, the design's rows are
    // taller than the room inside its padding and CSS lets them overflow 10 px above it, into the
    // iPhone's empty status area; here the header grows instead, so they stay under the status bar.
    val minTop = maxOf(statusOffset + v.paddingTop.dp, statusBar)
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (haze == null) {
                    Modifier.background(TreinoColors.Background.copy(alpha = v.backgroundAlpha))
                } else {
                    // Blur at full strength once collapsed, with a lighter wash than the plain fill.
                    val strength = (v.backgroundAlpha / 0.94f).coerceIn(0f, 1f)
                    Modifier.hazeBlur(
                        HazeInput.Sources(haze),
                        HazeBlurStyle {
                            blurRadius(24.dp)
                            backgroundColor(TreinoColors.Background)
                            colorEffects(listOf(HazeColorEffect.tint(TreinoColors.Background.copy(alpha = 0.72f))))
                            alpha(strength)
                        },
                    )
                },
            )
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Color.White.copy(alpha = v.borderAlpha), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(v.gap.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.height(v.eyebrowHeight.dp).clipToBounds()) {
                            TText(
                                dateLabel,
                                style = TreinoType.Label,
                                maxLines = 1,
                                modifier = Modifier.alpha(v.eyebrowAlpha).wrapContentHeight(Alignment.Top, unbounded = true),
                            )
                        }
                        Row(
                            Modifier
                                .clickable(role = Role.Button, onClick = onSwitch)
                                .semantics { contentDescription = "Trocar treino" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TText(
                                workoutName,
                                style = TreinoType.Title.copy(fontSize = v.titleSize.sp, lineHeight = (v.titleSize * 1.15f).sp),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                            )
                            // b-workout's chevron (gap 4); it leaves with the eyebrow.
                            Box(Modifier.width((24f * v.extrasAlpha).dp).clipToBounds()) {
                                Icon(
                                    TreinoIcons.ChevronDown,
                                    tint = TreinoColors.TextFaint,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .alpha(v.extrasAlpha)
                                        .wrapContentWidth(Alignment.Start, unbounded = true),
                                )
                            }
                            Box(Modifier.width(v.titleGap.dp))
                            Box(
                                Modifier
                                    .widthIn(max = v.inlineCountMaxWidth.dp)
                                    .clipToBounds()
                                    .alpha(v.inlineCountAlpha)
                                    .alignByBaseline(),
                            ) {
                                TText(count, style = TreinoType.mono(15.sp), maxLines = 1, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                            }
                        }
                    }
                    Timer(v, clock)
                    Box(
                        Modifier
                            .size(v.avatarSize.dp)
                            .alpha(v.avatarAlpha)
                            .clip(CircleShape)
                            .background(TreinoColors.Surface, CircleShape)
                            .clickable(role = Role.Button, onClick = onProfile)
                            .semantics { contentDescription = if (menu) "Mais opções" else "Perfil" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (menu) {
                            Icon(TreinoIcons.More, tint = TreinoColors.Text, size = 20.dp)
                        } else {
                            TText(
                                initials(profileName),
                                style = TreinoType.Body.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.W700),
                                maxLines = 1,
                                modifier = Modifier.wrapContentWidth(unbounded = true).wrapContentHeight(unbounded = true),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(v.rowGap.dp)) {
                    SegmentedProgressBar(
                        progress.barSegments(),
                        modifier = Modifier.weight(1f),
                        height = v.barHeight.dp,
                        gap = v.segmentGap.dp,
                    )
                    Box(Modifier.widthIn(max = v.sideCountMaxWidth.dp).clipToBounds().alpha(v.sideCountAlpha)) {
                        TText(count, style = TreinoType.mono(15.sp), maxLines = 1, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val side = HeaderScroll.PADDING_SIDE_DP.dp.roundToPx()
        val bottom = HeaderScroll.PADDING_BOTTOM_DP.dp.roundToPx()
        val width = constraints.maxWidth
        val rows = measurables.first().measure(Constraints.fixedWidth((width - 2 * side).coerceAtLeast(0)))
        val height = maxOf((statusOffset + v.height.dp).roundToPx(), minTop.roundToPx() + rows.height + bottom)
        layout(width, height) { rows.place(side, height - bottom - rows.height) }
    }
}

/** The workout clock pill: 40 → 24 high, its background fades and the text turns faint past 50%. */
@Composable
private fun Timer(v: HeaderValues, clock: String) {
    Row(
        Modifier
            .height(v.timerHeight.dp)
            .background(TreinoColors.Card.copy(alpha = v.timerBackgroundAlpha), RoundedCornerShape(20.dp))
            .padding(horizontal = v.timerPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // b-workout's clock icon (16 dp, gap 6); it leaves with the eyebrow.
        Box(Modifier.width((22f * v.extrasAlpha).dp).clipToBounds()) {
            Icon(
                TreinoIcons.Clock,
                tint = TreinoColors.TextDim,
                size = 16.dp,
                modifier = Modifier.alpha(v.extrasAlpha).wrapContentWidth(Alignment.Start, unbounded = true),
            )
        }
        TText(
            clock,
            style = TreinoType.mono(v.timerFontSize.sp),
            color = if (v.timerFaint) TreinoColors.TextFaint else TreinoColors.Text,
            maxLines = 1,
        )
    }
}

/** "9/16" with "/16" faint. */
fun countText(progress: WorkoutProgress): AnnotatedString = buildAnnotatedString {
    append("${progress.done}")
    withStyle(SpanStyle(color = TreinoColors.TextFaint)) { append("/${progress.total}") }
}
