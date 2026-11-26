package dev.charlesferreira.treino.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import dev.charlesferreira.treino.MainActivity
import dev.charlesferreira.treino.R

/**
 * Builds the two timer notifications: the ongoing one while resting (FGS, low channel, a
 * countdown chronometer the system ticks by itself) and the heads-up at the end (high channel,
 * no channel sound: the sound is ours).
 */
class RestNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val rest = NotificationChannel(CHANNEL_REST, "Descanso", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        val done = NotificationChannel(CHANNEL_DONE, "Fim do descanso", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(listOf(rest, done))
    }

    fun ongoing(state: RestState): Notification {
        val builder = Notification.Builder(context, CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ORANGE)
            .setContentTitle("Descanso")
            .setContentText("${state.exerciseName}, ${state.setLabel}, ${state.target}")
            .setSubText(state.workoutName)
            .setContentIntent(openIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(state.endsAtMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(action("−15s", RestActionReceiver.ACTION_MINUS_15))
            .addAction(action("+15s", RestActionReceiver.ACTION_PLUS_15))
            .addAction(action("Pular", RestActionReceiver.ACTION_SKIP))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        applyProgress(builder, state.progress, promote = true)
        return builder.build()
    }

    fun done(state: RestState): Notification {
        val builder = Notification.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ORANGE)
            .setColorized(true)
            .setContentTitle("Hora da série ${state.setNumber}")
            .setContentText("${state.exerciseName} ${state.target}")
            .setSubText(state.workoutName)
            .setContentIntent(openIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .addAction(action("+30s", RestActionReceiver.ACTION_PLUS_30))
            .addAction(Notification.Action.Builder(null, "Abrir", openIntent()).build())
        applyProgress(builder, state.progress, promote = false)
        return builder.build()
    }

    /**
     * Phase 4 hook: the "between sets" ongoing notification gets this action. During the rest the
     * three slots are −15s, +15s and Pular, so it is not shown in Phase 0.
     */
    fun logSetAction(): Notification.Action = action("Registrar série", RestActionReceiver.ACTION_LOG_SET)

    fun notifyOngoing(state: RestState) = manager.notify(ID_REST, ongoing(state))

    fun notifyDone(state: RestState) = manager.notify(ID_DONE, done(state))

    fun cancelDone() = manager.cancel(ID_DONE)

    fun cancelOngoing() = manager.cancel(ID_REST)

    /** Android 16 ProgressStyle with one segment per exercise; a single plain bar below API 36. */
    private fun applyProgress(builder: Notification.Builder, progress: WorkoutProgress, promote: Boolean) {
        if (progress.totalSets == 0) return
        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(
                    RestProgress.segments(progress).map { Notification.ProgressStyle.Segment(it.length).setColor(ORANGE) },
                )
                .setProgress(RestProgress.position(progress))
            builder.setStyle(style)
            if (promote) requestPromotion(builder)
        } else {
            builder.setProgress(progress.totalSets, RestProgress.position(progress), false)
        }
    }

    /**
     * Live Update (Now Bar on One UI). Builder.setRequestPromotedOngoing is API 36.1; on 36.0 the
     * same request is the extra, which the 36.1 builder writes too.
     */
    private fun requestPromotion(builder: Notification.Builder) {
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setRequestPromotedOngoing(true)
        } else {
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
    }

    fun logPromotionState(notification: Notification) {
        if (Build.VERSION.SDK_INT < 36) return
        val canPost = manager.canPostPromotedNotifications()
        Log.i(
            RestLog.TAG,
            "promotion: canPostPromoted=$canPost promotable=${notification.hasPromotableCharacteristics()} " +
                "flagPromoted=${notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0}",
        )
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(label: String, action: String): Notification.Action {
        val intent = Intent(context, RestActionReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, label, pending).build()
    }

    companion object {
        const val CHANNEL_REST = "rest"
        const val CHANNEL_DONE = "rest_done"
        const val ID_REST = 1
        const val ID_DONE = 2
        const val ORANGE = 0xFFFF7A45.toInt()
        private const val REQUEST_OPEN = 1
        private const val DONE_TIMEOUT_MS = 10 * 60_000L
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}
