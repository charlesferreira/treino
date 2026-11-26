package dev.charlesferreira.treino.timer

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * Called by the "Registrar série" notification action, off the main thread, from a receiver that
 * may be in a fresh process. [state] is the rest in the store, if any. The app writes the set to
 * Room and starts the next rest; the notification tap allows the foreground service start.
 */
fun interface SetLogHandler {
    suspend fun onLogSet(context: Context, state: RestState?)
}

/**
 * Called when a rest is over (skipped, or its end alert rang), off the main thread. The app posts
 * its "between sets" notification here, since the rest notification goes away with the service.
 */
fun interface RestOverHandler {
    suspend fun onRestOver(context: Context)
}

/**
 * Entry point of the rest timer. Everything here works from a fresh process: the state lives in
 * [RestTimerStore], the end of the rest is an exact alarm, and nothing counts time.
 *
 * Phase 4 usage:
 * ```
 * RestTimer.start(context, RestRequest(durationMs = 90_000, workoutName = "Superior B", ...))
 * RestTimer.state(context)?.remainingMs(System.currentTimeMillis())
 * RestTimer.setLogHandler = SetLogHandler { ctx, state -> /* write the set to Room */ }  // in Application.onCreate
 * RestTimer.restOverHandler = RestOverHandler { ctx -> /* post the between-sets notification */ }
 * ```
 */
object RestTimer {

    /** Default only logs. Set it in Application.onCreate so a receiver in a fresh process sees it. */
    @Volatile
    var setLogHandler: SetLogHandler = SetLogHandler { _, state ->
        Log.i(RestLog.TAG, "log set requested without a handler: ${state?.exerciseName} ${state?.target}")
    }

    /** Set in Application.onCreate, like [setLogHandler]. */
    @Volatile
    var restOverHandler: RestOverHandler = RestOverHandler { }

    /**
     * True while the app's Activity is resumed. The end of a rest then shows in the app (the done
     * pill) and the heads-up is not posted over it; sound and vibration still play.
     */
    @Volatile
    var appInForeground: Boolean = false

    /** Where receivers and the service run the handlers. Never cancelled: work here is short. */
    internal val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts (or restarts) a rest. Call from the foreground: it starts the foreground service. */
    fun start(context: Context, request: RestRequest) {
        context.startForegroundService(RestTimerService.startIntent(context, request))
    }

    /**
     * Brings the service and the alarm back for a rest the store says is running (after an app
     * update or the process being killed). Idempotent; call when the app comes to the front.
     */
    fun resumeIfActive(context: Context) {
        val state = state(context) ?: return
        if (state.finished) return
        startService(context, RestTimerService.resumeIntent(context))
    }

    /** The current rest, or null when none. The UI reads this when it comes back to the front. */
    fun state(context: Context): RestState? = RestTimerStore(context).load()

    /** [state] as it changes; the screens only redraw from it, nothing is counted by them. */
    fun observe(context: Context): Flow<RestState?> = RestTimerStore(context).observe()

    /** The app saw the end of the rest ("Abrir" in the done pill): drops it and its heads-up. */
    fun dismissFinished(context: Context) {
        val store = RestTimerStore(context)
        if (store.load()?.finished != true) return
        store.clear()
        RestNotifications(context).cancelDone()
        Log.i(RestLog.TAG, "finished rest dismissed in the app")
    }

    /** −15 s / +15 s / +30 s. Never below zero; at zero the rest ends right away. */
    fun adjust(context: Context, deltaMs: Long) {
        val store = RestTimerStore(context)
        val current = store.load() ?: run { Log.w(RestLog.TAG, "adjust without a rest"); return }
        val now = System.currentTimeMillis()
        val wasFinished = current.finished
        val next = RestMath.adjust(current, deltaMs, now)
        if (next == current) return
        store.save(next)
        Log.i(RestLog.TAG, "adjusted by ${deltaMs / 1000}s, ends ${RestLog.time(next.endsAtMs)}")
        val alarms = RestAlarms(context)
        if (RestMath.isDue(next, now)) {
            // An exact alarm for "now" lands in the next AlarmManager batch, seconds late: ring directly.
            alarms.cancel()
            startService(context, RestTimerService.doneIntent(context))
            return
        }
        alarms.schedule(next.endsAtMs)
        val notifications = RestNotifications(context)
        if (wasFinished) {
            notifications.cancelDone()
            // Back to resting: the ongoing notification belongs to the foreground service again.
            startService(context, RestTimerService.resumeIntent(context))
        } else {
            notifications.notifyOngoing(next)
        }
    }

    /** Ends the rest now without the alert. */
    fun skip(context: Context) {
        RestAlarms(context).cancel()
        RestTimerStore(context).clear()
        RestNotifications(context).cancelDone()
        context.stopService(android.content.Intent(context, RestTimerService::class.java))
        Log.i(RestLog.TAG, "skipped")
    }

    /** The Profile switches (Phase 3). Both default to on. */
    fun setSoundEnabled(context: Context, enabled: Boolean) {
        RestTimerStore(context).soundEnabled = enabled
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        RestTimerStore(context).vibrationEnabled = enabled
    }

    private fun startService(context: Context, intent: android.content.Intent) {
        try {
            context.startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // A notification action grants a temporary FGS-start exemption, so this is not expected;
            // if it happens the alarm still fires and the next start brings the service back.
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                Log.e(RestLog.TAG, "foreground service start not allowed", e)
            } else {
                throw e
            }
        }
    }
}
