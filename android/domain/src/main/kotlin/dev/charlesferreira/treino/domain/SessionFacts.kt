package dev.charlesferreira.treino.domain

object SessionDuration {
    /**
     * Duração em ms: do início ao fim da sessão; o que faltar vem do horário da primeira e da
     * última série/tempo registrados. Nulo quando não dá para saber (dias importados do PWA, que
     * não têm horário, ou um único registro sem início/fim).
     */
    fun of(session: Session, sets: List<SetEntry>, times: List<TimeEntry>): Long? {
        val stamps = sets.filter { it.sessionId == session.id }.mapNotNull { it.loggedAt } +
            times.filter { it.sessionId == session.id }.mapNotNull { it.loggedAt }
        val start = session.startedAt ?: stamps.minOrNull() ?: return null
        val end = session.endedAt ?: stamps.maxOrNull() ?: return null
        return (end - start).takeIf { it > 0 }
    }

    /** Em minutos inteiros, arredondado ("58 min"). */
    fun minutes(session: Session, sets: List<SetEntry>, times: List<TimeEntry>): Int? =
        of(session, sets, times)?.let { ((it + 30_000) / 60_000).toInt() }
}

/** Um segmento da barra de progresso: um item do treino, com peso proporcional às séries. */
data class ProgressSegment(val itemId: String, val exerciseId: String, val total: Int, val done: Int) {
    val complete: Boolean get() = done >= total
}

/** "9/16" e os segmentos da barra segmentada. */
data class WorkoutProgress(val done: Int, val total: Int, val segments: List<ProgressSegment>) {
    /** Fração da largura de cada segmento (soma 1). */
    val weights: List<Double> get() = segments.map { if (total == 0) 0.0 else it.total.toDouble() / total }

    companion object {
        /**
         * [items] são os itens do treino da sessão. Cada série ou tempo da sessão conta para o item
         * do seu exercício, até o número de séries do item; se o exercício aparece em dois itens,
         * enche o primeiro antes do segundo. Registros de exercícios fora do treino não entram.
         */
        fun of(items: List<WorkoutItem>, sessionId: String, sets: List<SetEntry>, times: List<TimeEntry>): WorkoutProgress {
            val logged = (sets.filter { it.sessionId == sessionId }.map { it.exerciseId } +
                times.filter { it.sessionId == sessionId }.map { it.exerciseId })
                .groupingBy { it }.eachCount().toMutableMap()
            val segments = items.sortedBy { it.position }.map { item ->
                val available = logged[item.exerciseId] ?: 0
                val done = minOf(available, item.sets)
                logged[item.exerciseId] = available - done
                ProgressSegment(item.id, item.exerciseId, item.sets, done)
            }
            return WorkoutProgress(segments.sumOf { it.done }, segments.sumOf { it.total }, segments)
        }
    }
}

object WorkoutMode {
    /**
     * Treino em andamento: começou (início gravado ou algum registro) e não terminou. Enquanto
     * for verdade, o app inteiro é esse treino, sem a barra de abas.
     */
    fun running(session: Session?, hasEntries: Boolean): Boolean =
        session != null && session.endedAt == null && (session.startedAt != null || hasEntries)

    /** Três horas sem registro: o treino provavelmente acabou e ninguém tocou em Terminar. */
    const val STALE_MS = 3 * 60 * 60 * 1000L

    /** O último sinal de vida do treino: a série ou o tempo mais recente, senão o início. */
    fun lastActivity(session: Session, sets: List<SetEntry>, times: List<TimeEntry>): Long? =
        (sets.filter { it.sessionId == session.id }.mapNotNull { it.loggedAt } +
            times.filter { it.sessionId == session.id }.mapNotNull { it.loggedAt })
            .maxOrNull() ?: session.startedAt

    /** Em andamento e parado há mais de [STALE_MS]: o app pergunta se ainda está treinando. */
    fun stale(session: Session, sets: List<SetEntry>, times: List<TimeEntry>, now: Long): Boolean {
        if (session.endedAt != null) return false
        val last = lastActivity(session, sets, times) ?: return false
        return now - last > STALE_MS
    }
}
