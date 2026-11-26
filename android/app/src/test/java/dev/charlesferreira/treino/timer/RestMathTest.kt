package dev.charlesferreira.treino.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestMathTest {

    private val progress = WorkoutProgress(listOf(3, 3, 2, 2, 2, 2, 2), setsDone = 9)

    private fun rest(durationMs: Long, now: Long) = RestState.start(
        RestRequest(durationMs, "Superior B", "Rosca martelo", 2, 2, "16 kg × 10", progress),
        now,
    )

    @Test
    fun startSetsEndFromNow() {
        val s = rest(90_000, now = 1_000)
        assertEquals(91_000, s.endsAtMs)
        assertEquals(90_000, s.totalMs)
        assertEquals(90_000, s.remainingMs(1_000))
        assertEquals("série 2 de 2", s.setLabel)
    }

    @Test
    fun plusAndMinusMoveTheEndAndTheTotal() {
        val s = rest(90_000, now = 0)
        val plus = RestMath.adjust(s, 15_000, nowMs = 10_000)
        assertEquals(105_000, plus.endsAtMs)
        assertEquals(105_000, plus.totalMs)
        val minus = RestMath.adjust(plus, -15_000, nowMs = 20_000)
        assertEquals(90_000, minus.endsAtMs)
        assertEquals(90_000, minus.totalMs)
    }

    @Test
    fun minusNeverPassesZero() {
        val s = rest(15_000, now = 0)
        val adjusted = RestMath.adjust(s, -15_000, nowMs = 5_000)
        assertEquals(5_000, adjusted.endsAtMs)
        assertEquals(0, adjusted.remainingMs(5_000))
        assertEquals(5_000, adjusted.totalMs)
        assertTrue(RestMath.isDue(adjusted, 5_000))
    }

    @Test
    fun minusOnAnOverdueRestKeepsItAtNow() {
        val s = rest(10_000, now = 0)
        val adjusted = RestMath.adjust(s, -15_000, nowMs = 12_000)
        assertEquals(12_000, adjusted.endsAtMs)
        assertEquals(0, adjusted.remainingMs(12_000))
    }

    @Test
    fun plusThirtyRestartsAFinishedRest() {
        val done = rest(90_000, now = 0).copy(finished = true)
        val again = RestMath.adjust(done, 30_000, nowMs = 100_000)
        assertFalse(again.finished)
        assertEquals(130_000, again.endsAtMs)
        assertEquals(30_000, again.totalMs)
        assertFalse(RestMath.isDue(again, 100_000))
    }

    @Test
    fun minusOnAFinishedRestDoesNothing() {
        val done = rest(90_000, now = 0).copy(finished = true)
        assertEquals(done, RestMath.adjust(done, -15_000, nowMs = 100_000))
    }

    @Test
    fun finishedIsNeverDue() {
        assertFalse(RestMath.isDue(rest(1, now = 0).copy(finished = true), 10))
    }

    @Test
    fun formatRoundsUp() {
        assertEquals("1:30", RestMath.format(90_000))
        assertEquals("0:48", RestMath.format(47_001))
        assertEquals("0:01", RestMath.format(1))
        assertEquals("0:00", RestMath.format(0))
        assertEquals("0:00", RestMath.format(-5))
        assertEquals("10:00", RestMath.format(600_000))
    }

    @Test
    fun requestNeedsAPositiveDuration() {
        assertFailsWith<IllegalArgumentException> {
            RestRequest(0, "", "", 1, 1, "", WorkoutProgress.EMPTY)
        }
    }
}

class RestProgressTest {

    @Test
    fun oneSegmentPerExerciseSizedBySets() {
        val p = WorkoutProgress(listOf(3, 3, 2, 2, 2, 2, 2), setsDone = 9)
        assertEquals(listOf(3, 3, 2, 2, 2, 2, 2), RestProgress.segments(p).map { it.length })
        assertEquals(16, p.totalSets)
        assertEquals(9, RestProgress.position(p))
    }

    @Test
    fun segmentsAreProportional() {
        val p = WorkoutProgress(listOf(4, 2), setsDone = 0)
        val lengths = RestProgress.segments(p).map { it.length }
        assertEquals(2.0, lengths[0].toDouble() / lengths[1])
    }

    @Test
    fun progressBounds() {
        assertFailsWith<IllegalArgumentException> { WorkoutProgress(listOf(2, 2), setsDone = 5) }
        assertFailsWith<IllegalArgumentException> { WorkoutProgress(listOf(2, 0), setsDone = 0) }
        assertEquals(4, RestProgress.position(WorkoutProgress(listOf(2, 2), setsDone = 4)))
    }

    @Test
    fun planRoundTrips() {
        val plan = listOf(3, 3, 2)
        assertEquals("3,3,2", RestProgress.encode(plan))
        assertEquals(plan, RestProgress.decode("3,3,2"))
        assertEquals(emptyList(), RestProgress.decode(null))
        assertEquals(listOf(3, 2), RestProgress.decode("3, x,0,2"))
    }
}
