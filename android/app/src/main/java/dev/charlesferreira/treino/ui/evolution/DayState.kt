package dev.charlesferreira.treino.ui.evolution

import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SessionDuration
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Volume
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem

/* Pure state of "Dia do histórico" (`hist-day`). */

/** One exercise of the day: its sets ("18 × 10") or times, with the unit spelled out. */
data class DayExerciseUi(
    val exercise: Exercise,
    /** "kg por halter", "kg"; null for time exercises. */
    val unit: String?,
    val sets: List<SetEntry>,
    val times: List<TimeEntry>,
)

data class DayDetailUi(
    val date: String,
    /** "19 nov, quinta". */
    val title: String,
    val workoutName: String,
    val minutes: Int?,
    val seriesCount: Int,
    /** Null on a day without load sets. */
    val volumeKg: Double?,
    val note: String?,
    val exercises: List<DayExerciseUi>,
)

fun buildDayDetail(
    date: String,
    session: Session?,
    workout: Workout?,
    items: List<WorkoutItem>,
    exercises: Map<String, Exercise>,
    sets: List<SetEntry>,
    times: List<TimeEntry>,
): DayDetailUi {
    val ids = (sets.map { it.exerciseId } + times.map { it.exerciseId }).distinct()
    val itemOrder = items.sortedBy { it.position }.map { it.exerciseId }
    val firstLogged = ids.associateWith { id ->
        (sets.filter { it.exerciseId == id }.mapNotNull { it.loggedAt } + times.filter { it.exerciseId == id }.mapNotNull { it.loggedAt })
            .minOrNull() ?: Long.MAX_VALUE
    }
    // The workout's order, then the ones outside it by the time they were done.
    val ordered = ids.sortedWith(
        compareBy<String>({ itemOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { firstLogged[it] }, { exercises[it]?.name ?: it }),
    )
    val list = ordered.mapNotNull { id ->
        val exercise = exercises[id] ?: return@mapNotNull null
        DayExerciseUi(
            exercise = exercise,
            unit = if (exercise.kind == ExerciseKind.TIME && sets.none { it.exerciseId == id }) null else PtBr.loadUnit(exercise),
            sets = sets.filter { it.exerciseId == id }.sortedBy { it.position },
            times = times.filter { it.exerciseId == id }.sortedBy { it.position },
        )
    }
    val name = workout?.name ?: list.firstOrNull()?.exercise?.name ?: "Treino"
    return DayDetailUi(
        date = date,
        title = PtBr.dayTitle(date),
        workoutName = name,
        minutes = session?.let { SessionDuration.minutes(it, sets, times) },
        seriesCount = sets.size + times.size,
        volumeKg = if (sets.isEmpty() || session == null) null else Volume.ofSession(session.id, sets, exercises),
        note = session?.note,
        exercises = list,
    )
}

/** "18 × 10". */
fun setText(set: SetEntry): String = "${PtBr.number(set.weightKg)} × ${set.reps}"

/** "35 min, 5,2 km, esteira". */
fun timeText(time: TimeEntry): String =
    listOfNotNull("${PtBr.number(time.minutes)} min", time.km?.let { "${PtBr.number(it)} km" }, time.place).joinToString(", ")
