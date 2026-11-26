package dev.charlesferreira.treino.ui.evolution

import dev.charlesferreira.treino.domain.PtBr
import kotlin.math.round

/** A point in dp, from the chart's top left. */
data class ChartPoint(val x: Float, val y: Float)

data class AxisLabel(val text: String, val at: Float)

/** Everything the Canvas draws, in dp. */
data class ChartGeometry(
    val points: List<ChartPoint>,
    /** Gridlines at [ChartScale.TOP], [ChartScale.MID] and [ChartScale.LOW] with their values. */
    val yLabels: List<AxisLabel>,
    /** Dates under the points. */
    val xLabels: List<AxisLabel>,
)

/**
 * The scale of the `b-exercise` chart, whose SVG is 326 × 150: gridlines at y 30, 65, 100 and
 * the baseline at 128; the top value sits on 30 and the lowest on 100, with the middle value on
 * 65; points from x 28 to 18 short of the right edge; dates at y 145, under every other point.
 */
object ChartScale {
    const val HEIGHT = 150f
    const val TOP = 30f
    const val MID = 65f
    const val LOW = 100f
    const val BASE = 128f
    const val LEFT = 28f
    const val RIGHT_GAP = 18f
    const val DATE_Y = 145f

    fun of(values: List<Double>, dates: List<String>, width: Float): ChartGeometry {
        if (values.isEmpty()) return ChartGeometry(emptyList(), emptyList(), emptyList())
        var lo = values.min()
        var hi = values.max()
        if (hi == lo) {
            // A flat line sits on the middle gridline, with a step above and below.
            val d = flatStep(hi)
            hi += d
            lo = maxOf(0.0, lo - d)
        }
        val right = width - RIGHT_GAP
        val xs = if (values.size == 1) {
            listOf((LEFT + right) / 2)
        } else {
            values.indices.map { LEFT + (right - LEFT) * it / (values.size - 1) }
        }
        val points = values.mapIndexed { i, v ->
            ChartPoint(xs[i], (LOW - (v - lo) / (hi - lo) * (LOW - TOP)).toFloat())
        }
        val yLabels = listOf(
            AxisLabel(label(hi), TOP),
            AxisLabel(label((hi + lo) / 2), MID),
            AxisLabel(label(lo), LOW),
        )
        val every = if (values.size > 4) 2 else 1
        val xLabels = dates.indices.filter { it % every == 0 }.map { AxisLabel(PtBr.shortDate(dates[it]), xs[it]) }
        return ChartGeometry(points, yLabels, xLabels)
    }

    private fun flatStep(v: Double): Double = when {
        v >= 1000 -> 100.0
        v >= 100 -> 10.0
        v >= 10 -> 2.0
        else -> 1.0
    }

    /** "16", "12,5"; whole numbers from 100 up ("2.160"). */
    fun label(v: Double): String = PtBr.number(if (v >= 100) round(v) else v)
}

/**
 * The 64 × 26 sparkline of `b-progress`: points from x 2 to 62; the highest value at y 4 and the
 * lowest at 22; a flat history is a line at 13.
 */
object Sparkline {
    const val WIDTH = 64f
    const val HEIGHT = 26f

    fun points(values: List<Double>): List<ChartPoint> {
        if (values.isEmpty()) return emptyList()
        val series = if (values.size == 1) listOf(values[0], values[0]) else values
        val lo = series.min()
        val hi = series.max()
        return series.mapIndexed { i, v ->
            val x = 2f + 60f * i / (series.size - 1)
            val y = if (hi == lo) 13f else (22.0 - (v - lo) / (hi - lo) * 18.0).toFloat()
            ChartPoint(x, y)
        }
    }
}
