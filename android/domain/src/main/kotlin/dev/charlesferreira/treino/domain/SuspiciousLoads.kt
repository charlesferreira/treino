package dev.charlesferreira.treino.domain

/** Uma sessão sinalizada: a carga parece ser a do par de halteres, não a de um halter. */
data class SuspiciousSession(
    val sessionId: String,
    val date: String,
    /** Maior carga anotada na sessão. */
    val maxKg: Double,
    /** Mediana das cargas máximas das sessões vizinhas. */
    val neighborMedianKg: Double,
    /** As séries como estão. */
    val sets: List<SetEntry>,
    /** As mesmas séries com a carga dividida por 2. Sugestão; nada é gravado aqui. */
    val suggestedSets: List<SetEntry>,
) {
    val ratio: Double get() = maxKg / neighborMedianKg
    val suggestedMaxKg: Double get() = maxKg / 2
}

data class SuspiciousExercise(val exercise: Exercise, val sessions: List<SuspiciousSession>)

/**
 * Revisão das cargas antigas de halteres. Para cada exercício que conta 2 halteres, compara a
 * carga máxima de cada sessão com a mediana das sessões vizinhas do mesmo exercício (até
 * [WINDOW] antes e [WINDOW] depois, sem contar a própria) e sinaliza quando ela é
 * ≥ [RATIO] × a mediana. Precisa de pelo menos [MIN_NEIGHBORS] vizinhas.
 *
 * Só devolve a lista com a sugestão (dividir por 2). Nunca altera nada: quem grava é a tela,
 * depois que a pessoa escolhe.
 */
object SuspiciousLoads {
    const val RATIO = 1.6
    const val WINDOW = 3
    const val MIN_NEIGHBORS = 2

    fun detect(exercises: List<Exercise>, sessions: List<Session>, sets: List<SetEntry>): List<SuspiciousExercise> {
        val setsByExercise = sets.groupBy { it.exerciseId }
        return exercises.filter { it.countsTwoDumbbells }.mapNotNull { ex ->
            val exSets = setsByExercise[ex.id] ?: return@mapNotNull null
            val loads = LoadHistory.of(ex.id, sessions, exSets).loads
            val flagged = loads.indices.mapNotNull { i ->
                val neighbors = (maxOf(0, i - WINDOW) until i).map { loads[it].maxKg } +
                    (i + 1..minOf(loads.lastIndex, i + WINDOW)).map { loads[it].maxKg }
                if (neighbors.size < MIN_NEIGHBORS) return@mapNotNull null
                val median = median(neighbors)
                val load = loads[i]
                if (median <= 0 || load.maxKg < RATIO * median) return@mapNotNull null
                val sessionSets = exSets.filter { it.sessionId == load.sessionId }.sortedBy { it.position }
                SuspiciousSession(
                    sessionId = load.sessionId,
                    date = load.date,
                    maxKg = load.maxKg,
                    neighborMedianKg = median,
                    sets = sessionSets,
                    suggestedSets = sessionSets.map { it.copy(weightKg = it.weightKg / 2) },
                )
            }
            if (flagged.isEmpty()) null else SuspiciousExercise(ex, flagged)
        }
    }

    internal fun median(values: List<Double>): Double {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
