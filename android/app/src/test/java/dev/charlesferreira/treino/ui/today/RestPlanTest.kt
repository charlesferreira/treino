package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.data.DaySummary
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.timer.RestMath
import dev.charlesferreira.treino.timer.RestRequest
import dev.charlesferreira.treino.timer.RestState
import dev.charlesferreira.treino.timer.WorkoutProgress
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestPlanTest {
    private val pull = Exercise("pull", name = "Puxada", group = "costas", equipment = Equipment.MACHINE)
    private val curl = Exercise("curl", name = "Rosca martelo", group = "biceps", equipment = Equipment.DUMBBELL, countsTwoDumbbells = true)
    private val run = Exercise("run", name = "Esteira", group = "meus", kind = ExerciseKind.TIME)
    private val upper = Workout("w1", name = "Superior B", position = 0)
    private val baseItems = listOf(
        WorkoutItem("i1", "w1", "pull", position = 0, sets = 2, repsMin = 8, repsMax = 12, restSec = 90),
        WorkoutItem("i2", "w1", "curl", position = 1, sets = 2, repsMin = 10, repsMax = 12, restSec = 60),
    )
    private val past = Session("s0", date = "2026-11-19", workoutId = "w1", startedAt = 1L)
    private val today = Session("s1", date = "2026-11-26", workoutId = "w1", startedAt = 1_000L)
    private val pastSets = listOf(
        SetEntry("a", "s0", "pull", 0, 59.0, 10),
        SetEntry("b", "s0", "pull", 1, 64.0, 8),
        SetEntry("c", "s0", "curl", 0, 16.0, 11),
        SetEntry("d", "s0", "curl", 1, 16.0, 10),
    )

    private fun set(id: String, ex: String, pos: Int, kg: Double, reps: Int, at: Long) = SetEntry(id, "s1", ex, pos, kg, reps, loggedAt = at)

    private fun ui(todaySets: List<SetEntry>, items: List<WorkoutItem> = baseItems, session: Session = today): TodayUi {
        val ids = items.map { it.exerciseId }.distinct()
        val histories = ids.associateWith { id ->
            listOfNotNull(
                pastSets.filter { it.exerciseId == id }.takeIf { it.isNotEmpty() }?.let { ExerciseDay("s0", "2026-11-19", it, emptyList()) },
                todaySets.filter { it.exerciseId == id }.takeIf { it.isNotEmpty() }?.let { ExerciseDay("s1", "2026-11-26", it, emptyList()) },
            )
        }
        return buildToday(
            TodayInput(
                date = "2026-11-26", profileName = "Charles", keepScreenOn = false,
                workouts = listOf(upper), workout = upper, nextInSequence = upper, session = session,
                items = items, allItems = items, exercises = listOf(pull, curl, run).associateBy { it.id },
                sets = todaySets, times = emptyList(), histories = histories,
                days = listOf(DaySummary(past, "Superior B", 4, 0)), selectedItemId = null,
            ),
        )
    }

    @Test
    fun restAfterTheFirstSetPointsToTheSecond() {
        val logged = set("x1", "pull", 0, 59.0, 11, at = 2_000)
        val request = assertNotNull(restAfterLog(ui(listOf(logged)), "i1", logged))
        assertEquals(
            RestRequest(
                durationMs = 90_000,
                workoutName = "Superior B",
                exerciseName = "Puxada",
                setNumber = 2,
                setCount = 2,
                target = "64 kg × 8",
                progress = WorkoutProgress(listOf(2, 2), 1),
                nextShort = "64 × 8",
                loggedLabel = "Série 1",
                loggedValue = "59 × 11",
            ),
            request,
        )
    }

    @Test
    fun restAfterTheLastSetOfAnExerciseNamesTheNextExercise() {
        val sets = listOf(set("x1", "pull", 0, 59.0, 10, 2_000), set("x2", "pull", 1, 64.0, 8, 3_000))
        val request = assertNotNull(restAfterLog(ui(sets), "i1", sets[1]))
        assertEquals(90_000, request.durationMs) // the rest of the item just done
        assertEquals("Rosca martelo", request.exerciseName)
        assertEquals(1, request.setNumber)
        assertEquals("16 kg × 11", request.target)
        assertEquals("Puxada, série 2", request.loggedLabel)
        assertEquals(WorkoutProgress(listOf(2, 2), 2), request.progress)
    }

    @Test
    fun noRestAfterTheLastSetOfTheWorkout() {
        val sets = listOf(
            set("x1", "pull", 0, 59.0, 10, 2_000), set("x2", "pull", 1, 64.0, 8, 3_000),
            set("x3", "curl", 0, 16.0, 11, 4_000), set("x4", "curl", 1, 16.0, 10, 5_000),
        )
        val today = ui(sets)
        assertNull(today.nextUp())
        assertNull(restAfterLog(today, "i2", sets[3]))
    }

    @Test
    fun noRestWhenTheItemHasNone() {
        val items = baseItems.map { if (it.id == "i1") it.copy(restSec = 0) else it }
        val logged = set("x1", "pull", 0, 59.0, 10, 2_000)
        assertNull(restAfterLog(ui(listOf(logged), items), "i1", logged))
    }

    @Test
    fun nextStaysOnTheExerciseLoggedLast() {
        // Curl done out of order while the pull has sets left: the next set is curl's second.
        val logged = set("x1", "curl", 0, 16.0, 11, 2_000)
        val today = ui(listOf(logged))
        assertEquals("i2", today.current?.item?.id) // the card agrees with the rest and the notification
        val next = assertNotNull(today.nextUp())
        assertEquals("i2", next.item.item.id)
        assertEquals(2, next.setNumber)
        assertEquals(SetDraft(16.0, 10), next.draft)
    }

    @Test
    fun notificationLogsThePrefilledNextSetThenTheNextRest() {
        // What "Registrar série" does: nextUp() is written as is, then the rest is planned again.
        val first = set("x1", "pull", 0, 59.0, 10, 2_000)
        val next = assertNotNull(ui(listOf(first)).nextUp())
        val draft = assertNotNull(next.draft)
        assertEquals(SetDraft(64.0, 8), draft)
        val written = set("x2", next.item.exercise.id, next.position, draft.weightKg, draft.reps, 3_000)
        val request = assertNotNull(restAfterLog(ui(listOf(first, written)), next.item.item.id, written))
        assertEquals("Rosca martelo", request.exerciseName)
        assertEquals(1, request.setNumber)
        assertEquals("16 × 11", request.nextShort)
        assertEquals(2, request.progress.setsDone)
    }

    @Test
    fun betweenSetsShowsTheWorkoutAndTheNextSet() {
        val info = assertNotNull(betweenSets(ui(listOf(set("x1", "pull", 0, 59.0, 10, 2_000))), nowMs = 9_999))
        assertEquals("Superior B", info.workoutName)
        assertEquals(1_000, info.startedAtMs)
        assertEquals(WorkoutProgress(listOf(2, 2), 1), info.progress)
        assertEquals("A seguir: Puxada, série 2 de 2, 64 × 8", info.next)
        assertTrue(info.canLog)
    }

    @Test
    fun betweenSetsBeforeATimeExerciseCannotLog() {
        val items = baseItems.take(1) + WorkoutItem("i3", "w1", "run", position = 1, sets = 1, minutesMin = 30.0, minutesMax = 40.0)
        val sets = listOf(set("x1", "pull", 0, 59.0, 10, 2_000), set("x2", "pull", 1, 64.0, 8, 3_000))
        val info = assertNotNull(betweenSets(ui(sets, items), 0))
        assertEquals("A seguir: Esteira, 30–40 min", info.next)
        assertFalse(info.canLog)
    }

    @Test
    fun noWorkoutNotificationBeforeTheStartOrAfterTheEnd() {
        assertNull(betweenSets(ui(emptyList(), session = today.copy(startedAt = null)), 0))
        assertNull(betweenSets(ui(listOf(set("x1", "pull", 0, 59.0, 10, 2_000)), session = today.copy(endedAt = 5_000)), 0))
    }

    @Test
    fun allSetsDoneKeepsTheNotificationWithoutTheAction() {
        val sets = listOf(
            set("x1", "pull", 0, 59.0, 10, 2_000), set("x2", "pull", 1, 64.0, 8, 3_000),
            set("x3", "curl", 0, 16.0, 11, 4_000), set("x4", "curl", 1, 16.0, 10, 5_000),
        )
        val info = assertNotNull(betweenSets(ui(sets), 0))
        assertEquals("Todas as séries feitas", info.next)
        assertFalse(info.canLog)
    }

    @Test
    fun restViewFollowsTheStore() {
        val running = RestState.start(
            RestRequest(90_000, "Superior B", "Puxada", 2, 2, "64 kg × 8", WorkoutProgress(listOf(2, 2), 1)),
            nowMs = 0,
        )
        assertEquals(RestView.NONE, restView(null, expanded = true, nowMs = 0))
        assertEquals(RestView.SHEET, restView(running, expanded = true, nowMs = 10))
        assertEquals(RestView.PILL, restView(running, expanded = false, nowMs = 10))
        val done = running.copy(finished = true)
        assertEquals(RestView.DONE, restView(done, expanded = true, nowMs = 90_000 + 60_000))
        assertEquals(RestView.NONE, restView(done, expanded = false, nowMs = 90_000 + REST_DONE_VISIBLE_MS))
    }

    @Test
    fun ringFractionAndStartSurviveAdjusts() {
        val start = RestState.start(RestRequest(90_000, "W", "E", 1, 2, "t", WorkoutProgress.EMPTY), nowMs = 0)
        assertEquals(0.8f, start.fraction(18_000), 0.0001f) // 1:12 of 1:30, as in b-rest
        val plus = RestMath.adjust(start, 15_000, nowMs = 18_000)
        assertEquals(0L, plus.startedAtMs)
        assertEquals("1:45", RestMath.format(plus.totalMs))
        val minus = RestMath.adjust(start, -15_000, nowMs = 18_000)
        assertEquals(0L, minus.startedAtMs)
        assertEquals(1f, start.fraction(0))
        assertEquals(0f, start.fraction(100_000))
    }
}
