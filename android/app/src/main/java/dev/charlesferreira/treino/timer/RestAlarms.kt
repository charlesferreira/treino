package dev.charlesferreira.treino.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/** One exact wake-up alarm at the end of the rest, on the wall clock the store keeps. */
class RestAlarms(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(endsAtMs: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "cannot schedule exact alarms!")
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pendingIntent())
        Log.i(TAG, "scheduled rest end at ${RestLog.time(endsAtMs)} (in ${endsAtMs - System.currentTimeMillis()} ms)")
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
        Log.i(TAG, "cancelled rest alarm")
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, RestDoneReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = RestLog.TAG
        const val REQUEST_CODE = 100
    }
}
