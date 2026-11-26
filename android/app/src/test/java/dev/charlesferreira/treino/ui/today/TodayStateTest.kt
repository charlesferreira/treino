package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.data.DaySummary
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayStateTest {
    private val curl = Exercise("curl", name = "Rosca martelo", group = "biceps", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val pull = Exercise("pull", name = "Puxada", group = "costas", equipment = Equipment.MACHINE)
    private val run = Exercise("run", name = "Corrida", group = "meus", kind = ExerciseKind.TIME)
    private val upper = Workout("w1", name = "Superior B", position = 0)
    private val cardio = Workout("w2", name = "Corrida Z2", position = 1)
    private val items = listOf(
        WorkoutItem("i1", "w1", "pull", position = 0, sets = 2, repsMin = 8, repsMax = 12, restSec = 90),
        WorkoutItem("i2", "w1", "curl", position = 1, sets = 2, repsMin = 10, repsMax = 12, restSec = 90),
    )
    private val runItem = WorkoutItem("i3", "w2", "run", position = 0, sets = 1, minutesMin = 30.0, minutesMax = 40.0)

    private val past = Session("s0", date = "2026-11-19", workoutId = "w1", startedAt = 1L)
    private val today = Session("s1", date = "2026-11-26", workoutId = "w1", startedAt = 100L)

    private val pastSets = listOf(
        SetEntry("a", "s0", "pull", 0, 59.0, 10),
        SetEntry("b", "s0", "pull", 1, 64.0, 8),
        SetEntry("c", "s0", "curl", 0, 14.0, 12),
        SetEntry("d", "s0", "curl", 1, 14.0, 11),
    )

    private fun input(todaySets: List<SetEntry> = emptyList(), selected: String? = null, times: List<TimeEntry> = emptyList()): TodayInput {
        val histories = listOf("pull", "curl").associateWith { id ->
            listOfNotNull(
                ExerciseDay("s0", "2026-11-19", pastSets.filter { it.exerciseId == id }, emptyList()),
                todaySets.filter { it.exerciseId == id }.takeIf { it.isNotEmpty() }?.let { ExerciseDay("s1", "2026-11-26", it, emptyList()) },
            )
        }
        return TodayInput(
            date = "2026-11-26",
            profileName = "Charles",
            keepScreenOn = false,
            workouts = listOf(upper, cardio),
            workout = upper,
            nextInSequence = cardio,
            session = today,
            items = items,
            allItems = items + runItem,
            exercises = listOf(curl, pull, run).associateBy { it.id },
            sets = todaySets,
            times = times,
            histories = histories,
            days = listOf(DaySummary(past, "Superior B", 4, 0)),
            selectedItemId = selected,
        )
    }

    @Test
    fun startListShowsTheLastSession() {
        val ui = buildToday(input())
        assertEquals(listOf(1, 2), ui.items.map { it.number })
        assertEquals("2 × 8–12", ui.items[0].target)
        assertEquals("2026-11-19", ui.items[0].lastDate)
        assertEquals("64 kg", loggedSummary(ui.items[0].lastSets, ui.items[0].lastTimes))
        assertEquals("2026-11-19", ui.lastDoneDate)
        assertEquals(4, ui.setCount)
        assertEquals("Quinta, 26 nov", ui.dateLabel)
    }

    @Test
    fun switchOptionsFollowTheRotation() {
        val ui = buildToday(input())
        assertEquals(listOf("Superior B", "Corrida Z2"), ui.switchOptions.map { it.workout.name })
        assertEquals(listOf(true, false), ui.switchOptions.map { it.selected })
        assertEquals("feito em 19 nov", ui.switchOptions[0].subtitle)
        assertEquals("próximo da sequência", ui.switchOptions[1].subtitle)
        assertEquals("tempo, 30–40 min", switchSubtitle(false, null, listOf(runItem), mapOf("run" to run)))
    }

    @Test
    fun currentIsTheFirstNotCompleteAndPrefillComesFromTheLastSession() {
        val ui = buildToday(input())
        val current = ui.current!!
        assertEquals("i1", current.item.id)
        assertEquals(SetDraft(59.0, 10), current.defaultSetDraft("s1"))
        assertEquals(SetDraft(64.0, 8), current.prefill(1, "s1"))
        assertEquals(64.0, current.maxKg)

        val oneDone = buildToday(input(todaySets = listOf(SetEntry("t1", "s1", "pull", 0, 61.5, 10))))
        // The next set still comes from the last session's same set, not from today's.
        assertEquals(SetDraft(64.0, 8), oneDone.current!!.defaultSetDraft("s1"))
        assertEquals(DraftKey("i1", 1), oneDone.current!!.draftKey())
        assertEquals(1, oneDone.progress.done)
    }

    @Test
    fun completeItemsLeaveTheCardUnlessPicked() {
        val done = listOf(
            SetEntry("t1", "s1", "pull", 0, 64.0, 10),
            SetEntry("t2", "s1", "pull", 1, 64.0, 9),
        )
        assertEquals("i2", buildToday(input(todaySets = done)).current!!.item.id)
        assertEquals("i1", buildToday(input(todaySets = done, selected = "i1")).current!!.item.id)
        val all = done + listOf(SetEntry("t3", "s1", "curl", 0, 16.0, 10), SetEntry("t4", "s1", "curl", 1, 16.0, 10))
        assertNull(buildToday(input(todaySets = all)).current)
        assertEquals(16.0, buildToday(input(todaySets = all)).items[1].maxKg)
    }

    @Test
    fun neverDoneStartsEmptyThenRepeatsTodaysSet() {
        val fresh = buildToday(input().copy(histories = emptyMap())).current!!
        assertEquals(SetDraft(0.0, 8), fresh.defaultSetDraft("s1"))
        val afterOne = buildToday(
            input(todaySets = listOf(SetEntry("t1", "s1", "pull", 0, 40.0, 9))).copy(histories = emptyMap()),
        ).current!!
        assertEquals(SetDraft(40.0, 9), afterOne.defaultSetDraft("s1"))
    }

    @Test
    fun timeDraftComesFromTheLastRun() {
        val runUi = ItemUi(
            item = runItem, exercise = run, number = 1, target = itemTarget(runItem, run), done = 0, total = 1,
            sets = emptyList(), times = emptyList(), lastDate = "2026-11-18",
            lastSets = emptyList(), lastTimes = listOf(TimeEntry("x", "s9", "run", 0, 35.0, 5.2, "esteira")),
            maxKg = null, history = emptyList(),
        )
        assertEquals("30–40 min", runUi.target)
        assertEquals(TimeDraft(35.0, "5,2", "esteira"), runUi.defaultTimeDraft())
        assertEquals("35 min 5,2 km esteira", timeText(runUi.lastTimes[0]))
        assertEquals(TimeDraft(30.0, "", null), runUi.copy(lastTimes = emptyList()).defaultTimeDraft())
    }

    @Test
    fun labels() {
        assertEquals("kg/halter", unitLabel(curl))
        assertEquals("kg", unitLabel(pull))
        assertEquals("2", stepLabel(curl))
        assertEquals("1 placa", stepLabel(pull))
        assertEquals("2,5", stepLabel(curl.copy(stepKg = 2.5)))
        assertEquals("1:30", restLabel(90))
        assertEquals("34:12", clockLabel(34 * 60_000L + 12_000))
        assertEquals("1:02:09", clockLabel(3_729_000))
        assertEquals("00:00", clockLabel(-5))
        assertEquals("14 × 12", setText(pastSets[2]))
        assertEquals(14.5, parseDecimal("14,5"))
        assertNull(parseDecimal("abc"))
    }
}
