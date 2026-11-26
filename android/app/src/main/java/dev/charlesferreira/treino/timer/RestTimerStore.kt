package dev.charlesferreira.treino.timer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The running rest, in SharedPreferences, plus the sound/vibration switches the Profile turns on
 * and off later. Synchronous on purpose: receivers and the service read it in a fresh process.
 */
class RestTimerStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): RestState? {
        if (!prefs.getBoolean(ACTIVE, false)) return null
        val plan = RestProgress.decode(prefs.getString(PLAN, null))
        val done = prefs.getInt(SETS_DONE, 0).coerceIn(0, plan.sum())
        return RestState(
            endsAtMs = prefs.getLong(ENDS_AT, 0),
            totalMs = prefs.getLong(TOTAL_MS, 0),
            workoutName = prefs.getString(WORKOUT, "").orEmpty(),
            exerciseName = prefs.getString(EXERCISE, "").orEmpty(),
            setNumber = prefs.getInt(SET_NUMBER, 1),
            setCount = prefs.getInt(SET_COUNT, 1),
            target = prefs.getString(TARGET, "").orEmpty(),
            progress = WorkoutProgress(plan, done),
            finished = prefs.getBoolean(FINISHED, false),
            nextShort = prefs.getString(NEXT_SHORT, null) ?: prefs.getString(TARGET, "").orEmpty(),
            loggedLabel = prefs.getString(LOGGED_LABEL, "").orEmpty(),
            loggedValue = prefs.getString(LOGGED_VALUE, "").orEmpty(),
        )
    }

    fun save(state: RestState) {
        prefs.edit(commit = true) {
            putBoolean(ACTIVE, true)
            putLong(ENDS_AT, state.endsAtMs)
            putLong(TOTAL_MS, state.totalMs)
            putString(WORKOUT, state.workoutName)
            putString(EXERCISE, state.exerciseName)
            putInt(SET_NUMBER, state.setNumber)
            putInt(SET_COUNT, state.setCount)
            putString(TARGET, state.target)
            putString(PLAN, RestProgress.encode(state.progress.setsPerExercise))
            putInt(SETS_DONE, state.progress.setsDone)
            putBoolean(FINISHED, state.finished)
            putString(NEXT_SHORT, state.nextShort)
            putString(LOGGED_LABEL, state.loggedLabel)
            putString(LOGGED_VALUE, state.loggedValue)
        }
    }

    /**
     * The rest as it changes in this process (service, receivers and the app share one process, and
     * SharedPreferences tells every listener). Settings changes emit too, which is harmless.
     */
    fun observe(): Flow<RestState?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(load()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Drops the rest, keeps the settings. */
    fun clear() {
        prefs.edit(commit = true) {
            listOf(
                ACTIVE, ENDS_AT, TOTAL_MS, WORKOUT, EXERCISE, SET_NUMBER, SET_COUNT, TARGET, PLAN, SETS_DONE, FINISHED,
                NEXT_SHORT, LOGGED_LABEL, LOGGED_VALUE,
            )
                .forEach { remove(it) }
        }
    }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(SOUND_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SOUND_ENABLED, value) }

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(VIBRATION_ENABLED, true)
        set(value) = prefs.edit { putBoolean(VIBRATION_ENABLED, value) }

    private companion object {
        const val NAME = "rest_timer"
        const val ACTIVE = "active"
        const val ENDS_AT = "ends_at"
        const val TOTAL_MS = "total_ms"
        const val WORKOUT = "workout"
        const val EXERCISE = "exercise"
        const val SET_NUMBER = "set_number"
        const val SET_COUNT = "set_count"
        const val TARGET = "target"
        const val PLAN = "plan"
        const val SETS_DONE = "sets_done"
        const val FINISHED = "finished"
        const val NEXT_SHORT = "next_short"
        const val LOGGED_LABEL = "logged_label"
        const val LOGGED_VALUE = "logged_value"
        const val SOUND_ENABLED = "sound_enabled"
        const val VIBRATION_ENABLED = "vibration_enabled"
    }
}
