package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.data.DaySummary
import dev.charlesferreira.treino.data.ExerciseDay
import dev.charlesferreira.treino.domain.Exercise
import dev.charlesferreira.treino.domain.ExerciseKind
import dev.charlesferreira.treino.domain.NextSet
import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.Session
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.domain.Steps
import dev.charlesferreira.treino.domain.TimeEntry
import dev.charlesferreira.treino.domain.Workout
import dev.charlesferreira.treino.domain.WorkoutItem
import dev.charlesferreira.treino.domain.WorkoutProgress
import dev.charlesferreira.treino.ui.screens.target
import dev.charlesferreira.treino.ui.screens.weekdayLabel

/*
 * Pure state of the Hoje tab: everything the screens show is computed here from what the
 * repository reads, so it can be tested without Android.
 */

/** One item of today's workout, with today's log and the exercise's history. */
data class ItemUi(
    val item: WorkoutItem,
    val exercise: Exercise,
    /** 1-based, the order in the workout. */
    val number: Int,
    /** "3 × 8–12", "30–40 min". */
    val target: String,
    val done: Int,
    val total: Int,
    /** Today's sets of the exercise, by position. */
    val sets: List<SetEntry>,
    val times: List<TimeEntry>,
    /** The last session before today in which the exercise was done. */
    val lastDate: String?,
    val lastSets: List<SetEntry>,
    val lastTimes: List<TimeEntry>,
    /** The heaviest set ever, today included. */
    val maxKg: Double?,
    /** Every day the exercise was done, oldest first (today included). */
    val history: List<ExerciseDay>,
) {
    val complete: Boolean get() = done >= total
    val isTime: Boolean get() = exercise.kind == ExerciseKind.TIME
}

/** A row of "Trocar o treino". */
data class SwitchOption(val workout: Workout, val selected: Boolean, val subtitle: String?)

data class TodayUi(
    val date: String,
    /** "Quinta, 26 nov". */
    val dateLabel: String,
    val profileName: String,
    val keepScreenOn: Boolean,
    val workout: Workout?,
    val session: Session?,
    val items: List<ItemUi>,
    val progress: WorkoutProgress,
    /** The workout's last executed day before today ("último em 19 nov"). */
    val lastDoneDate: String?,
    val switchOptions: List<SwitchOption>,
    /** The exercise card: the one picked, else [followUp]. Null when all are done. */
    val current: ItemUi?,
) {
    /** In progress: started, or something was already logged today. */
    val started: Boolean get() = session?.startedAt != null || items.any { it.sets.isNotEmpty() || it.times.isNotEmpty() }
    val ended: Boolean get() = session?.endedAt != null
    val setCount: Int get() = items.sumOf { it.total }
}

/** Everything [buildToday] reads. */
data class TodayInput(
    val date: String,
    val profileName: String,
    val keepScreenOn: Boolean,
    val workouts: List<Workout>,
    val workout: Workout?,
    val nextInSequence: Workout?,
    val session: Session?,
    val items: List<WorkoutItem>,
    val allItems: List<WorkoutItem>,
    val exercises: Map<String, Exercise>,
    val sets: List<SetEntry>,
    val times: List<TimeEntry>,
    val histories: Map<String, List<ExerciseDay>>,
    val days: List<DaySummary>,
    val selectedItemId: String?,
)

fun buildToday(input: TodayInput): TodayUi {
    val sessionId = input.session?.id
    val progress = WorkoutProgress.of(input.items, sessionId ?: "", input.sets, input.times)
    val doneByItem = progress.segments.associate { it.itemId to it.done }
    val items = input.items.sortedBy { it.position }.mapIndexedNotNull { i, item ->
        val exercise = input.exercises[item.exerciseId] ?: return@mapIndexedNotNull null
        val history = input.histories[item.exerciseId].orEmpty()
        val last = history.lastOrNull { it.sessionId != sessionId && it.date <= input.date }
        val allSets = history.flatMap { it.sets } + input.sets.filter { it.exerciseId == exercise.id }
        ItemUi(
            item = item,
            exercise = exercise,
            number = i + 1,
            target = itemTarget(item, exercise),
            done = doneByItem[item.id] ?: 0,
            total = item.sets,
            sets = input.sets.filter { it.exerciseId == exercise.id }.sortedBy { it.position },
            times = input.times.filter { it.exerciseId == exercise.id }.sortedBy { it.position },
            lastDate = last?.date,
            lastSets = last?.sets.orEmpty(),
            lastTimes = last?.times.orEmpty(),
            maxKg = allSets.maxOfOrNull { it.weightKg },
            history = history,
        )
    }
    val selected = items.firstOrNull { it.item.id == input.selectedItemId }
    val executed = input.days.filter { it.setCount + it.timeCount > 0 && it.session.date < input.date }
    val lastDoneByWorkout = executed.filter { it.session.workoutId != null }
        .groupBy { it.session.workoutId!! }
        .mapValues { (_, d) -> d.maxOf { it.session.date } }
    return TodayUi(
        date = input.date,
        dateLabel = weekdayLabel(input.date),
        profileName = input.profileName,
        keepScreenOn = input.keepScreenOn,
        workout = input.workout,
        session = input.session,
        items = items,
        progress = progress,
        lastDoneDate = input.workout?.let { lastDoneByWorkout[it.id] },
        switchOptions = input.workouts.sortedBy { it.position }.map { w ->
            SwitchOption(
                workout = w,
                selected = w.id == input.workout?.id,
                subtitle = switchSubtitle(
                    isNext = w.id == input.nextInSequence?.id,
                    lastDone = lastDoneByWorkout[w.id],
                    items = input.allItems.filter { it.workoutId == w.id },
                    exercises = input.exercises,
                ),
            )
        },
        current = selected ?: followUp(items),
    )
}

/**
 * The exercise that comes next when none is picked: the one of the set logged last while it has
 * sets left, else the first not complete. The rest and the notification use the same rule
 * ([nextUp]), so the card, the rest and "Registrar série" agree.
 */
fun followUp(items: List<ItemUi>): ItemUi? {
    val lastLogged = items
        .flatMap { ui -> ui.sets.map { ui to (it.loggedAt ?: 0L) } + ui.times.map { ui to (it.loggedAt ?: 0L) } }
        .maxByOrNull { it.second }
        ?.first
    return lastLogged?.takeIf { !it.complete } ?: items.firstOrNull { !it.complete }
}

/** "3 × 8–12"; a time item done once shows only the range ("30–40 min"). */
fun itemTarget(item: WorkoutItem, exercise: Exercise?): String {
    val text = target(item, exercise)
    return if (exercise?.kind == ExerciseKind.TIME && item.sets == 1) text.removePrefix("1 × ") else text
}

/**
 * The subtitle of a workout in "Trocar o treino": "próximo da sequência", else "feito em 24 nov",
 * else, for a workout made only of time exercises, "tempo, 30–40 min".
 */
fun switchSubtitle(isNext: Boolean, lastDone: String?, items: List<WorkoutItem>, exercises: Map<String, Exercise>): String? {
    if (isNext) return "próximo da sequência"
    if (lastDone != null) return "feito em ${PtBr.shortDate(lastDone)}"
    val allTime = items.isNotEmpty() && items.all { exercises[it.exerciseId]?.kind == ExerciseKind.TIME }
    if (allTime) {
        val first = items.minBy { it.position }
        return "tempo, " + itemTarget(first, exercises[first.exerciseId]).removePrefix("${first.sets} × ")
    }
    return null
}

/* ------------------------------------------------------------------ Drafts */

/** Which row the steppers edit: the next set (or time) of an item. */
data class DraftKey(val itemId: String, val position: Int)

data class SetDraft(val weightKg: Double, val reps: Int)

data class TimeDraft(val minutes: Double, val km: String, val place: String?)

/** The position of the next set to log. */
fun ItemUi.nextPosition(): Int = if (isTime) times.size else sets.size

fun ItemUi.draftKey(): DraftKey = DraftKey(item.id, nextPosition())

/** The same set of the last session ([NextSet.prefill]); null when the exercise was never done. */
fun ItemUi.prefill(position: Int, sessionId: String?): SetDraft? =
    NextSet.prefill(
        exerciseId = exercise.id,
        position = position,
        sessions = history.map { Session(id = it.sessionId, date = it.date) },
        sets = history.flatMap { it.sets },
        currentSessionId = sessionId,
    )?.let { SetDraft(it.weightKg, it.reps) }

/**
 * What the steppers start with: the prefill; for an exercise never done, today's previous set, or
 * an empty load with the bottom of the rep range.
 */
fun ItemUi.defaultSetDraft(sessionId: String?): SetDraft {
    val position = nextPosition()
    return prefill(position, sessionId)
        ?: sets.lastOrNull()?.let { SetDraft(it.weightKg, it.reps) }
        ?: SetDraft(0.0, item.repsMin ?: item.repsMax ?: DEFAULT_REPS)
}

/** A time exercise starts with the last session's entry, else the bottom of the minutes range. */
fun ItemUi.defaultTimeDraft(): TimeDraft {
    val position = nextPosition()
    val last = lastTimes.sortedBy { it.position }.let { l -> l.firstOrNull { it.position == position } ?: l.lastOrNull() }
        ?: times.lastOrNull()
    return if (last != null) {
        TimeDraft(last.minutes, last.km?.let(PtBr::number).orEmpty(), last.place)
    } else {
        TimeDraft(item.minutesMin ?: item.minutesMax ?: DEFAULT_MINUTES, "", null)
    }
}

const val DEFAULT_REPS = 10
const val DEFAULT_MINUTES = 30.0
const val MINUTES_STEP = 5.0

/* ----------------------------------------------------------------- Labels */

/** The load unit over the table: "kg/halter" when the load is per dumbbell. */
fun unitLabel(exercise: Exercise): String = if (exercise.countsTwoDumbbells) "kg/halter" else "kg"

/** The stepper's visible step: "2", "2,5", or "1 placa" on a machine. */
fun stepLabel(exercise: Exercise): String {
    val step = Steps.of(exercise)
    return if (step.plates != null) "${step.plates} ${if (step.plates == 1) "placa" else "placas"}" else PtBr.number(step.kg)
}

/** The stepper buttons' step: the number only ("2", "2,5", "1"); plates get [stepCaption]. */
fun stepButton(exercise: Exercise): String {
    val step = Steps.of(exercise)
    return step.plates?.toString() ?: PtBr.number(step.kg)
}

/**
 * Under the stepper's value on a machine: "±1 placa". "−1 placa" on each button left the value
 * no room ("20…" on the S23).
 */
fun stepCaption(exercise: Exercise): String? =
    if (Steps.of(exercise).plates != null) "±${stepLabel(exercise)}" else null

/** TalkBack: "2 kg", "1 placa". */
fun stepDescription(exercise: Exercise): String {
    val step = Steps.of(exercise)
    return if (step.plates != null) stepLabel(exercise) else PtBr.kg(step.kg)
}

/** "14 × 12". */
fun setText(set: SetEntry): String = "${PtBr.number(set.weightKg)} × ${set.reps}"

/** "35 min 5,2 km esteira". */
fun timeText(time: TimeEntry): String = listOfNotNull(
    "${PtBr.number(time.minutes)} min",
    time.km?.let { "${PtBr.number(it)} km" },
    time.place,
).joinToString(" ")

/** "1:30". */
fun restLabel(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** "34:12", "1:02:09": the workout's clock. */
fun clockLabel(elapsedMs: Long): String {
    val total = (elapsedMs.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "${m.toString().padStart(2, '0')}:$ss"
}

/** "18 kg" or "35 min": the right side of a row in the start list (last session) or the done card (today). */
fun loggedSummary(sets: List<SetEntry>, times: List<TimeEntry>): String? = when {
    sets.isNotEmpty() -> PtBr.kg(sets.maxOf { it.weightKg })
    times.isNotEmpty() -> "${PtBr.number(times.sumOf { it.minutes })} min"
    else -> null
}

/** Parses "14", "14,5" or "14.5"; null when it is not a number. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

/** Places offered as chips in a time exercise; stored in lower case. */
val PLACES = listOf("esteira", "rua")
