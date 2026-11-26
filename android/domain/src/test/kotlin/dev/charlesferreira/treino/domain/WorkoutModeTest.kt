package dev.charlesferreira.treino.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutModeTest {
    private val hour = 60 * 60 * 1000L

    private fun set(session: String, pos: Int, at: Long?) = SetEntry("$session-$pos", session, "ex", pos, 20.0, 10, at)

    @Test
    fun `running only between start and end`() {
        assertFalse(WorkoutMode.running(null, hasEntries = false))
        assertFalse(WorkoutMode.running(Session("s", "2026-11-26"), hasEntries = false))
        assertTrue(WorkoutMode.running(Session("s", "2026-11-26", startedAt = 1L), hasEntries = false))
        // A set logged from the notification before "Começar treino" also counts.
        assertTrue(WorkoutMode.running(Session("s", "2026-11-26"), hasEntries = true))
        assertFalse(WorkoutMode.running(Session("s", "2026-11-26", startedAt = 1L, endedAt = 2L), hasEntries = true))
    }

    @Test
    fun `last activity is the newest set, else the start`() {
        val session = Session("s", "2026-11-26", startedAt = 1_000L)
        assertEquals(1_000L, WorkoutMode.lastActivity(session, emptyList(), emptyList()))
        val sets = listOf(set("s", 0, 5_000L), set("s", 1, 9_000L), set("other", 0, 99_000L))
        assertEquals(9_000L, WorkoutMode.lastActivity(session, sets, emptyList()))
        val time = TimeEntry("t", "s", "run", 0, 30.0, loggedAt = 12_000L)
        assertEquals(12_000L, WorkoutMode.lastActivity(session, sets, listOf(time)))
        assertNull(WorkoutMode.lastActivity(Session("s", "2026-11-26"), emptyList(), emptyList()))
    }

    @Test
    fun `stale after three hours without a set`() {
        val session = Session("s", "2026-11-26", startedAt = 0L)
        val sets = listOf(set("s", 0, 1 * hour))
        assertFalse(WorkoutMode.stale(session, sets, emptyList(), now = 4 * hour))
        assertTrue(WorkoutMode.stale(session, sets, emptyList(), now = 4 * hour + 1))
        assertFalse(WorkoutMode.stale(session.copy(endedAt = 2 * hour), sets, emptyList(), now = 10 * hour))
    }
}
