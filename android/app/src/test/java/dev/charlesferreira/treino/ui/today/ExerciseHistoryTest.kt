package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.SetEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseHistoryTest {
    private fun day(date: String, vararg kg: Double) = ExerciseDay(
        sessionId = date,
        date = date,
        sets = kg.mapIndexed { i, w -> SetEntry("$date-$i", date, "ex", i, w, 10) },
        times = emptyList(),
    )

    @Test
    fun `newest first, today left out, up when the top load rose`() {
        val days = listOf(day("2026-11-10", 14.0, 14.0), day("2026-11-17", 16.0, 14.0), day("2026-11-20", 16.0), day("2026-11-26", 18.0))
        val rows = historyRows(days, today = "2026-11-26")
        assertEquals(listOf("20 nov", "17 nov", "10 nov"), rows.map { it.date })
        assertEquals(listOf(false, true, false), rows.map { it.up })
        assertEquals("16 kg", rows[1].top)
        assertEquals("16 × 10   14 × 10", rows[1].detail)
    }

    @Test
    fun `keeps the last sessions only`() {
        val days = (1..9).map { day("2026-11-0$it", 10.0) }
        assertEquals("9 nov", historyRows(days, today = "2026-11-26", limit = 3).first().date)
        assertEquals(3, historyRows(days, today = "2026-11-26", limit = 3).size)
    }
}
