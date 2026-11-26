package dev.charlesferreira.treino.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseSearchTest {
    private fun ex(id: String, name: String, group: String, vararg aliases: String) =
        Exercise(id, name = name, group = group, aliases = aliases.toList())

    private val all = listOf(
        ex("a", "Remada baixa", "costas"),
        ex("b", "Puxada aberta", "costas", "pulldown"),
        ex("c", "Elevação lateral", "ombros"),
        ex("d", "Supino reto com barra", "peito", "supino"),
        ex("e", "Crucifixo", "peito", "voador"),
        ex("f", "Meu exercício", "meus"),
        ex("g", "Abdominal remador", "abdomen"),
        ex("h", "Exercício importado", "grupo-velho"),
    )

    @Test
    fun `empty query lists all by name`() {
        assertEquals(listOf("g", "e", "c", "h", "f", "b", "a", "d"), ExerciseSearch.search(all, "  ").map { it.id })
    }

    @Test
    fun `accents and case do not matter, starts-with comes first`() {
        assertEquals(listOf("c"), ExerciseSearch.search(all, "ELEVACAO").map { it.id })
        assertEquals(listOf("c"), ExerciseSearch.search(all, "elevação lat").map { it.id })
        // "remada" starts "Remada baixa" and is inside "Abdominal remador".
        assertEquals(listOf("a", "g"), ExerciseSearch.search(all, "rema").map { it.id })
    }

    @Test
    fun `aliases match`() {
        assertEquals(listOf("e"), ExerciseSearch.search(all, "voa").map { it.id })
        assertEquals(listOf("b"), ExerciseSearch.search(all, "pull").map { it.id })
    }

    @Test
    fun `exact match ignores accents and case`() {
        assertEquals("c", ExerciseSearch.exactMatch(all, " elevacao LATERAL ")?.id)
        assertNull(ExerciseSearch.exactMatch(all, "elevação"))
        assertNull(ExerciseSearch.exactMatch(all, ""))
    }

    @Test
    fun `groups follow the PWA order with mine first and unknown groups in mine`() {
        val groups = ExerciseSearch.grouped(ExerciseSearch.search(all, ""))
        assertEquals(listOf("meus", "peito", "costas", "ombros", "abdomen"), groups.map { it.first })
        assertEquals(listOf("h", "f"), groups.first().second.map { it.id })
        assertEquals("Meus exercícios", ExerciseGroups.label("meus"))
        assertEquals("Abdômen", ExerciseGroups.label("abdomen"))
        assertEquals("Quadríceps", ExerciseGroups.label("quadriceps"))
    }

    @Test
    fun `upcoming is today's workout until today has a log, then the one after it`() {
        val ws = listOf(Workout("A", name = "A", position = 0), Workout("B", name = "B", position = 1), Workout("C", name = "C", position = 2))
        val past = Session("s1", "2026-11-24", "A")
        assertEquals("B", WorkoutSequence.upcoming(ws, listOf(past), setOf("s1"), "2026-11-26")?.id)
        val today = Session("s2", "2026-11-26", "B")
        assertEquals("B", WorkoutSequence.upcoming(ws, listOf(past, today), setOf("s1"), "2026-11-26")?.id)
        assertEquals("C", WorkoutSequence.upcoming(ws, listOf(past, today), setOf("s1", "s2"), "2026-11-26")?.id)
        // Reordering the rotation changes what comes after the anchor.
        val reordered = listOf(ws[0].copy(position = 0), ws[2].copy(position = 1), ws[1].copy(position = 2))
        assertEquals("C", WorkoutSequence.upcoming(reordered, listOf(past), setOf("s1"), "2026-11-26")?.id)
    }
}
