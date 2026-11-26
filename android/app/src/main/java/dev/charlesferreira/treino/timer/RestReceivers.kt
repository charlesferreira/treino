package dev.charlesferreira.treino.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Fired by AlarmManager at the end of the rest. Hands off to the service, which owns the alert. */
class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = RestTimerStore(context).load()
        val now = System.currentTimeMillis()
        Log.i(
            RestLog.TAG,
            "alarm fired at ${RestLog.time(now)}, scheduled ${state?.let { RestLog.time(it.endsAtMs) }}, " +
                "delay ${state?.let { now - it.endsAtMs }} ms",
        )
        context.startForegroundService(RestTimerService.doneIntent(context))
    }
}

/**
 * Notification buttons. Adjusts the store, reschedules the alarm and refreshes the notification
 * without opening the app. Runs in a fresh process just as well, since everything is in the store.
 */
class RestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(RestLog.TAG, "action ${intent.action}")
        when (intent.action) {
            ACTION_MINUS_15 -> RestTimer.adjust(context, -15_000)
            ACTION_PLUS_15 -> RestTimer.adjust(context, 15_000)
            ACTION_PLUS_30 -> RestTimer.adjust(context, 30_000)
            ACTION_SKIP -> RestTimer.skip(context)
            ACTION_LOG_SET -> {
                val state = RestTimerStore(context).load()
                if (state == null) Log.w(RestLog.TAG, "log set without a rest in the store")
                else RestTimer.setLogHandler.onLogSet(context, state)
            }
        }
    }

    companion object {
        const val ACTION_MINUS_15 = "dev.charlesferreira.treino.timer.MINUS_15"
        const val ACTION_PLUS_15 = "dev.charlesferreira.treino.timer.PLUS_15"
        const val ACTION_PLUS_30 = "dev.charlesferreira.treino.timer.PLUS_30"
        const val ACTION_SKIP = "dev.charlesferreira.treino.timer.SKIP"
        const val ACTION_LOG_SET = "dev.charlesferreira.treino.timer.LOG_SET"
    }
}

/**
 * After a reboot or an app update the system has dropped our alarm (and killed the service).
 * Only the alarm is put back: Android 15+ refuses a mediaPlayback FGS started from BOOT_COMPLETED,
 * and the alarm firing is itself an exemption that brings the service up (same as zazen).
 */
class RestRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = RestTimerStore(context)
        val state = store.load()
        val now = System.currentTimeMillis()
        Log.i(RestLog.TAG, "restore on ${intent.action}: state=${state != null} finished=${state?.finished}")
        if (state == null || state.finished) return
        if (now - state.endsAtMs > STALE_MS) {
            // A rest that ended long ago while the phone was off: ringing now would be wrong.
            store.save(state.copy(finished = true))
            Log.i(RestLog.TAG, "rest ended ${now - state.endsAtMs} ms ago, marked finished without alert")
        } else {
            RestAlarms(context).schedule(maxOf(state.endsAtMs, now))
        }
    }

    private companion object {
        const val STALE_MS = 60_000L
    }
}
