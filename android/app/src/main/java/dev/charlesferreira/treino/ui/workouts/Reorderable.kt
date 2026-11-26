package dev.charlesferreira.treino.ui.workouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex

/** What a row of [ReorderableColumn] gets: the drag handle's modifier and whether it is lifted. */
class ReorderScope(val handle: Modifier, val dragging: Boolean, val index: Int)

/** Moves the element at [from] to [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * A short list (the rotation, a workout's items) reordered by dragging the grip. The lifted row
 * follows the finger, the others slide out of its way, and on release [onMove] gets the new order
 * of keys. Until the stored list catches up the new order is shown, so the rows don't jump back.
 * TalkBack gets "Mover para cima" and "Mover para baixo" on each row.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> String,
    onMove: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, scope: ReorderScope) -> Unit,
) {
    var pending by remember { mutableStateOf<List<String>?>(null) }
    val keys = items.map(key)
    LaunchedEffect(keys) { if (pending == keys || pending?.toSet() != keys.toSet()) pending = null }
    val byKey = items.associateBy(key)
    val order = pending?.takeIf { p -> p.all { it in byKey } && p.size == keys.size } ?: keys

    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dy by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val latestOrder by rememberUpdatedState(order)
    val latestOnMove by rememberUpdatedState(onMove)

    // Where the lifted row would land for the current drag.
    fun target(order: List<String>, from: Int, dy: Float): Int {
        var to = from
        var acc = 0f
        if (dy > 0) {
            while (to + 1 < order.size) {
                val h = heights[order[to + 1]] ?: break
                if (dy > acc + h / 2f) { acc += h; to++ } else break
            }
        } else if (dy < 0) {
            while (to - 1 >= 0) {
                val h = heights[order[to - 1]] ?: break
                if (-dy > acc + h / 2f) { acc += h; to-- } else break
            }
        }
        return to
    }

    fun commit(newOrder: List<String>) {
        if (newOrder != latestOrder) {
            pending = newOrder
            latestOnMove(newOrder)
        }
    }

    val from = dragging?.let { order.indexOf(it) } ?: -1
    val to = if (from >= 0) target(order, from, dy) else -1
    val liftedHeight = dragging?.let { heights[it] } ?: 0

    Column(modifier) {
        order.forEachIndexed { index, k ->
            val item = byKey[k] ?: return@forEachIndexed
            key(k) {
                val lifted = k == dragging
                val shift = when {
                    from < 0 || lifted -> 0f
                    index in (from + 1)..to -> -liftedHeight.toFloat()
                    index in to until from -> liftedHeight.toFloat()
                    else -> 0f
                }
                val animated by animateFloatAsState(shift, tween(160), label = "shift")
                val handle = Modifier.pointerInput(k) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = k
                            dy = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dy += amount.y
                        },
                        onDragEnd = {
                            val current = latestOrder
                            val i = current.indexOf(k)
                            if (i >= 0) commit(current.moved(i, target(current, i, dy)))
                            dragging = null
                            dy = 0f
                        },
                        onDragCancel = {
                            dragging = null
                            dy = 0f
                        },
                    )
                }
                val actions = buildList {
                    if (index > 0) add(CustomAccessibilityAction("Mover para cima") { commit(order.moved(index, index - 1)); true })
                    if (index < order.lastIndex) add(CustomAccessibilityAction("Mover para baixo") { commit(order.moved(index, index + 1)); true })
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[k] = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) dy else animated
                            if (lifted) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                shadowElevation = 12f
                            }
                        }
                        .semantics { customActions = actions },
                ) {
                    row(item, ReorderScope(handle, lifted, index))
                }
            }
        }
    }
}
