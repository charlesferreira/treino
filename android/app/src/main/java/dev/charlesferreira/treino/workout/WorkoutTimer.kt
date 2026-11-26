package dev.charlesferreira.treino.workout

import android.content.Context
import android.util.Log
import dev.charlesferreira.treino.data.TreinoRepository
import dev.charlesferreira.treino.domain.SetEntry
import dev.charlesferreira.treino.timer.RestLog
import dev.charlesferreira.treino.timer.RestNotifications
import dev.charlesferreira.treino.timer.RestTimer
import dev.charlesferreira.treino.ui.today.TodayInput
import dev.charlesferreira.treino.ui.today.TodayUi
import dev.charlesferreira.treino.ui.today.betweenSets
import dev.charlesferreira.treino.ui.today.buildToday
import dev.charlesferreira.treino.ui.today.nextUp
import dev.charlesferreira.treino.ui.today.restAfterLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Connects today's workout (Room) to the rest timer: starts the rest after a set, logs the next
 * set from the notification, and keeps the between-sets notification in step. Works from a
 * receiver in a fresh process: everything is read again from Room and the timer's store.
 */
class WorkoutTimer(private val app: Context, private val repository: TreinoRepository) {

    /** One "Registrar série" at a time, so a double tap does not log two sets at once. */
    private val logLock = Mutex()

    /** Today as the Hoje tab sees it, read once. */
    suspend fun snapshot(date: String = repository.today()): TodayUi {
        val workout = repository.observeWorkoutFor(date).first()
        val session = repository.observeSession(date).first()
        val items = workout?.let { repository.observeItems(it.id).first() }.orEmpty()
        val settings = repository.settings.current()
        return buildToday(
            TodayInput(
                date = date,
                profileName = settings.profileName,
                keepScreenOn = settings.keepScreenOn,
                workouts = repository.observeWorkouts().first(),
                workout = workout,
                nextInSequence = repository.observeNextInSequence(date).first(),
                session = session,
                items = items,
                allItems = repository.observeAllItems().first(),
                exercises = repository.observeExercises().first().associateBy { it.id },
                sets = session?.let { repository.observeSets(it.id).first() }.orEmpty(),
                times = session?.let { repository.observeTimes(it.id).first() }.orEmpty(),
                histories = items.map { it.exerciseId }.distinct()
                    .associateWith { repository.observeExerciseHistory(it).first() },
                days = repository.observeDays().first(),
                selectedItemId = null,
            ),
        )
    }

    /**
     * After [set] of item [itemId] was written: starts its rest, or, on the last set of the
     * workout (or an item without rest), ends a rest still on screen.
     */
    suspend fun afterSetLogged(date: String, itemId: String, set: SetEntry) {
        val request = restAfterLog(snapshot(date), itemId, set)
        if (request != null) {
            RestTimer.start(app, request)
            Log.i(RestLog.TAG, "rest after set: ${request.durationMs} ms, next ${request.exerciseName} ${request.target}")
        } else {
            if (RestTimer.state(app) != null) RestTimer.skip(app)
            Log.i(RestLog.TAG, "no rest after this set")
            refreshNotification()
        }
    }

    /** "Registrar série" in the notification: the next set, prefilled, then the next rest. */
    suspend fun logNextFromNotification() = logLock.withLock {
        val today = snapshot()
        val next = today.nextUp()
        val draft = next?.draft
        if (!today.started || today.ended || next == null || draft == null) {
            Log.w(RestLog.TAG, "log set from notification: nothing to log (started=${today.started} ended=${today.ended})")
            refreshNotification()
            return@withLock
        }
        val set = repository.logSet(today.date, next.item.exercise.id, draft.weightKg, draft.reps)
        Log.i(
            RestLog.TAG,
            "logged from notification: ${next.item.exercise.name} série ${set.position + 1} ${set.weightKg} kg × ${set.reps}",
        )
        afterSetLogged(today.date, next.item.item.id, set)
    }

    /**
     * Shows the between-sets notification while the workout is in progress and no rest runs (the
     * rest has its own, from the service); removes it otherwise.
     */
    suspend fun refreshNotification() {
        val notifications = RestNotifications(app)
        val rest = RestTimer.state(app)
        if (rest != null && !rest.finished) {
            notifications.cancelWorkout()
            return
        }
        val info = betweenSets(snapshot(), System.currentTimeMillis())
        if (info == null) notifications.cancelWorkout() else notifications.notifyWorkout(info)
    }

    /** Refreshes the notification on every change of the log, the workouts or the rest, while the process lives. */
    @OptIn(FlowPreview::class)
    fun watch(scope: CoroutineScope) {
        scope.launch {
            combine(
                repository.observeDays(),
                repository.observeAllItems(),
                repository.observeWorkouts(),
                RestTimer.observe(app).map { it?.let { r -> r.startedAtMs to r.finished } },
            ) { days, items, workouts, rest -> listOf(days, items, workouts, rest) }
                .debounce(300)
                .collectLatest {
                    runCatching { refreshNotification() }.onFailure { Log.e(RestLog.TAG, "workout notification", it) }
                }
        }
    }
}
