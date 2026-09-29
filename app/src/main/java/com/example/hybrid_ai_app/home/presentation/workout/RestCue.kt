package com.example.hybrid_ai_app.home.presentation.workout

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The end-of-rest cue: a short vibration and the default notification sound.
 *
 * Played by the session screen while it is showing; the foreground service plays the same cue
 * through a notification when it is not (see [SessionScreenPresence]). Neither may throw: a device
 * with no vibrator, or with the sound switched off, simply skips that half.
 */
object RestCue {

    private val PATTERN = longArrayOf(0, 300, 150, 300)

    fun play(context: Context) {
        vibrate(context)
        chime(context)
    }

    private fun vibrate(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
        }
    }

    private fun chime(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }
}
