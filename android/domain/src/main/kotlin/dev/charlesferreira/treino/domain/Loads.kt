package dev.charlesferreira.treino.domain

/** Volume = carga × reps, ×2 quando o exercício conta 2 halteres. Só exercícios de carga. */
object Volume {
    fun ofSet(set: SetEntry, exercise: Exercise?): Double =
        set.weightKg * set.reps * (if (exercise?.countsTwoDumbbells == true) 2 else 1)

    /** Séries de um exercício (as de outro exercício são ignoradas). */
    fun ofExercise(sets: List<SetEntry>, exercise: Exercise): Double =
        sets.filter { it.exerciseId == exercise.id }.sumOf { ofSet(it, exercise) }

    /** Todas as séries de uma sessão. Exercício desconhecido conta ×1. */
    fun ofSession(sessionId: String, sets: List<SetEntry>, exercisesById: Map<String, Exercise>): Double =
        sets.filter { it.sessionId == sessionId }.sumOf { ofSet(it, exercisesById[it.exerciseId]) }
}

/** A carga máxima de um exercício numa sessão. */
data class SessionLoad(val sessionId: String, val date: String, val maxKg: Double)

/** "Subiu de carga": a carga máxima da sessão passou a da sessão anterior do mesmo exercício. */
data class LoadIncrease(
    val sessionId: String,
    val date: String,
    val fromKg: Double,
    val toKg: Double,
) {
    val deltaKg: Double get() = toKg - fromKg
}

/**
 * Histórico de carga de um exercício, uma entrada por sessão em que ele foi feito, em ordem de
 * data (empate pelo início da sessão e pelo id, para ser estável).
 */
class LoadHistory(val loads: List<SessionLoad>) {

    val maxKg: Double? get() = loads.maxOfOrNull { it.maxKg }

    /** A carga máxima da sessão mais recente. */
    val latestKg: Double? get() = loads.lastOrNull()?.maxKg

    /** Todas as subidas, em ordem de data. */
    fun increases(): List<LoadIncrease> = loads.zipWithNext { prev, cur ->
        if (cur.maxKg > prev.maxKg) LoadIncrease(cur.sessionId, cur.date, prev.maxKg, cur.maxKg) else null
    }.filterNotNull()

    fun lastIncrease(): LoadIncrease? = increases().lastOrNull()

    /** A subida nesta sessão, se houve (resumo do treino: "14 → 16 kg"). */
    fun increaseIn(sessionId: String): LoadIncrease? = increases().firstOrNull { it.sessionId == sessionId }

    /**
     * "igual desde 3 nov": a data da primeira sessão da sequência final com a mesma carga máxima.
     * Nulo sem histórico.
     */
    fun stableSince(): String? {
        if (loads.isEmpty()) return null
        val latest = loads.last().maxKg
        var i = loads.lastIndex
        while (i > 0 && loads[i - 1].maxKg == latest) i--
        return loads[i].date
    }

    /** "+4 kg em 7 sem": carga mais recente menos a da primeira sessão a partir de [fromDate]. */
    fun changeSince(fromDate: String): Double? {
        val first = loads.firstOrNull { it.date >= fromDate } ?: return null
        return loads.last().maxKg - first.maxKg
    }

    companion object {
        fun of(exerciseId: String, sessions: List<Session>, sets: List<SetEntry>): LoadHistory {
            val byId = sessions.associateBy { it.id }
            val loads = sets.asSequence()
                .filter { it.exerciseId == exerciseId }
                .groupBy { it.sessionId }
                .mapNotNull { (sid, s) ->
                    val session = byId[sid] ?: return@mapNotNull null
                    session to SessionLoad(sid, session.date, s.maxOf { it.weightKg })
                }
                .sortedWith(compareBy({ it.first.date }, { it.first.startedAt ?: 0L }, { it.first.id }))
                .map { it.second }
            return LoadHistory(loads)
        }
    }
}

/** Melhor série: a maior carga, e nela o maior número de reps ("16 × 11"). */
fun bestSet(sets: List<SetEntry>): SetEntry? =
    sets.maxWithOrNull(compareBy<SetEntry> { it.weightKg }.thenBy { it.reps })

/** Carga e reps sugeridas para a próxima série. Nulo nos dois campos = campo vazio. */
data class SetPrefill(val weightKg: Double, val reps: Int)

object NextSet {
    /**
     * A série [position] (a partir de 0) de [exerciseId] vem com a carga e as reps da mesma série
     * da última sessão em que o exercício foi feito; se aquela sessão teve menos séries, da última
     * dela; se o exercício nunca foi feito, nulo (campos vazios).
     *
     * [currentSessionId] é a sessão em andamento, que não conta como "última".
     */
    fun prefill(
        exerciseId: String,
        position: Int,
        sessions: List<Session>,
        sets: List<SetEntry>,
        currentSessionId: String? = null,
    ): SetPrefill? {
        val byId = sessions.associateBy { it.id }
        val last = sets.asSequence()
            .filter { it.exerciseId == exerciseId && it.sessionId != currentSessionId }
            .groupBy { it.sessionId }
            .mapNotNull { (sid, s) -> byId[sid]?.let { it to s } }
            .maxWithOrNull(compareBy({ it.first.date }, { it.first.startedAt ?: 0L }, { it.first.id }))
            ?.second
            ?: return null
        val ordered = last.sortedBy { it.position }
        val match = ordered.firstOrNull { it.position == position } ?: ordered.last()
        return SetPrefill(match.weightKg, match.reps)
    }
}
