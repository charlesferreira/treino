package dev.charlesferreira.treino.domain

/**
 * Sequência circular dos treinos, porte de `lastExecuted` / `nextInSequence` / `suggestWorkout`
 * do `src/plan.ts`. A ordem é `Workout.position`. Uma sessão só "conta" como executada quando
 * tem pelo menos uma série ou um tempo.
 */
object WorkoutSequence {

    fun executedSessionIds(sets: List<SetEntry>, times: List<TimeEntry>): Set<String> =
        buildSet {
            sets.forEach { add(it.sessionId) }
            times.forEach { add(it.sessionId) }
        }

    /** A sessão executada mais recente antes de [date]: a âncora da rotação. */
    fun lastExecuted(sessions: List<Session>, executed: Set<String>, date: String): Session? =
        sessions.filter { it.date < date && it.id in executed }
            .maxWithOrNull(compareBy({ it.date }, { it.startedAt ?: 0L }, { it.id }))

    /** O seguinte ao último executado. Ignora a escolha manual de hoje. */
    fun nextInSequence(
        workouts: List<Workout>,
        sessions: List<Session>,
        executed: Set<String>,
        date: String,
    ): Workout? {
        val ordered = workouts.sortedBy { it.position }
        if (ordered.isEmpty()) return null
        val anchor = lastExecuted(sessions, executed, date) ?: return ordered.first()
        val i = ordered.indexOfFirst { it.id == anchor.workoutId }
        if (i < 0) return ordered.first()
        return ordered[(i + 1) % ordered.size]
    }

    /** O treino de [date]: o escolhido/registrado no dia, ou o próximo da fila. */
    fun suggestWorkout(
        workouts: List<Workout>,
        sessions: List<Session>,
        executed: Set<String>,
        date: String,
    ): Workout? {
        val chosen = sessions.filter { it.date == date && it.workoutId != null }
            .maxWithOrNull(compareBy({ it.startedAt ?: 0L }, { it.id }))
            ?.workoutId
        if (chosen != null) workouts.firstOrNull { it.id == chosen }?.let { return it }
        return nextInSequence(workouts, sessions, executed, date)
    }

    fun suggestWorkout(
        workouts: List<Workout>,
        sessions: List<Session>,
        sets: List<SetEntry>,
        times: List<TimeEntry>,
        date: String,
    ): Workout? = suggestWorkout(workouts, sessions, executedSessionIds(sets, times), date)

    /**
     * O "próximo" da tela Treinos: o treino que ainda falta fazer. Enquanto hoje não tem registro,
     * é o que o Hoje mostra ([suggestWorkout]); depois do primeiro registro de hoje, é o seguinte
     * ao de hoje na sequência.
     */
    fun upcoming(
        workouts: List<Workout>,
        sessions: List<Session>,
        executed: Set<String>,
        today: String,
    ): Workout? {
        val doneToday = sessions.any { it.date == today && it.id in executed }
        if (!doneToday) return suggestWorkout(workouts, sessions, executed, today)
        val tomorrow = java.time.LocalDate.parse(today).plusDays(1).toString()
        return nextInSequence(workouts, sessions, executed, tomorrow)
    }
}
