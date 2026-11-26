package dev.charlesferreira.treino.ui.workouts

import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Steps
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutsStateTest {
    private val curl = Exercise("curl", name = "Rosca martelo", group = "biceps", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val press = Exercise("press", name = "Leg press", group = "quadriceps", equipment = Equipment.MACHINE)
    private val run = Exercise("run", name = "Corrida", group = "cardio", kind = ExerciseKind.TIME)
    private val exercises = listOf(curl, press, run).associateBy { it.id }

    private fun item(id: String, workout: String, ex: String, pos: Int, sets: Int = 3, reps: Pair<Int, Int>? = 8 to 12, min: Pair<Double, Double>? = null, rest: Int = 90) =
        WorkoutItem(id, workout, ex, pos, sets, reps?.first, reps?.second, min?.first, min?.second, rest)

    @Test
    fun `sequence is ordered by position, numbered, summarized and marks the upcoming one`() {
        val ws = listOf(Workout("b", name = "Corrida Z2", position = 1), Workout("a", name = "Superior", position = 0), Workout("c", name = "Vazio", position = 2))
        val items = listOf(
            item("1", "a", "curl", 0, sets = 3),
            item("2", "a", "press", 1, sets = 2),
            item("3", "b", "run", 0, sets = 1, reps = null, min = 30.0 to 40.0, rest = 0),
        )
        val rows = buildSequence(ws, items, exercises, upcomingId = "b")
        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
        assertEquals(listOf(1, 2, 3), rows.map { it.number })
        assertEquals("2 exercícios, 5 séries", rows[0].summary)
        assertEquals("tempo, 30–40 min", rows[1].summary)
        assertEquals("sem exercícios", rows[2].summary)
        assertEquals(listOf(false, true, false), rows.map { it.upcoming })
        assertEquals("1 exercício, 1 série", workoutSummary(listOf(item("x", "a", "curl", 0, sets = 1)), exercises))
    }

    @Test
    fun `item lines and the duration estimate`() {
        assertEquals("3 × 8–12, 2:15", itemLine(item("1", "a", "curl", 0, rest = 135), curl))
        assertEquals("3 × 10, 1:30", itemLine(item("1", "a", "curl", 0, reps = 10 to 10), curl))
        assertEquals("4 séries", itemLine(item("1", "a", "curl", 0, sets = 4, reps = null, rest = 0), curl))
        assertEquals("30–40 min", itemLine(item("1", "a", "run", 0, sets = 1, reps = null, min = 30.0 to 40.0, rest = 0), run))
        assertEquals("2 × 2,5 min, 1:00", itemLine(item("1", "a", "run", 0, sets = 2, reps = null, min = 2.5 to 2.5, rest = 60), run))
        // 2 load items: 2 × 60 setup + 3 × (60 + 120) + 2 × (60 + 90) = 120 + 540 + 300 = 960 s = 16 min, about 15.
        val load = listOf(item("1", "a", "curl", 0, sets = 3, rest = 120), item("2", "a", "press", 1, sets = 2, rest = 90))
        assertEquals(15, estimatedMinutes(load, exercises))
        // Time: 60 setup + 35 min = 36 min, about 35.
        assertEquals(35, estimatedMinutes(listOf(item("3", "b", "run", 0, sets = 1, reps = null, min = 30.0 to 40.0, rest = 0)), exercises))
        assertNull(estimatedMinutes(emptyList(), exercises))
        val ui = buildWorkoutEdit(Workout("a", name = "A", position = 0), load.reversed(), exercises)
        assertEquals(listOf("1", "2"), ui.rows.map { it.item.id })
        assertEquals("2 exercícios", ui.exercisesTag)
        assertEquals("5 séries", ui.setsTag)
        assertEquals("cerca de 15 min", ui.durationTag)
    }

    @Test
    fun `range validation`() {
        assertEquals(RangeInput.Ok(8.0, 12.0), parseRange("8", "12", decimals = false))
        assertEquals(RangeInput.Ok(8.0, 12.0), parseRange("12", "8", decimals = false))
        assertEquals(RangeInput.Ok(10.0, 10.0), parseRange("10", "", decimals = false))
        assertEquals(RangeInput.Ok(10.0, 10.0), parseRange(" ", "10", decimals = false))
        assertEquals(RangeInput.Empty, parseRange("", "", decimals = false))
        assertEquals(RangeInput.Invalid, parseRange("0", "12", decimals = false))
        assertEquals(RangeInput.Invalid, parseRange("8,5", "12", decimals = false))
        assertEquals(RangeInput.Invalid, parseRange("1000", "", decimals = false))
        assertEquals(RangeInput.Ok(2.5, 30.0), parseRange("2,5", "30", decimals = true))
        assertEquals(RangeInput.Invalid, parseRange("abc", "30", decimals = true))
        assertEquals(RangeInput.Invalid, parseRange("700", "", decimals = true))
    }

    @Test
    fun `item draft keeps both ranges, clamps steppers and writes only its kind`() {
        val base = item("1", "a", "curl", 0, sets = 2, reps = 10 to 12, rest = 90)
        var d = ItemDraft.of(base, curl)
        assertEquals(ExerciseKind.LOAD, d.kind)
        assertEquals("20" to "30", d.minutesMin to d.minutesMax)
        d = d.setsMinus().setsMinus().setsMinus()
        assertEquals(1, d.sets)
        repeat(50) { d = d.restPlus() }
        assertEquals(MAX_REST, d.restSec)
        d = d.copy(restSec = 0).restMinus()
        assertEquals(0, d.restSec)
        assertEquals("sem", restText(d.restSec))

        val time = d.copy(kind = ExerciseKind.TIME).applyTo(base)!!
        assertEquals(null to null, time.repsMin to time.repsMax)
        assertEquals(20.0 to 30.0, time.minutesMin to time.minutesMax)

        val back = ItemDraft.of(time, run)
        assertEquals("8" to "12", back.repsMin to back.repsMax)

        val bad = d.copy(repsMin = "0")
        assertFalse(bad.valid)
        assertNull(bad.applyTo(base))

        val notes = d.copy(notes = "  pegada neutra ").applyTo(base)!!
        assertEquals("pegada neutra", notes.notes)
        assertEquals(listOf("Séries" to "1", "Reps" to "10–12", "Descanso" to "sem"), d.prescription())
    }

    @Test
    fun `equipment draft - options, defaults and what it writes`() {
        var d = EquipmentDraft.of(curl)
        assertEquals(listOf("1 kg", "2 kg", "2,5 kg", "5 kg"), d.stepOptions().map { it.label })
        assertEquals("2 kg", d.stepOptions().single { it.selected }.label)
        d = d.chooseStep(2.5)
        assertEquals(2.5, d.applyTo(curl).stepKg)
        d = d.chooseStep(2.0)
        assertNull(d.applyTo(curl).stepKg)

        d = d.copy(countsTwoDumbbells = false)
        assertFalse(d.applyTo(curl).countsTwoDumbbells)

        // Machine: "1 placa" first and chosen by default, plate weight kept apart from the default.
        d = d.select(Equipment.MACHINE)
        assertEquals("1 placa", d.stepOptions().first().label)
        assertTrue(d.stepOptions().first().selected)
        d = d.platePlus().platePlus()
        val machine = d.applyTo(curl)
        assertEquals(Equipment.MACHINE, machine.equipment)
        assertFalse(machine.countsTwoDumbbells)
        assertEquals(6.0, machine.plateKg)
        assertEquals(6.0, Steps.of(machine).kg)
        assertEquals(1, Steps.of(machine).plates)

        // Back to dumbbells: counts 2 again, default step.
        val dumbbell = d.select(Equipment.DUMBBELL).applyTo(machine)
        assertTrue(dumbbell.countsTwoDumbbells)
        assertNull(dumbbell.stepKg)
        assertEquals(2.0, Steps.of(dumbbell).kg)

        // An imported odd step shows up as an option.
        assertTrue(EquipmentDraft.of(press.copy(stepKg = 7.5)).stepOptions().any { it.label == "7,5 kg" && it.selected })
    }

    @Test
    fun `picker - create row, groups, trailing text`() {
        val all = listOf(curl, press, run, Exercise("mine", name = "Remada maluca", group = "meus", aliases = listOf("remadinha")))
        val add = buildPicker(all, "rem", browse = false)
        assertEquals("rem", add.create)
        assertEquals(listOf("Meus exercícios"), add.sections.map { it.label })
        assertNull(add.sections.single().rows.single().trailing)

        assertNull(buildPicker(all, "rosca MARTELO ", browse = false).create)
        assertNull(buildPicker(all, "", browse = false).create)

        val browse = buildPicker(all, "", browse = true)
        assertEquals(listOf("Meus exercícios", "Bíceps", "Quadríceps", "Cardio"), browse.sections.map { it.label })
        assertEquals("Halteres 2×", browse.sections[1].rows.single().trailing)
        assertEquals("Máquina", browse.sections[2].rows.single().trailing)
        assertEquals("tempo", browse.sections[3].rows.single().trailing)
    }

    @Test
    fun `reorder helper and the first item of an exercise`() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(1, 5))
        val ws = listOf(Workout("x", name = "X", position = 1), Workout("y", name = "Y", position = 0))
        val items = listOf(item("1", "x", "curl", 0), item("2", "y", "curl", 3), item("3", "y", "curl", 1), item("4", "gone", "curl", 0))
        assertEquals("3", firstItemOf("curl", ws, items)?.id)
        assertNull(firstItemOf("press", ws, items))
    }
}
