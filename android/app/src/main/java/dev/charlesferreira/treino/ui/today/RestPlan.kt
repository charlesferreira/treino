package dev.charlesferreira.treino.ui.today

import dev.charlesferreira.treino.domain.PtBr
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.timer.RestRequest
import dev.charlesferreira.treino.timer.WorkoutOngoing
import dev.charlesferreira.treino.timer.WorkoutProgress as TimerProgress

/*
 * Pure rules that connect today's workout to the rest timer (Phase 4). The app and the
 * notification receiver both build a [TodayUi] from Room and call these, so what the notification
 * shows is what "Registrar série" logs.
 */

/** The set that comes next. [draft] is the prefilled load and reps; null for a time exercise. */
data class NextUp(val item: ItemUi, val position: Int, val draft: SetDraft?) {
    val setNumber: Int get() = position + 1

    /** "16 kg × 10" (notification), or the time target ("30–40 min"). */
    val target: String get() = draft?.let {
        if (item.exercise.bodyweight) "${it.reps} reps" else "${PtBr.number(it.weightKg)} kg × ${it.reps}"
    } ?: item.target

    /** "16 × 10" (the app's rest screens); "12 reps" with no load. */
    val short: String get() = draft?.let {
        if (item.exercise.bodyweight) "${it.reps} reps" else "${PtBr.number(it.weightKg)} × ${it.reps}"
    } ?: item.target
}

/**
 * The next set ([followUp]): the exercise of the set logged last, while it has sets left, else
 * the first exercise not complete, in workout order. Null when everything is done. The workout
 * card follows the same rule when nothing is picked.
 */
fun TodayUi.nextUp(): NextUp? {
    val item = followUp(items) ?: return null
    return NextUp(item, item.nextPosition(), if (item.isTime) null else item.defaultSetDraft(session?.id))
}

/** Sets per exercise and sets done, for the notification's segments. */
fun TodayUi.timerProgress(): TimerProgress {
    val plan = items.map { it.total }.filter { it > 0 }
    return TimerProgress(plan, progress.done.coerceIn(0, plan.sum()))
}

/**
 * The rest after [logged] was written, from [today] read again after the write. Null (no rest)
 * when the item has no rest or when it was the last set of the workout.
 */
fun restAfterLog(today: TodayUi, loggedItemId: String, logged: SetEntry): RestRequest? {
    val loggedUi = today.items.firstOrNull { it.item.id == loggedItemId } ?: return null
    if (loggedUi.item.restSec <= 0) return null
    val next = today.nextUp() ?: return null
    val number = logged.position + 1
    return RestRequest(
        durationMs = loggedUi.item.restSec * 1000L,
        workoutName = today.workout?.name.orEmpty(),
        exerciseName = next.item.exercise.name,
        setNumber = next.setNumber,
        setCount = next.item.total,
        target = next.target,
        progress = today.timerProgress(),
        nextShort = next.short,
        loggedLabel = if (next.item.item.id == loggedItemId) "Série $number" else "${loggedUi.exercise.name}, série $number",
        loggedValue = setText(logged),
    )
}

/**
 * The between-sets notification of a workout in progress, or null when there is none (not
 * started, finished, or no workout). [nowMs] stands in for a start that was never recorded.
 */
fun betweenSets(today: TodayUi, nowMs: Long): WorkoutOngoing? {
    val workout = today.workout ?: return null
    if (!today.started || today.ended) return null
    val startedAt = today.session?.startedAt
        ?: today.items.flatMap { ui -> ui.sets.mapNotNull { it.loggedAt } + ui.times.mapNotNull { it.loggedAt } }.minOrNull()
        ?: nowMs
    val next = today.nextUp()
    val text = when {
        next == null -> "Todas as séries feitas"
        next.draft == null -> "A seguir: ${next.item.exercise.name}, ${next.item.target}"
        else -> "A seguir: ${next.item.exercise.name}, série ${next.setNumber} de ${next.item.total}, ${next.short}"
    }
    return WorkoutOngoing(
        workoutName = workout.name,
        startedAtMs = startedAt,
        progress = today.timerProgress(),
        next = text,
        canLog = next?.draft != null,
    )
}
