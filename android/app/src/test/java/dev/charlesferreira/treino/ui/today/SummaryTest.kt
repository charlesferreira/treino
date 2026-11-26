package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.data.DaySummary
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SummaryTest {
    private val curl = Exercise("curl", name = "Rosca martelo", group = "biceps", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val pull = Exercise("pull", name = "Puxada supinada", group = "costas", equipment = Equipment.MACHINE)
    private val row = Exercise("row", name = "Remada", group = "costas", equipment = Equipment.CABLE)
    private val workout = Workout("w1", name = "Superior B", position = 0)
    private val items = listOf(
        WorkoutItem("i1", "w1", "curl", 0, sets = 2),
        WorkoutItem("i2", "w1", "pull", 1, sets = 1),
        WorkoutItem("i3", "w1", "row", 2, sets = 1),
    )
    private val prev = Session("s0", "2026-11-19", "w1", startedAt = 0L, endedAt = 54 * 60_000L)
    private val now = Session("s1", "2026-11-26", "w1", startedAt = 1_000_000L, endedAt = 1_000_000L + 58 * 60_000L)

    private val prevSets = listOf(
        SetEntry("a", "s0", "curl", 0, 14.0, 12),
        SetEntry("b", "s0", "pull", 0, 59.0, 10),
        SetEntry("c", "s0", "row", 0, 52.0, 12),
    )
    private val sets = listOf(
        SetEntry("d", "s1", "curl", 0, 16.0, 10),
        SetEntry("e", "s1", "curl", 1, 16.0, 9),
        SetEntry("f", "s1", "pull", 0, 64.0, 8),
        SetEntry("g", "s1", "row", 0, 52.0, 12),
    )

    private fun history(id: String) = listOf(
        ExerciseDay("s0", prev.date, prevSets.filter { it.exerciseId == id }, emptyList()),
        ExerciseDay("s1", now.date, sets.filter { it.exerciseId == id }, emptyList()),
    )

    @Test
    fun factsOfTheSession() {
        val ui = buildSummary(
            SummaryInput(
                session = now, workout = workout, items = items,
                exercises = listOf(curl, pull, row).associateBy { it.id },
                sets = sets, times = emptyList(),
                previous = prev, previousSets = prevSets, previousTimes = emptyList(),
                histories = listOf("curl", "pull", "row").associateWith(::history),
            ),
        )
        assertEquals("Superior B", ui.workoutName)
        assertEquals(58, ui.minutes)
        assertEquals(54, ui.previousMinutes)
        assertEquals(4, ui.setsDone); assertEquals(4, ui.setsTotal)
        assertEquals(3, ui.previousSets)
        // Volume counts the dumbbell curl twice: 16×10×2 + 16×9×2 + 64×8 + 52×12.
        assertEquals(16.0 * 10 * 2 + 16.0 * 9 * 2 + 64 * 8 + 52 * 12, ui.volumeKg)
        assertEquals(14.0 * 12 * 2 + 59 * 10 + 52 * 12, ui.previousVolumeKg)
        assertEquals(39, ui.reps)
        assertEquals("2026-11-19", ui.previousDate)
        // Only what went up, in workout order; the note is a fact, never a recommendation.
        assertEquals(
            listOf(LiftUi("Rosca martelo", 14.0, 16.0, "por halter"), LiftUi("Puxada supinada", 59.0, 64.0, "+1 placa")),
            ui.lifts,
        )
        assertEquals(((ui.volumeKg - ui.previousVolumeKg!!) / ui.previousVolumeKg!! * 100).let { Math.round(it).toInt() }, ui.volumeChangePercent)
    }

    @Test
    fun noPreviousSession() {
        val ui = buildSummary(
            SummaryInput(
                session = now, workout = workout, items = items, exercises = listOf(curl, pull, row).associateBy { it.id },
                sets = sets, times = emptyList(), previous = null, previousSets = emptyList(), previousTimes = emptyList(),
                histories = emptyMap(),
            ),
        )
        assertNull(ui.previousDate)
        assertNull(ui.volumeChangePercent)
        assertEquals(emptyList(), ui.lifts)
    }

    @Test
    fun previousSessionIsTheWorkoutsLastExecutedDay() {
        val days = listOf(
            DaySummary(now, "Superior B", 4, 0),
            DaySummary(prev, "Superior B", 3, 0),
            DaySummary(Session("x", "2026-11-22", "w1"), "Superior B", 0, 0),
            DaySummary(Session("y", "2026-11-24", "w2"), "Pernas", 5, 0),
        )
        assertEquals(prev, previousSession(days, "w1", "2026-11-26"))
        assertNull(previousSession(days, null, "2026-11-26"))
    }
}
