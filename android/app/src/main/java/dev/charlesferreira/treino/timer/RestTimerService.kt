package dev.charlesferreira.treino.timer

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import dev.charlesferreira.treino.R
import kotlinx.coroutines.launch

/**
 * Foreground service for the duration of a rest. It owns the ongoing notification and the
 * end-of-rest alert; the countdown itself is the notification's chronometer plus one exact
 * alarm, so nothing here ticks. State lives in [RestTimerStore], so a fresh process carries on.
 */
class RestTimerService : Service() {

    private lateinit var store: RestTimerStore
    private lateinit var alarms: RestAlarms
    private lateinit var notifications: RestNotifications
    private val main = Handler(Looper.getMainLooper())
    private var ringing = false

    override fun onCreate() {
        super.onCreate()
        store = RestTimerStore(this)
        alarms = RestAlarms(this)
        notifications = RestNotifications(this)
        notifications.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(RestLog.TAG, "service onStartCommand action=${intent?.action} startId=$startId")
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> {
                val request = requestFrom(intent)
                if (request == null) {
                    Log.e(RestLog.TAG, "start without a valid request")
                    goForeground(placeholder())
                    stopSelfSoon()
                } else {
                    val state = RestState.start(request, now)
                    store.save(state)
                    notifications.cancelDone()
                    notifications.cancelWorkout()
                    goForeground(notifications.ongoing(state))
                    alarms.schedule(state.endsAtMs)
                    Log.i(RestLog.TAG, "rest started: ${request.durationMs} ms, ends ${RestLog.time(state.endsAtMs)}")
                }
            }
            ACTION_DONE -> {
                val state = store.load()
                goForeground(state?.let { notifications.ongoing(it) } ?: placeholder())
                if (state == null || state.finished) {
                    Log.w(RestLog.TAG, "done with no running rest (state=${state != null}), ignoring")
                    if (!ringing) stopSelfSoon()
                } else {
                    ring(state, now)
                }
            }
            else -> restore(now) // ACTION_RESUME, or a sticky restart with a null intent
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(RestLog.TAG, "service onDestroy")
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restore(now: Long) {
        val state = store.load()
        when {
            state == null || state.finished -> {
                Log.i(RestLog.TAG, "nothing to restore")
                goForeground(placeholder())
                stopSelfSoon()
            }
            RestMath.isDue(state, now) && now - state.endsAtMs > STALE_MS -> {
                // The alarm was lost (app update, killed process) long ago: no late alert.
                Log.i(RestLog.TAG, "rest ended ${now - state.endsAtMs} ms ago, finishing without alert")
                store.save(state.copy(finished = true))
                goForeground(placeholder())
                stopSelfSoon()
            }
            RestMath.isDue(state, now) -> {
                goForeground(notifications.ongoing(state))
                ring(state, now)
            }
            else -> {
                notifications.cancelWorkout()
                goForeground(notifications.ongoing(state))
                alarms.schedule(state.endsAtMs)
                Log.i(RestLog.TAG, "restored rest, ends ${RestLog.time(state.endsAtMs)}")
            }
        }
    }

    private fun ring(state: RestState, now: Long) {
        if (ringing) return
        ringing = true
        val lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "treino:rest_done")
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        Log.i(
            RestLog.TAG,
            "REST DONE ringing at ${RestLog.time(now)}, scheduled ${RestLog.time(state.endsAtMs)}, " +
                "delay ${now - state.endsAtMs} ms, sound=${store.soundEnabled} vibration=${store.vibrationEnabled}",
        )
        val finished = state.copy(finished = true)
        store.save(finished)
        alarms.cancel()
        // A plain heads-up. Posting it as the FGS notification (to get setColorized honored) was tried
        // on the emulator: still not colorized, and it was left non-dismissible. See docs/SPIKE.md.
        // With the app on screen the done pill shows it instead of a heads-up over the app.
        if (RestTimer.appInForeground) Log.i(RestLog.TAG, "app in foreground: no heads-up")
        else notifications.notifyDone(finished)
        if (store.vibrationEnabled) RestVibrator(this).vibrate()
        // The between-sets notification replaces the rest one.
        val over = RestTimer.handlerScope.launch {
            runCatching { RestTimer.restOverHandler.onRestOver(applicationContext) }
                .onFailure { Log.e(RestLog.TAG, "rest over handler failed", it) }
        }
        val end = {
            ringing = false
            // The heads-up stays; the ongoing notification goes with the service.
            over.invokeOnCompletion {
                main.post {
                    if (lock.isHeld) lock.release()
                    // A rest started while ringing ("Registrar série") keeps the service.
                    val next = store.load()
                    if (!ringing && (next == null || next.finished)) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            Unit
        }
        if (store.soundEnabled) {
            RestSound(this).play { main.post(end) }
        } else {
            main.postDelayed(end, VIBRATION_MS)
        }
    }

    /** Re-posts the rest notification every second so its bar empties; stops when the rest ends. */
    private val tick = object : Runnable {
        override fun run() {
            val state = store.load() ?: return
            if (state.finished || ringing) return
            val now = System.currentTimeMillis()
            if (RestMath.isDue(state, now)) return
            notifications.notifyOngoing(state)
            main.postDelayed(this, TICK_MS - (now - state.startedAtMs) % TICK_MS)
        }
    }

    private fun startTicking() {
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
    }

    private fun goForeground(notification: Notification) {
        startTicking()
        startForeground(RestNotifications.ID_REST, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        notifications.logPromotionState(notification)
    }

    /** startForegroundService must always be answered with startForeground, even when there is nothing to show. */
    private fun placeholder(): Notification =
        Notification.Builder(this, RestNotifications.CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Treino")
            .build()

    private fun stopSelfSoon() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "dev.charlesferreira.treino.timer.START"
        const val ACTION_DONE = "dev.charlesferreira.treino.timer.DONE"
        const val ACTION_RESUME = "dev.charlesferreira.treino.timer.RESUME"
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
        private const val VIBRATION_MS = 1_200L
        private const val STALE_MS = 60_000L
        private const val TICK_MS = 1_000L

        private const val EXTRA_DURATION_MS = "duration_ms"
        private const val EXTRA_WORKOUT = "workout"
        private const val EXTRA_EXERCISE = "exercise"
        private const val EXTRA_SET_NUMBER = "set_number"
        private const val EXTRA_SET_COUNT = "set_count"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_PLAN = "plan"
        private const val EXTRA_SETS_DONE = "sets_done"
        private const val EXTRA_NEXT_SHORT = "next_short"
        private const val EXTRA_LOGGED_LABEL = "logged_label"
        private const val EXTRA_LOGGED_VALUE = "logged_value"

        fun startIntent(context: Context, r: RestRequest): Intent =
            Intent(context, RestTimerService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_DURATION_MS, r.durationMs)
                .putExtra(EXTRA_WORKOUT, r.workoutName)
                .putExtra(EXTRA_EXERCISE, r.exerciseName)
                .putExtra(EXTRA_SET_NUMBER, r.setNumber)
                .putExtra(EXTRA_SET_COUNT, r.setCount)
                .putExtra(EXTRA_TARGET, r.target)
                .putExtra(EXTRA_PLAN, RestProgress.encode(r.progress.setsPerExercise))
                .putExtra(EXTRA_SETS_DONE, r.progress.setsDone)
                .putExtra(EXTRA_NEXT_SHORT, r.nextShort)
                .putExtra(EXTRA_LOGGED_LABEL, r.loggedLabel)
                .putExtra(EXTRA_LOGGED_VALUE, r.loggedValue)

        fun doneIntent(context: Context): Intent =
            Intent(context, RestTimerService::class.java).setAction(ACTION_DONE)

        fun resumeIntent(context: Context): Intent =
            Intent(context, RestTimerService::class.java).setAction(ACTION_RESUME)

        private fun requestFrom(intent: Intent): RestRequest? = runCatching {
            RestRequest(
                durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0),
                workoutName = intent.getStringExtra(EXTRA_WORKOUT).orEmpty(),
                exerciseName = intent.getStringExtra(EXTRA_EXERCISE).orEmpty(),
                setNumber = intent.getIntExtra(EXTRA_SET_NUMBER, 1),
                setCount = intent.getIntExtra(EXTRA_SET_COUNT, 1),
                target = intent.getStringExtra(EXTRA_TARGET).orEmpty(),
                progress = WorkoutProgress(
                    RestProgress.decode(intent.getStringExtra(EXTRA_PLAN)),
                    intent.getIntExtra(EXTRA_SETS_DONE, 0),
                ),
                nextShort = intent.getStringExtra(EXTRA_NEXT_SHORT) ?: intent.getStringExtra(EXTRA_TARGET).orEmpty(),
                loggedLabel = intent.getStringExtra(EXTRA_LOGGED_LABEL).orEmpty(),
                loggedValue = intent.getStringExtra(EXTRA_LOGGED_VALUE).orEmpty(),
            )
        }.getOrNull()
    }
}
