package dev.charlesferreira.treino.ui.evolution

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartMathTest {
    private val dates = listOf(
        "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-29", "2026-11-05", "2026-11-12", "2026-11-19", "2026-11-26",
    )

    @Test
    fun `escala do design 326 por 150`() {
        // O gráfico do b-exercise: 12, 12, 12, 14, 14, 14, 14, 16.
        val g = ChartScale.of(listOf(12.0, 12.0, 12.0, 14.0, 14.0, 14.0, 14.0, 16.0), dates, 326f)
        assertEquals(listOf(28f, 68f, 108f, 148f, 188f, 228f, 268f, 308f), g.points.map { it.x })
        assertEquals(listOf(100f, 100f, 100f, 65f, 65f, 65f, 65f, 30f), g.points.map { it.y })
        assertEquals(listOf(AxisLabel("16", 30f), AxisLabel("14", 65f), AxisLabel("12", 100f)), g.yLabels)
        assertEquals(listOf("8 out", "22 out", "5 nov", "19 nov"), g.xLabels.map { it.text })
        assertEquals(listOf(28f, 108f, 188f, 268f), g.xLabels.map { it.at })
    }

    @Test
    fun `linha plana fica no meio e poucos pontos tem todas as datas`() {
        val g = ChartScale.of(listOf(60.0, 60.0, 60.0), dates.take(3), 326f)
        assertEquals(listOf(65f, 65f, 65f), g.points.map { it.y })
        assertEquals(listOf("62", "60", "58"), g.yLabels.map { it.text })
        assertEquals(3, g.xLabels.size)
    }

    @Test
    fun `um ponto so fica no centro`() {
        val g = ChartScale.of(listOf(18.0), dates.take(1), 326f)
        assertEquals(1, g.points.size)
        assertEquals((28f + 308f) / 2, g.points[0].x)
        assertEquals(65f, g.points[0].y)
    }

    @Test
    fun `volume arredonda os rotulos`() {
        val g = ChartScale.of(listOf(2000.0, 2320.0), dates.take(2), 326f)
        assertEquals(listOf("2.320", "2.160", "2.000"), g.yLabels.map { it.text })
        assertTrue(ChartScale.of(emptyList(), emptyList(), 326f).points.isEmpty())
    }

    @Test
    fun `sparkline de 64 por 26`() {
        val p = Sparkline.points(listOf(12.0, 12.0, 14.0, 16.0))
        assertEquals(listOf(2f, 22f, 42f, 62f), p.map { it.x })
        assertEquals(listOf(22f, 22f, 13f, 4f), p.map { it.y })
        assertEquals(listOf(13f, 13f), Sparkline.points(listOf(50.0)).map { it.y })
    }
}
