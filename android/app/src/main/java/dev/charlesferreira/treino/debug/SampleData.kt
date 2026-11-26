package dev.charlesferreira.treino.debug

import dev.charlesferreira.treino.data.TreinoRepository
import kotlinx.coroutines.flow.first

/** Debug only: the design's "Superior B", from catalog exercises, to look at the screens with data. */
object SampleData {
    private val SUPERIOR_B = listOf(
        Triple("Desenvolvimento com halteres", 3, 8 to 12),
        Triple("Puxada supinada na polia", 3, 8 to 12),
        Triple("Remada baixa no triângulo", 3, 10 to 12),
        Triple("Crucifixo na máquina (peck deck)", 2, 12 to 15),
        Triple("Rosca martelo", 2, 10 to 12),
        Triple("Tríceps na polia com corda", 2, 10 to 15),
        Triple("Elevação lateral com halteres", 2, 12 to 15),
    )

    suspend fun createSuperiorB(repository: TreinoRepository) {
        repository.seedCatalog()
        val byName = repository.observeExercises().first().associateBy { it.name }
        val workout = repository.createWorkout("Superior B")
        SUPERIOR_B.forEach { (name, sets, reps) ->
            val exercise = byName[name] ?: return@forEach
            repository.addItem(workout.id, exercise.id, sets = sets, repsMin = reps.first, repsMax = reps.second, restSec = 90)
        }
    }
}
