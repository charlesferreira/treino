package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.ui.evolution.equipmentTag
import dev.charlesferreira.treino.ui.workouts.EquipmentDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymFeedbackTest {
    private fun exercise(eq: Equipment, two: Boolean = false) = Exercise("ex", name = "X", group = "meus", equipment = eq, countsTwoDumbbells = two)
    private fun set(kg: Double, reps: Int, i: Int = 0) = SetEntry("s$i", "d", "ex", i, kg, reps)

    @Test
    fun `bodyweight sets read as reps`() {
        assertEquals("12 reps", setText(set(0.0, 12)))
        assertEquals("25 × 12", setText(set(25.0, 12)))
        assertEquals("15 reps", loggedSummary(listOf(set(0.0, 12), set(0.0, 15, 1)), emptyList()))
        assertTrue(exercise(Equipment.BODYWEIGHT).bodyweight)
    }

    @Test
    fun `unilateral counts two sides on a cable, never on a barbell or the body`() {
        assertEquals("Unilateral", twoSidesTag(exercise(Equipment.CABLE, two = true)))
        assertEquals("kg/lado", unitLabel(exercise(Equipment.CABLE, two = true)))
        assertEquals("Halteres 2×", twoSidesTag(exercise(Equipment.DUMBBELL, two = true)))
        assertEquals("kg/halter", unitLabel(exercise(Equipment.DUMBBELL, two = true)))
        assertNull(twoSidesTag(exercise(Equipment.CABLE)))
        assertEquals("Cabo, unilateral", equipmentTag(exercise(Equipment.CABLE, two = true)))

        val cable = EquipmentDraft.of(exercise(Equipment.CABLE)).copy(countsTwoDumbbells = true)
        assertTrue(cable.applyTo(exercise(Equipment.CABLE)).countsTwoDumbbells)
        val barbell = cable.select(Equipment.BARBELL).copy(countsTwoDumbbells = true)
        assertFalse(barbell.applyTo(exercise(Equipment.CABLE)).countsTwoDumbbells)
        assertFalse(EquipmentDraft.of(exercise(Equipment.CABLE)).select(Equipment.BODYWEIGHT).copy(countsTwoDumbbells = true).canCountTwo)
    }

    @Test
    fun `history lists every past session`() {
        val days = (1..9).map { d -> ExerciseDay("s$d", "2026-11-0$d", listOf(SetEntry("x$d", "s$d", "ex", 0, 10.0, 10)), emptyList()) }
        assertEquals(9, historyRows(days, today = "2026-11-26").size)
    }
}
