package dev.charlesferreira.treino.ui.evolution

import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Equipment
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.LoadHistory
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.Volume
import dev.charlesferreira.treino.domain.bestSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/* Pure state of "Progressão do exercício" (`b-exercise`). */

/** The chart's three views. */
enum class Metric(val label: String) {
    /** Top load of each session. */
    MAX("Carga máx"),

    /** Load × reps of the session (×2 with two dumbbells). */
    VOLUME("Volume"),

    /** The best set of the session (top load, then most reps), as load × reps of that one set. */
    BEST("Melhor série"),
}

/** One session of the exercise, oldest first. */
data class ExerciseSession(
    val sessionId: String,
    val date: String,
    val sets: List<SetEntry>,
    val maxKg: Double,
    val volumeKg: Double,
    val best: SetEntry,
    /** The top load went up against the previous session. */
    val up: Boolean,
) {
    fun value(metric: Metric): Double = when (metric) {
        Metric.MAX -> maxKg
        Metric.VOLUME -> volumeKg
        Metric.BEST -> best.weightKg * best.reps
    }
}

/** A highlighted set: "16 × 11" on 26 nov. */
data class DatedSets(val text: String, val date: String)

data class ProgressUi(
    val exercise: Exercise,
    /** "Halteres 2×", "Máquina"...; null for "other". */
    val equipmentTag: String?,
    /** "Carga máxima por halter" or "Carga máxima". */
    val maxLabel: String,
    val maxKg: Double?,
    /** "+4 kg em 7 sem" over the chart's sessions; null with fewer than two. */
    val change: String?,
    val changeUp: Boolean,
    val sessions: List<ExerciseSession>,
    val best: DatedSets?,
    /** "Mais reps com 14": the label's load and the sets. */
    val mostRepsKg: Double?,
    val mostReps: DatedSets?,
) {
    /** The chart shows the last [CHART_SESSIONS]. */
    val chartSessions: List<ExerciseSession> get() = sessions.takeLast(CHART_SESSIONS)

    companion object {
        const val CHART_SESSIONS = 8
    }
}

fun equipmentTag(exercise: Exercise): String? = when (exercise.equipment) {
    Equipment.DUMBBELL -> if (exercise.countsTwoDumbbells) "Halteres 2×" else "Halter"
    Equipment.BARBELL -> "Barra"
    Equipment.MACHINE -> "Máquina"
    Equipment.CABLE -> "Polia"
    Equipment.BODYWEIGHT -> "Peso corporal"
    Equipment.OTHER -> null
}

fun buildProgress(exercise: Exercise, days: List<ExerciseDay>): ProgressUi {
    val withSets = days.filter { it.sets.isNotEmpty() }
    val history = LoadHistory.of(exercise.id, withSets.map { Session(id = it.sessionId, date = it.date) }, withSets.flatMap { it.sets })
    val ups = history.increases().map { it.sessionId }.toSet()
    val byId = withSets.associateBy { it.sessionId }
    val sessions = history.loads.map { load ->
        val sets = byId.getValue(load.sessionId).sets
        ExerciseSession(
            sessionId = load.sessionId,
            date = load.date,
            sets = sets,
            maxKg = load.maxKg,
            volumeKg = Volume.ofExercise(sets, exercise),
            best = bestSet(sets)!!,
            up = load.sessionId in ups,
        )
    }

    val window = sessions.takeLast(ProgressUi.CHART_SESSIONS)
    var change: String? = null
    var changeUp = false
    if (window.size >= 2) {
        val delta = window.last().maxKg - window.first().maxKg
        val days = ChronoUnit.DAYS.between(LocalDate.parse(window.first().date), LocalDate.parse(window.last().date))
        val span = if (days < 7) "$days ${if (days == 1L) "dia" else "dias"}" else "${(days / 7.0).roundToInt()} sem"
        change = if (delta == 0.0) "igual em $span" else "${PtBr.loadDelta(delta, exercise)} em $span"
        changeUp = delta > 0
    }

    val allSets = sessions.flatMap { it.sets }
    val best = bestSet(allSets)?.let { b -> DatedSets(setText(b), sessions.first { b in it.sets }.date) }

    // "Mais reps com 14": the load just under the top one (the top one if it never changed), and
    // the session with the most reps at it.
    val levels = sessions.map { it.maxKg }.distinct().sortedDescending()
    val level = levels.getOrNull(1) ?: levels.firstOrNull()
    val mostReps = level?.let { kg ->
        sessions.map { s -> s to s.sets.filter { it.weightKg == kg } }
            .filter { it.second.isNotEmpty() }
            .maxWithOrNull(compareBy<Pair<ExerciseSession, List<SetEntry>>> { p -> p.second.sumOf { it.reps } }.thenBy { it.first.date })
            ?.let { (s, sets) -> DatedSets("${PtBr.number(kg)} × ${sets.joinToString(", ") { "${it.reps}" }}", s.date) }
    }

    return ProgressUi(
        exercise = exercise,
        equipmentTag = equipmentTag(exercise),
        maxLabel = if (exercise.countsTwoDumbbells) "Carga máxima por halter" else "Carga máxima",
        maxKg = history.maxKg,
        change = change,
        changeUp = changeUp,
        sessions = sessions,
        best = best,
        mostRepsKg = level,
        mostReps = mostReps,
    )
}

/** "16 × 11, 16 × 10". */
fun sessionSetsText(sets: List<SetEntry>): String = sets.sortedBy { it.position }.joinToString(", ") { setText(it) }
