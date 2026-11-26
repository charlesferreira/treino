package dev.charlesferreira.treino.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.timer.RestMath
import dev.charlesferreira.treino.timer.RestState
import dev.charlesferreira.treino.ui.components.Icon
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.FloatingOverlay
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType

/** How long the in-app "Hora da série" stays after the rest ended, like the heads-up's timeout. */
const val REST_DONE_VISIBLE_MS = 10 * 60_000L

/** What the rest layer of Hoje shows for [rest] at [nowMs]. */
enum class RestView { NONE, SHEET, PILL, DONE }

fun restView(rest: RestState?, expanded: Boolean, nowMs: Long): RestView = when {
    rest == null -> RestView.NONE
    rest.finished -> if (nowMs - rest.endsAtMs < REST_DONE_VISIBLE_MS) RestView.DONE else RestView.NONE
    expanded -> RestView.SHEET
    else -> RestView.PILL
}

/** The space the pill takes above the tab bar, for the content to scroll clear of it. */
val RestPillClearance = 72.dp

interface RestActions {
    fun onAdjust(deltaMs: Long)
    fun onSkip()
    fun onMinimize()
    fun onExpand()
    fun onDoneSeen()
}

/**
 * The rest of Hoje, drawn by the root above the tab bar: the expanded sheet (`b-rest`), the pill
 * (`b-rest-pill`) or the end (`b-rest-done`). Everything is read from [rest] (the timer's store);
 * the clock only redraws.
 */
@Composable
fun RestLayer(view: RestView, rest: RestState?, actions: RestActions) {
    if (rest == null || view == RestView.NONE) return
    FloatingOverlay {
        when (view) {
            RestView.SHEET -> RestSheet(rest, actions)
            RestView.PILL -> RestPill(rest, actions)
            RestView.DONE -> RestDonePill(rest, actions)
            RestView.NONE -> Unit
        }
    }
}

/** Now, refreshed every frame while [running]: for drawing only. */
@Composable
private fun frameNow(running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        if (running) {
            while (true) {
                withFrameMillis { }
                now.longValue = System.currentTimeMillis()
            }
        }
    }
    return now
}

/** Arc of the remaining time, from the top, clockwise, round caps (the SVG rotated −90°). */
private fun DrawScope.ring(radius: Float, stroke: Float, track: Color, fraction: Float) {
    drawCircle(track, radius = radius, style = Stroke(stroke))
    if (fraction <= 0f) return
    drawArc(
        color = TreinoColors.Accent,
        startAngle = -90f,
        sweepAngle = 360f * fraction,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
}

/* -------------------------------------------------------------- b-rest */

@Composable
private fun RestSheet(rest: RestState, actions: RestActions) {
    BackHandler(onBack = actions::onMinimize)
    val now = frameNow(running = true)
    val remaining by remember(rest) { derivedStateOf { RestMath.format(rest.remainingMs(now.value)) } }
    val total = RestMath.format(rest.totalMs)
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxSize()) {
        // The design fades the screen to 30%: the background at 70% over it, tab bar included.
        Box(
            Modifier
                .fillMaxSize()
                .background(TreinoColors.Background.copy(alpha = 0.7f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions::onMinimize),
        )
        Column(
            Modifier
                .fillMaxSize()
                // `top: 150px` with the design's 54 px status bar.
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = status + 96.dp)
                .dropShadow(shape, Shadow(radius = 50.dp, color = Color.Black.copy(alpha = 0.6f), offset = DpOffset(0.dp, (-10).dp)))
                .clip(shape)
                .background(TreinoColors.Card, shape)
                .border(1.dp, TreinoColors.BorderStrong, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .semantics { contentDescription = "Descanso" }
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = maxOf(24.dp, nav + 8.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(width = 40.dp, height = 5.dp).background(TreinoColors.SurfaceHigh, RoundedCornerShape(3.dp)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TText("Descanso", style = TreinoType.Label, color = TreinoColors.AccentLight, maxLines = 1)
                    TText(rest.exerciseName, style = TreinoType.Body.copy(fontWeight = FontWeight.W600), maxLines = 1)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TreinoColors.Surface, CircleShape)
                        .clickable(role = Role.Button, onClick = actions::onMinimize)
                        .semantics { contentDescription = "Minimizar" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(TreinoIcons.ChevronDown, tint = TreinoColors.Text)
                }
            }
            Box(
                Modifier.size(250.dp).semantics { contentDescription = "$remaining restantes de $total" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(250.dp)) {
                    ring(radius = 110.dp.toPx(), stroke = 14.dp.toPx(), track = TreinoColors.SurfaceLow, fraction = rest.fraction(now.value))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TText(remaining, style = TreinoType.mono(68.sp, FontWeight.W700).copy(lineHeight = 68.sp), maxLines = 1)
                    TText(
                        "de $total",
                        modifier = Modifier.padding(top = 6.dp),
                        style = TreinoType.mono(14.sp, color = TreinoColors.TextFaint),
                        maxLines = 1,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustButton("−15s", filled = true) { actions.onAdjust(-15_000) }
                AdjustButton("+15s", filled = true) { actions.onAdjust(15_000) }
                AdjustButton("Pular", filled = false, onClick = actions::onSkip)
            }
            Spacer(Modifier.weight(1f))
            NextPanel(rest)
        }
    }
}

/** `.adj`: 52 dp pill, #24272C, 15 sp bold; Pular is only a 1.5 dp ring. */
@Composable
private fun RowScope.AdjustButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(TreinoColors.Surface, shape) else Modifier.border(1.5.dp, TreinoColors.SurfaceHigh, shape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TText(text, style = TreinoType.Body.copy(fontWeight = FontWeight.W700), maxLines = 1)
    }
}

/** The set just logged and the next one, on the background color, radius 20. */
@Composable
private fun NextPanel(rest: RestState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TreinoColors.Background, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rest.loggedLabel.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp).background(TreinoColors.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(TreinoIcons.Check, tint = TreinoColors.Ink, size = 12.dp, strokeWidth = 3f)
                }
                TText(rest.loggedLabel, Modifier.weight(1f), style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = TreinoColors.TextDim, maxLines = 1)
                TText(rest.loggedValue, style = TreinoType.mono(14.sp), maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(TreinoColors.Border))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(20.dp).border(2.dp, TreinoColors.Accent, CircleShape))
            TText(
                "Próxima, série ${rest.setNumber}",
                Modifier.weight(1f),
                style = TreinoType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600),
                maxLines = 1,
            )
            TText(rest.nextShort, style = TreinoType.mono(14.sp), maxLines = 1)
        }
    }
}

/* --------------------------------------------------------- b-rest-pill */

/** Bottom of the pill: 12 dp above the tab bar (the design's 98 = 24 + 62 + 12). */
@Composable
private fun pillBottom(): Dp = TabBarDefaults.bottomOffset() + TabBarDefaults.Height + 12.dp

@Composable
private fun RestPill(rest: RestState, actions: RestActions) {
    val now = frameNow(running = true)
    val remaining by remember(rest) { derivedStateOf { RestMath.format(rest.remainingMs(now.value)) } }
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = pillBottom())
                .fillMaxWidth()
                .height(60.dp)
                .dropShadow(shape, Shadow(radius = 36.dp, color = Color.Black.copy(alpha = 0.55f), offset = DpOffset(0.dp, 12.dp)))
                .clip(shape)
                .background(TreinoColors.Surface, shape)
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .clickable(role = Role.Button, onClick = actions::onExpand)
                .semantics { contentDescription = "Descanso: $remaining restantes. Toque para expandir" }
                .padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(Modifier.size(40.dp)) {
                ring(radius = 16.dp.toPx(), stroke = 4.dp.toPx(), track = TreinoColors.SurfaceHigh, fraction = rest.fraction(now.value))
            }
            TText(remaining, style = TreinoType.mono(24.sp, FontWeight.W700).copy(lineHeight = 28.sp), maxLines = 1)
            Column(Modifier.weight(1f)) {
                TText(
                    "Próxima",
                    style = TreinoType.Body.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.W600),
                    color = TreinoColors.TextFaint,
                    maxLines = 1,
                )
                TText(rest.nextShort, style = TreinoType.mono(13.sp).copy(lineHeight = 15.6.sp), maxLines = 1)
            }
            val chip = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .height(44.dp)
                    .clip(chip)
                    .background(Color(0xFF33363D), chip)
                    .clickable(role = Role.Button) { actions.onAdjust(15_000) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                TText("+15s", style = TreinoType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.W700), maxLines = 1)
            }
        }
    }
}

/* --------------------------------------------------------- b-rest-done */

@Composable
private fun RestDonePill(rest: RestState, actions: RestActions) {
    val bottom = pillBottom()
    val shape = RoundedCornerShape(30.dp)
    val title = "Hora da série ${rest.setNumber}"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // The glow: 8 dp around the pill, accent at 18%.
        Box(
            Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = bottom - 8.dp)
                .fillMaxWidth()
                .height(76.dp)
                .background(TreinoColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(38.dp)),
        )
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = bottom)
                .fillMaxWidth()
                .height(60.dp)
                .dropShadow(shape, Shadow(radius = 36.dp, color = TreinoColors.Accent.copy(alpha = 0.3f), offset = DpOffset(0.dp, 12.dp)))
                .clip(shape)
                .background(TreinoColors.Accent, shape)
                .clickable(role = Role.Button, onClick = actions::onDoneSeen)
                .semantics { contentDescription = "Descanso acabou. Série ${rest.setNumber} de ${rest.setCount}" }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).background(TreinoColors.Ink.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(TreinoIcons.Machine, tint = TreinoColors.Ink, size = 22.dp, strokeWidth = 2.2f)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                TText(title, style = TreinoType.Button.copy(lineHeight = 19.sp), maxLines = 1)
                TText(
                    rest.nextShort,
                    style = TreinoType.mono(12.sp, color = TreinoColors.Ink.copy(alpha = 0.75f)).copy(lineHeight = 14.4.sp),
                    maxLines = 1,
                )
            }
            val chip = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .height(44.dp)
                    .clip(chip)
                    .background(TreinoColors.Ink, chip)
                    .clickable(role = Role.Button, onClick = actions::onDoneSeen)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                TText("Abrir", style = TreinoType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.W700), color = TreinoColors.AccentLight, maxLines = 1)
            }
        }
    }
}
