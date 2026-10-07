package com.example.hybrid_ai_app.tracking

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.hybrid_ai_app.core.domain.model.RunCue
import com.example.hybrid_ai_app.core.domain.model.RunPhase

/**
 * Plays the audible and haptic signals of a guided run: a short beep for each of the last three
 * seconds of a segment, and a distinct pattern for starting to run, starting to rest, and finishing.
 *
 * Tones go through the media stream, so they mix with music playing on the phone or in earphones
 * and follow the media volume. Nothing here may throw: a device with no vibrator, or a tone
 * generator that cannot be created, simply loses that half of the cue.
 */
class RunCuePlayer(context: Context) {

    private class Beep(val tone: Int, val durationMs: Int, val gapMs: Long = BEEP_GAP_MS)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val toneGenerator: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()
    private val handler = Handler(Looper.getMainLooper())

    fun play(cue: RunCue) {
        // A new cue replaces the tail of an earlier multi-beep one instead of overlapping it.
        handler.removeCallbacksAndMessages(null)
        when (cue) {
            is RunCue.Countdown -> signal(listOf(Beep(ToneGenerator.TONE_PROP_BEEP, 90)), longArrayOf(0, 80))
            is RunCue.PhaseStart -> when (cue.phase) {
                RunPhase.WARMUP, RunPhase.CONTINUOUS, RunPhase.WORK -> signal(GO, longArrayOf(0, 200, 100, 200))
                RunPhase.REST, RunPhase.COOLDOWN -> signal(EASE, longArrayOf(0, 500))
            }
            RunCue.Finished -> signal(FINISH, longArrayOf(0, 300, 120, 300, 120, 600))
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { toneGenerator?.release() }
    }

    private fun signal(beeps: List<Beep>, vibration: LongArray) {
        vibrate(vibration)
        var delay = 0L
        for (beep in beeps) {
            handler.postDelayed({ runCatching { toneGenerator?.startTone(beep.tone, beep.durationMs) } }, delay)
            delay += beep.durationMs + beep.gapMs
        }
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
    }

    private companion object {
        const val VOLUME = 100
        const val BEEP_GAP_MS = 100L

        // Two high beeps to start running, one long lower tone to ease off, three to finish.
        val GO = listOf(Beep(ToneGenerator.TONE_DTMF_D, 120), Beep(ToneGenerator.TONE_DTMF_D, 120))
        val EASE = listOf(Beep(ToneGenerator.TONE_DTMF_1, 500))
        val FINISH = List(3) { Beep(ToneGenerator.TONE_DTMF_D, 150) }
    }
}
