package dev.charlesferreira.treino.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.charlesferreira.treino.domain.WorkoutProgress
import dev.charlesferreira.treino.ui.theme.TreinoColors

/** One exercise in the bar: its width weighs [total] sets, [done] of them logged. */
data class BarSegment(val total: Int, val done: Int)

fun WorkoutProgress.barSegments(): List<BarSegment> = segments.map { BarSegment(it.total, it.done) }

/**
 * The header's segmented bar. Each segment's width is proportional to its number of sets. Complete
 * segments are accent; the first incomplete one is the exercise in progress, filled with accent up
 * to done/total over #3A3D44 (the design's hard 50% gradient); the rest are #2A2D33.
 *
 * [height] and [gap] are parameters because the header shrinks on scroll (8 → 4 dp, gap 4 → 3).
 * The corner radius stays 4 dp, as in the design, capped at half the height.
 */
@Composable
fun SegmentedProgressBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    gap: Dp = 4.dp,
    cornerRadius: Dp = 4.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val visible = segments.filter { it.total > 0 }
        if (visible.isEmpty()) return@Canvas
        val gapPx = gap.toPx()
        val totalSets = visible.sumOf { it.total }.toFloat()
        val available = size.width - gapPx * (visible.size - 1)
        val radius = minOf(cornerRadius.toPx(), size.height / 2)
        val corner = CornerRadius(radius, radius)
        val current = visible.indexOfFirst { it.done < it.total }
        var x = 0f
        visible.forEachIndexed { i, seg ->
            val w = available * seg.total / totalSets
            val topLeft = Offset(x, 0f)
            val segSize = Size(w, size.height)
            when {
                seg.done >= seg.total -> drawRoundRect(TreinoColors.Accent, topLeft, segSize, corner)
                i == current -> {
                    drawRoundRect(TreinoColors.SurfaceHigh, topLeft, segSize, corner)
                    val fill = w * seg.done / seg.total
                    if (fill > 0f) {
                        clipRect(left = x, top = 0f, right = x + fill, bottom = size.height) {
                            drawRoundRect(TreinoColors.Accent, topLeft, segSize, corner)
                        }
                    }
                }
                else -> drawRoundRect(TreinoColors.SurfaceLow, topLeft, segSize, corner)
            }
            x += w + gapPx
        }
    }
}
