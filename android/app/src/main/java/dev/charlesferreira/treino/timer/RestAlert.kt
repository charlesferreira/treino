package dev.charlesferreira.treino.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.charlesferreira.treino.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared log tag and clock format, so one `logcat -s Treino/Timer` shows the whole timer. */
object RestLog {
    const val TAG = "Treino/Timer"
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    fun time(epochMs: Long): String = TIME.format(Instant.ofEpochMilli(epochMs))
}

/** The end-of-rest vibration, on the alarm usage so it goes through silent mode. */
class RestVibrator(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun vibrate() {
        // Same pattern as the PWA: navigator.vibrate([200, 100, 200, 100, 400]).
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
        Log.i(RestLog.TAG, "vibration requested (USAGE_ALARM, sdk=${Build.VERSION.SDK_INT})")
    }
}

/**
 * Plays res/raw/rest_done.wav once on the media stream, holding transient focus that ducks
 * whatever else is playing (gym music gets quieter instead of pausing), and abandons it at the end.
 */
class RestSound(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Calls [onDone] on the main thread when the sound ended or failed. */
    fun play(onDone: () -> Unit) {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        val granted = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(RestLog.TAG, "audio focus MAY_DUCK granted=$granted, media volume=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        var finished = false
        fun finish(player: MediaPlayer?) {
            if (finished) return
            finished = true
            player?.release()
            audioManager.abandonAudioFocusRequest(focus)
            Log.i(RestLog.TAG, "sound finished, focus abandoned")
            onDone()
        }
        val player = MediaPlayer.create(context, R.raw.rest_done, attributes, audioManager.generateAudioSessionId())
        if (player == null) {
            Log.e(RestLog.TAG, "could not create MediaPlayer")
            finish(null)
            return
        }
        player.setOnCompletionListener { finish(it) }
        player.setOnErrorListener { mp, what, extra ->
            Log.e(RestLog.TAG, "playback error what=$what extra=$extra")
            finish(mp)
            true
        }
        player.start()
        Log.i(RestLog.TAG, "sound started (USAGE_MEDIA, ${player.duration} ms)")
    }
}
