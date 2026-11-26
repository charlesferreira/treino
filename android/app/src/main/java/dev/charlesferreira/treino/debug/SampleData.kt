package dev.charlesferreira.treino.debug

import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.Steps
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug only: the design's "Superior B", from catalog exercises, to look at the screens with data.
 *
 * - Superior B once a week for nine weeks (63 to 7 days ago). The load climbs one step every few
 *   weeks and ends at [Line.before] 14 days ago and [Line.last] 7 days ago, so Hoje still has the
 *   same last session, top load and increase. The two oldest are "imported": no start, no times.
 * - "Pernas A" (leg press and extensora on the machine, stiff with dumbbells) on the same weeks, two
 *   days before Superior B (65 to 9 days ago).
 * - "Corrida Z2", a time workout, with a weekly run, the latest 6 days ago.
 *
 * The rotation is Superior B, Pernas A, Corrida Z2, so after the run 6 days ago Hoje suggests
 * Superior B, as before.
 */
object SampleData {
    private data class Line(val name: String, val sets: Int, val reps: Pair<Int, Int>, val before: Double, val last: Double, val lastReps: Int)

    private val SUPERIOR_B = listOf(
        Line("Desenvolvimento com halteres", 3, 8 to 12, 16.0, 18.0, 10),
        Line("Puxada supinada na polia", 3, 8 to 12, 59.0, 64.0, 10),
        Line("Remada baixa no triângulo", 3, 10 to 12, 52.0, 52.0, 12),
        Line("Crucifixo na máquina (peck deck)", 2, 12 to 15, 40.0, 45.0, 13),
        Line("Rosca martelo", 2, 10 to 12, 14.0, 14.0, 12),
        Line("Tríceps na polia com corda", 2, 10 to 15, 25.0, 27.0, 12),
        Line("Elevação lateral com halteres", 2, 12 to 15, 8.0, 8.0, 14),
    )

    private val PERNAS_A = listOf(
        Line("Leg press 45°", 4, 10 to 12, 170.0, 180.0, 10),
        Line("Cadeira extensora", 3, 12 to 15, 50.0, 55.0, 12),
        Line("Stiff com halteres", 3, 10 to 12, 20.0, 22.0, 10),
        Line("Panturrilha sentada", 3, 12 to 15, 40.0, 40.0, 14),
    )

    /** Superior B: 9 weeks, newest 7 days ago. Index 0 is the oldest. */
    private val UPPER_DAYS_AGO = (0 until 9).map { 63L - 7 * it }

    /** Pernas A: 9 weeks, the newest 9 days ago (before the latest run, so the rotation holds). */
    private val LEGS_DAYS_AGO = (0 until 9).map { 65L - 7 * it }

    /** The oldest sessions came from the PWA: no start, no end, no set times. */
    private const val IMPORTED = 2

    suspend fun createSuperiorB(repository: TreinoRepository) {
        repository.seedCatalog()
        val byName = repository.observeExercises().first().associateBy { it.name }
        val today = LocalDate.parse(repository.today())

        val upper = repository.createWorkout("Superior B")
        val upperLines = addItems(repository, upper.id, SUPERIOR_B, byName)
        val legs = repository.createWorkout("Pernas A")
        val legLines = addItems(repository, legs.id, PERNAS_A, byName)

        UPPER_DAYS_AGO.forEachIndexed { i, daysAgo -> logWeek(repository, today.minusDays(daysAgo), upper.id, upperLines, i, UPPER_DAYS_AGO.size, 19, 2) }
        LEGS_DAYS_AGO.forEachIndexed { i, daysAgo -> logWeek(repository, today.minusDays(daysAgo), legs.id, legLines, i, LEGS_DAYS_AGO.size, 18, 30) }

        // A time workout: "Corrida Z2" with a run a week, the latest 6 days ago.
        val run = byName["Corrida"] ?: repository.createExercise("Corrida", kind = ExerciseKind.TIME)
        val cardio = repository.createWorkout("Corrida Z2")
        repository.addItem(
            cardio.id, run.id, sets = 1, minutesMin = 30.0, minutesMax = 40.0, restSec = 0,
            notes = "Ritmo de conversa. Esteira com 1% de inclinação.",
        )
        val runs = listOf(
            Triple(48L, 30.0, null), Triple(41L, 32.0, "rua"), Triple(34L, 30.0, "esteira"),
            Triple(27L, 35.0, "rua"), Triple(20L, 33.0, "esteira"), Triple(13L, 40.0, "rua"), Triple(6L, 35.0, "esteira"),
        )
        runs.forEach { (daysAgo, minutes, place) ->
            val date = today.minusDays(daysAgo)
            val at = date.atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val km = if (place == null) null else if (daysAgo == 6L) 5.2 else minutes * 0.145
            repository.chooseWorkout(date.toString(), cardio.id)
            repository.startSession(date.toString(), at)
            repository.logTime(date.toString(), run.id, minutes, km = km?.let { Math.round(it * 10) / 10.0 }, place = place, loggedAt = at + minutes.toLong() * 60_000)
        }
        repository.setNote(today.minusDays(7).toString(), "Academia cheia, troquei a ordem da remada.")
    }

    private suspend fun addItems(
        repository: TreinoRepository,
        workoutId: String,
        lines: List<Line>,
        byName: Map<String, Exercise>,
    ): List<Pair<Line, Exercise>> = lines.mapNotNull { line -> byName[line.name]?.let { line to it } }.onEach { (line, exercise) ->
        repository.addItem(workoutId, exercise.id, sets = line.sets, repsMin = line.reps.first, repsMax = line.reps.second, restSec = 90)
    }

    /**
     * Week [index] of [count]: the last week has [Line.last], the one before [Line.before], and the
     * older ones go one step down every three weeks.
     */
    private suspend fun logWeek(
        repository: TreinoRepository,
        date: LocalDate,
        workoutId: String,
        lines: List<Pair<Line, Exercise>>,
        index: Int,
        count: Int,
        hour: Int,
        minute: Int,
    ) {
        val imported = index < IMPORTED
        var at = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val day = date.toString()
        repository.chooseWorkout(day, workoutId)
        if (!imported) repository.startSession(day, at)
        val fromEnd = count - 1 - index
        lines.forEach { (line, exercise) ->
            val kg = when (fromEnd) {
                0 -> line.last
                else -> maxOf(0.0, line.before - Steps.of(exercise).kg * ((fromEnd - 1) / 3))
            }
            repeat(line.sets) { set ->
                at += 150_000
                // The last week keeps the same reps in every set (Hoje's prefill reads it).
                val reps = if (fromEnd == 0) line.lastReps else line.lastReps - 1 + (index % 2) - set / 2
                repository.logSet(day, exercise.id, kg, maxOf(1, reps), loggedAt = if (imported) null else at)
            }
        }
        if (!imported) repository.finishSession(day, at + 60_000)
    }
}
