package dev.charlesferreira.treino.debug

import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.ExerciseKind
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug only: the design's "Superior B", from catalog exercises, to look at the screens with data.
 * Also two past sessions of it (14 and 7 days ago) so Hoje has a last session, a max and a load
 * increase to show, and a "Corrida Z2" time workout with one past run.
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

    suspend fun createSuperiorB(repository: TreinoRepository) {
        repository.seedCatalog()
        val byName = repository.observeExercises().first().associateBy { it.name }
        val workout = repository.createWorkout("Superior B")
        val lines = SUPERIOR_B.mapNotNull { line -> byName[line.name]?.let { line to it } }
        lines.forEach { (line, exercise) ->
            repository.addItem(workout.id, exercise.id, sets = line.sets, repsMin = line.reps.first, repsMax = line.reps.second, restSec = 90)
        }

        val today = LocalDate.parse(repository.today())
        listOf(14L to false, 7L to true).forEach { (daysAgo, last) ->
            val date = today.minusDays(daysAgo)
            var at = date.atTime(19, 2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.chooseWorkout(date.toString(), workout.id)
            repository.startSession(date.toString(), at)
            lines.forEach { (line, exercise) ->
                repeat(line.sets) {
                    at += 150_000
                    val kg = if (last) line.last else line.before
                    val reps = if (last) line.lastReps else line.lastReps - 1
                    repository.logSet(date.toString(), exercise.id, kg, reps, loggedAt = at)
                }
            }
            repository.finishSession(date.toString(), at + 60_000)
        }

        // A time workout: "Corrida Z2" with one run 6 days ago.
        val run = byName["Corrida"] ?: repository.createExercise("Corrida", kind = ExerciseKind.TIME)
        val cardio = repository.createWorkout("Corrida Z2")
        repository.addItem(
            cardio.id, run.id, sets = 1, minutesMin = 30.0, minutesMax = 40.0, restSec = 0,
            notes = "Ritmo de conversa. Esteira com 1% de inclinação.",
        )
        val runDate = today.minusDays(6).toString()
        val runAt = today.minusDays(6).atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.chooseWorkout(runDate, cardio.id)
        repository.startSession(runDate, runAt)
        repository.logTime(runDate, run.id, 35.0, km = 5.2, place = "esteira", loggedAt = runAt + 35 * 60_000)
    }
}
