package dev.charlesferreira.treino.ui.today

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.abs
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.ui.components.Card
import dev.charlesferreira.treino.ui.components.Cell
import dev.charlesferreira.treino.ui.components.GhostButton
import dev.charlesferreira.treino.ui.components.PrimaryButton
import dev.charlesferreira.treino.ui.components.Stepper
import dev.charlesferreira.treino.ui.components.TText
import dev.charlesferreira.treino.ui.components.Tag
import dev.charlesferreira.treino.ui.components.TreinoIcons
import dev.charlesferreira.treino.ui.nav.TabBarDefaults
import dev.charlesferreira.treino.ui.theme.TreinoColors
import dev.charlesferreira.treino.ui.theme.TreinoType
import dev.charlesferreira.treino.ui.workouts.DangerColor

/** What the workout screen does; the ViewModel implements it. */
interface WorkoutActions {
    fun onSwitch()
    fun onProfile()
    /** ⋮ of the workout under way: note, switch, add an exercise, end, delete the day. */
    fun onMenu()
    fun onSelect(item: ItemUi)
    fun onStepWeight(item: ItemUi, up: Boolean)
    fun onStepReps(item: ItemUi, up: Boolean)
    fun onTypeWeight(item: ItemUi, kg: Double)
    fun onTypeReps(item: ItemUi, reps: Int)
    fun onLogSet(item: ItemUi, draft: SetDraft)
    fun onEditSet(set: SetEntry)
    fun onEditTime(item: ItemUi, draft: (TimeDraft) -> TimeDraft)
    fun onLogTime(item: ItemUi, draft: TimeDraft)
    fun onEditLoggedTime(id: String)
    /** "Terminar treino", or "Ver resumo" once it ended. */
    fun onFinish()
    /** A tap on the open exercise's name: its last sessions, without leaving the workout. */
    fun onHistory(item: ItemUi)
}

/**
 * `b-workout`: the workout in progress. The header ([WorkoutHeader]) collapses as the content
 * scrolls; below it the exercises done, the current one (sets table, steppers, "Registrar série N")
 * and the ones still to do, then "Terminar treino".
 */
@Composable
fun WorkoutScreen(
    ui: TodayUi,
    clock: String,
    setDraft: (ItemUi) -> SetDraft,
    timeDraft: (ItemUi) -> TimeDraft,
    actions: WorkoutActions,
    bottomExtra: Dp = 0.dp,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val p by remember { derivedStateOf { HeaderScroll.progress(with(density) { scroll.value.toDp().value }) } }
    val v = HeaderScroll.values(p)
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusOffset = status - HeaderScroll.DESIGN_STATUS_DP.dp
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val anchor = remember { ItemAnchor() }
    val clearance = maxOf(TabBarDefaults.contentClearance() + bottomExtra, ime + 12.dp)

    val haze = rememberHazeState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                // What passes under the collapsed header shows through it, blurred.
                .hazeSource(haze)
                .verticalScroll(scroll)
                // A short workout still scrolls the whole collapse distance, so the header can
                // collapse; the room left over stays under "Terminar treino".
                .heightIn(min = maxHeight + HeaderScroll.COLLAPSE_DP.dp)
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = statusOffset + HeaderScroll.CONTENT_TOP_DP.dp,
                    bottom = clearance,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = ui.current
            // The workout's order never changes. Each exercise is one element that turns from a
            // compact row into its card and back, so the one closing and the one opening move at
            // the same time; neighbouring rows join into one card. After the end nothing opens.
            val openId = if (ui.ended) null else current?.item?.id
            Column {
                ui.items.forEachIndexed { i, item ->
                    key(item.item.id) {
                        val open = item.item.id == openId
                        val prevClosed = i > 0 && ui.items[i - 1].item.id != openId
                        val nextClosed = i < ui.items.lastIndex && ui.items[i + 1].item.id != openId
                        WorkoutItem(
                            open = open,
                            first = i == 0,
                            joinedAbove = !open && prevClosed,
                            joinedBelow = !open && nextClosed,
                            track = { top, height -> anchor.place(item.item.id, top, height) },
                            row = {
                                CompactRowBox(enabled = !ui.ended, onClick = { actions.onSelect(item) }) { CompactRow(item) }
                            },
                        ) {
                            if (item.isTime) {
                                TimeExerciseCard(item, timeDraft(item), actions)
                            } else {
                                CurrentExerciseCard(item, setDraft(item), ui.session?.id, actions)
                            }
                        }
                    }
                }
            }
            KeepOpenInView(openId, anchor, scroll, viewport = viewport, top = status + HEADER_CLEARANCE, bottom = clearance)
            // Not in the design: one action at the end of the list. Deleting the day and starting
            // another live in the ⋮ and in the summary, away from the path of a set.
            when {
                ui.ended -> PrimaryButton("Ver resumo", onClick = actions::onFinish)
                current == null -> PrimaryButton("Terminar treino", onClick = actions::onFinish, icon = TreinoIcons.Check)
                else -> GhostButton("Terminar treino", onClick = actions::onFinish)
            }
        }
        WorkoutHeader(
            v = v,
            haze = haze,
            statusBar = status,
            statusOffset = statusOffset,
            dateLabel = ui.dateLabel,
            workoutName = ui.workout?.name.orEmpty(),
            clock = clock,
            progress = ui.progress,
            profileName = ui.profileName,
            onSwitch = actions::onSwitch,
            onProfile = if (ui.ended) actions::onProfile else actions::onMenu,
            menu = !ui.ended,
        )
    }
}

/** A closed exercise: the check and the logged sets when done; else its number and target or "1/3". */
@Composable
private fun RowScope.CompactRow(item: ItemUi) {
    if (item.complete) {
        DoneMark()
        RowName(item)
        TText(
            loggedSummary(item.sets, item.times).orEmpty(),
            style = TreinoType.mono(13.sp, color = TreinoColors.TextDim),
            maxLines = 1,
        )
    } else {
        ProgressMark(item.done, item.total)
        RowName(item)
        TText(item.target, style = TreinoType.mono(12.sp, color = TreinoColors.TextFaint), maxLines = 1)
    }
}

/**
 * An exercise not done yet: an empty circle that fills clockwise from the top with each set, like
 * a clock (2 of 3 is two thirds). When done it becomes the [DoneMark].
 */
@Composable
private fun ProgressMark(done: Int, total: Int) {
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(fraction * 360f, tween(OPEN_MS), label = "progressMark")
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "$done de $total séries" }) {
        val stroke = 1.5.dp.toPx()
        drawCircle(TreinoColors.TextFaint, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        if (sweep > 0f) {
            val inset = stroke + 1.5.dp.toPx()
            drawArc(
                TreinoColors.Accent,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
            )
        }
    }
}

/** Where each exercise sat at the last layout (top in the window and height, in px). */
internal class ItemAnchor {
    private val tops = HashMap<String, Float>()
    private val heights = HashMap<String, Int>()
    fun place(id: String, top: Float, height: Int) {
        tops[id] = top
        heights[id] = height
    }
    fun top(id: String): Float? = tops[id]
    fun height(id: String): Int? = heights[id]
}

/**
 * When the open exercise changes, the one tapped (or the next after the last set) stays where it
 * was on screen while the cards resize, then the list scrolls only if its card does not fit
 * between the collapsed header ([top]) and the bottom clearance.
 */
@Composable
private fun KeepOpenInView(openId: String?, anchor: ItemAnchor, scroll: ScrollState, viewport: Dp, top: Dp, bottom: Dp) {
    // Read during composition, so it is the place from the layout before the change.
    val from = remember(openId) { openId?.let(anchor::top) }
    val density = LocalDensity.current
    LaunchedEffect(openId) {
        val id = openId ?: return@LaunchedEffect
        val start = from ?: return@LaunchedEffect
        val began = System.nanoTime()
        while (System.nanoTime() - began < (OPEN_MS + 40) * 1_000_000L) {
            withFrameNanos { }
            val now = anchor.top(id) ?: break
            val drift = now - start
            if (abs(drift) > 0.5f) scroll.scrollBy(drift)
        }
        val y = anchor.top(id) ?: return@LaunchedEffect
        val h = anchor.height(id) ?: return@LaunchedEffect
        with(density) {
            val minTop = top.toPx()
            val maxBottom = (viewport - bottom).toPx()
            val by = when {
                y < minTop -> y - minTop
                y + h > maxBottom -> minOf(y + h - maxBottom, y - minTop)
                else -> 0f
            }
            if (by != 0f) scroll.animateScrollBy(by, tween(OPEN_MS, easing = FastOutSlowInEasing))
        }
    }
}

/**
 * One exercise of the list. Closed it is a 52 dp row; rows next to each other join into one card
 * (square corners and an inset divider where they meet). Open it is the card with the accent
 * border, 12 dp from its neighbours. Corners, gaps and border animate with the content.
 */
@Composable
private fun WorkoutItem(
    open: Boolean,
    first: Boolean,
    joinedAbove: Boolean,
    joinedBelow: Boolean,
    track: (top: Float, height: Int) -> Unit,
    row: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val spec = tween<Dp>(OPEN_MS, easing = FastOutSlowInEasing)
    val gap by animateDpAsState(if (first || joinedAbove) 0.dp else 12.dp, spec, label = "gap")
    val radiusTop by animateDpAsState(if (joinedAbove) 0.dp else 22.dp, spec, label = "rt")
    val radiusBottom by animateDpAsState(if (joinedBelow) 0.dp else 22.dp, spec, label = "rb")
    val padTop by animateDpAsState(if (open || joinedAbove) 0.dp else 6.dp, spec, label = "pt")
    val padBottom by animateDpAsState(if (open || joinedBelow) 0.dp else 6.dp, spec, label = "pb")
    val border by animateColorAsState(
        if (open) TreinoColors.AccentBorder else TreinoColors.Border,
        tween(OPEN_MS, easing = FastOutSlowInEasing),
        label = "border",
    )
    val shape = RoundedCornerShape(topStart = radiusTop, topEnd = radiusTop, bottomEnd = radiusBottom, bottomStart = radiusBottom)
    val closedState = remember { MutableTransitionState(!open) }.apply { targetState = !open }
    val openState = remember { MutableTransitionState(open) }.apply { targetState = open }
    Column(
        Modifier
            .padding(top = gap)
            .onGloballyPositioned { track(it.positionInWindow().y, it.size.height) }
            .clip(shape)
            .background(TreinoColors.Card, shape)
            .drawWithCache {
                val stroke = 1.dp.toPx()
                val outline = shape.createOutline(size, layoutDirection, this)
                val inset = 16.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    // The edge shared with a joined neighbour is not drawn; the divider takes it.
                    drawOutline(outline, border, style = Stroke(stroke * 2))
                    if (joinedAbove) drawRect(TreinoColors.Card, Offset(stroke, 0f), Size(size.width - 2 * stroke, stroke))
                    if (joinedBelow) drawRect(TreinoColors.Card, Offset(stroke, size.height - stroke), Size(size.width - 2 * stroke, stroke))
                    if (joinedAbove) drawLine(TreinoColors.Border, Offset(inset, stroke / 2), Offset(size.width - inset, stroke / 2), stroke)
                }
            }
            .padding(top = padTop, bottom = padBottom),
    ) {
        AnimatedVisibility(
            visibleState = closedState,
            enter = expandVertically(tween(OPEN_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(OPEN_MS)),
            exit = shrinkVertically(tween(CLOSE_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(CLOSE_MS / 2)),
        ) { row() }
        AnimatedVisibility(
            visibleState = openState,
            enter = expandVertically(tween(OPEN_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(OPEN_MS, delayMillis = 60)),
            exit = shrinkVertically(tween(CLOSE_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(CLOSE_MS / 2)),
        ) { content() }
    }
}

/** A closed exercise's row: 52 dp, padding 16. */
@Composable
private fun CompactRowBox(enabled: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** The open exercise's content; the frame is its [WorkoutItem]. */
@Composable
fun ExerciseBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The collapsed header over the list, plus a little air. */
private val HEADER_CLEARANCE = 110.dp

private const val OPEN_MS = 250
private const val CLOSE_MS = 200

@Composable
private fun RowScope.RowName(item: ItemUi) {
    TText(item.exercise.name, style = TreinoType.Row, modifier = Modifier.weight(1f), maxLines = 1)
}

/** Name, target and tags of the current exercise. */
@Composable
fun ExerciseTitle(item: ItemUi, onName: (() -> Unit)? = null, tags: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TText(
                item.exercise.name,
                style = TreinoType.Heading,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onName != null) Modifier.clickable(role = Role.Button, onClickLabel = "Histórico", onClick = onName) else Modifier),
            )
            TText(item.target, style = TreinoType.mono(13.sp, color = TreinoColors.TextFaint), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = tags)
    }
}

/** The current exercise: tags, sets table, steppers and "Registrar série N". */
@Composable
private fun CurrentExerciseCard(item: ItemUi, draft: SetDraft, sessionId: String?, actions: WorkoutActions) {
    val exercise = item.exercise
    val next = item.nextPosition()
    val rows = maxOf(item.total, item.sets.size + 1)
    var focused by remember(item.item.id, next) { mutableStateOf(CellFocus.NONE) }
    ExerciseBody {
        ExerciseTitle(item, onName = { actions.onHistory(item) }) {
            twoSidesTag(exercise)?.let { Tag(it, icon = if (exercise.equipment == Equipment.DUMBBELL) TreinoIcons.Dumbbell else null) }
            if (item.item.restSec > 0) Tag(restLabel(item.item.restSec))
            if (!exercise.bodyweight) item.maxKg?.let { Tag("máx ${PtBr.kg(it)}") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TableRow {
                val style = TreinoType.Label.copy(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.06.em)
                TText("SÉRIE", style = style, maxLines = 1, modifier = Modifier.width(36.dp))
                TText(item.lastDate?.let { PtBr.shortDate(it).uppercase() }.orEmpty(), style = style, maxLines = 1, modifier = Modifier.weight(1f))
                TText(if (exercise.bodyweight) "" else unitLabel(exercise).uppercase(), style = style, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
                TText("REPS", style = style, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
                Box(Modifier.width(36.dp))
            }
            val lastByPosition = item.lastSets.associateBy { it.position }
            for (position in 0 until rows) {
                val logged = item.sets.getOrNull(position)
                val last = lastByPosition[position]
                when {
                    logged != null -> TableRow(Modifier.clickable(role = Role.Button) { actions.onEditSet(logged) }) {
                        SetNumber(position, accent = false)
                        LastText(last)
                        if (exercise.bodyweight) Box(Modifier.width(72.dp)) else Cell(PtBr.number(logged.weightKg), Modifier.width(72.dp))
                        Cell("${logged.reps}", Modifier.width(60.dp))
                        Box(Modifier.size(36.dp, 28.dp), contentAlignment = Alignment.CenterStart) {
                            // The set just logged pops its check in; older ones just show.
                            val fresh = remember(logged.id) { (logged.loggedAt ?: 0L) > System.currentTimeMillis() - 2_000 }
                            val pop = remember(logged.id) { Animatable(if (fresh) 0.4f else 1f) }
                            LaunchedEffect(logged.id) { if (fresh) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                            DoneMark(Modifier.padding(start = 3.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                        }
                    }
                    position == next -> TableRow(
                        Modifier
                            .bleed(8.dp)
                            .background(TreinoColors.AccentFaint, RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        SetNumber(position, accent = true)
                        LastText(last)
                        if (exercise.bodyweight) Box(Modifier.width(72.dp)) else NumberCell(
                            value = PtBr.number(draft.weightKg),
                            width = 72.dp,
                            highlighted = focused != CellFocus.REPS,
                            decimal = true,
                            onFocus = { if (it) focused = CellFocus.WEIGHT else if (focused == CellFocus.WEIGHT) focused = CellFocus.NONE },
                            onValue = { text -> parseDecimal(text)?.let { actions.onTypeWeight(item, it) } },
                        )
                        NumberCell(
                            value = "${draft.reps}",
                            width = 60.dp,
                            highlighted = focused == CellFocus.REPS,
                            decimal = false,
                            onFocus = { if (it) focused = CellFocus.REPS else if (focused == CellFocus.REPS) focused = CellFocus.NONE },
                            onValue = { text -> text.trim().toIntOrNull()?.takeIf { it >= 0 }?.let { actions.onTypeReps(item, it) } },
                        )
                        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                            Box(Modifier.size(28.dp).border(2.dp, TreinoColors.Accent, CircleShape))
                        }
                    }
                    else -> {
                        val prefill = item.prefill(position, sessionId)
                        TableRow(Modifier.alpha(0.55f)) {
                            SetNumber(position, accent = false)
                            LastText(last)
                            if (exercise.bodyweight) Box(Modifier.width(72.dp)) else Cell(prefill?.let { PtBr.number(it.weightKg) }.orEmpty(), Modifier.width(72.dp))
                            Cell(prefill?.let { "${it.reps}" }.orEmpty(), Modifier.width(60.dp))
                            Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.size(28.dp).border(2.dp, TreinoColors.SurfaceHigh, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        NextSetSteppers(item, draft, onStepWeight = { actions.onStepWeight(item, it) }, onStepReps = { actions.onStepReps(item, it) })
        val view = LocalView.current
        PrimaryButton(
            "Registrar série ${next + 1}",
            onClick = {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                actions.onLogSet(item, draft)
            },
            icon = TreinoIcons.Check,
        )
    }
}

private enum class CellFocus { NONE, WEIGHT, REPS }

/** Load and reps of the next set: the card's steppers, also in the rest sheet. */
@Composable
fun NextSetSteppers(item: ItemUi, draft: SetDraft, onStepWeight: (up: Boolean) -> Unit, onStepReps: (up: Boolean) -> Unit) {
    val exercise = item.exercise
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!exercise.bodyweight) Stepper(
            value = PtBr.kg(draft.weightKg),
            onMinus = { onStepWeight(false) },
            onPlus = { onStepWeight(true) },
            modifier = Modifier.weight(1f),
            minusLabel = "−${stepButton(exercise)}",
            plusLabel = "+${stepButton(exercise)}",
            caption = stepCaption(exercise),
            minusDescription = "Menos ${stepDescription(exercise)}",
            plusDescription = "Mais ${stepDescription(exercise)}",
        )
        Stepper(
            value = "${draft.reps}",
            onMinus = { onStepReps(false) },
            onPlus = { onStepReps(true) },
            modifier = Modifier.weight(1f),
            minusDescription = "Menos 1 rep",
            plusDescription = "Mais 1 rep",
        )
    }
}

/** `.tbl`: 36 | 1fr | 72 | 60 | 36, 8 dp between columns. */
@Composable
private fun TableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SetNumber(position: Int, accent: Boolean) {
    TText(
        "${position + 1}",
        style = TreinoType.mono(16.sp, color = if (accent) TreinoColors.Accent else TreinoColors.Text),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(36.dp),
    )
}

@Composable
private fun RowScope.LastText(last: SetEntry?) {
    TText(
        last?.let(::setText).orEmpty(),
        style = TreinoType.mono(14.sp, color = TreinoColors.TextFaint),
        maxLines = 1,
        modifier = Modifier.weight(1f),
    )
}

/**
 * The current row's load or reps: a `.cell` that takes the keyboard when tapped. The accent ring
 * marks the field being edited (the load until the reps get the focus).
 */
@Composable
private fun NumberCell(
    value: String,
    width: Dp,
    highlighted: Boolean,
    decimal: Boolean,
    onFocus: (Boolean) -> Unit,
    onValue: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(it)
        },
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .onFocusChanged { onFocus(it.isFocused) },
        textStyle = TreinoType.mono(17.sp).copy(textAlign = TextAlign.Center),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        cursorBrush = SolidColor(TreinoColors.Accent),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(TreinoColors.Surface, shape)
                    .then(if (highlighted) Modifier.border(2.dp, TreinoColors.Accent, shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

/** CSS `margin: 0 -[amount]`: the row reaches [amount] past both sides of its parent. */
fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
