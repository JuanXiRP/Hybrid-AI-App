package com.example.hybrid_ai_app.home.presentation.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** `1:05` under an hour, `1:02:03` from an hour up. */
fun formatClock(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

/** The wall clock, re-read every [periodMillis] so a countdown or stopwatch stays live. */
@Composable
fun rememberNowMillis(periodMillis: Long = 1000L): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMillis)
    }
}

/** Keeps only what can be part of a decimal number, e.g. `82.5` or `82,5`. */
fun filterDecimal(text: String, maxLength: Int): String {
    var seenSeparator = false
    val kept = StringBuilder()
    for (char in text) {
        when {
            char.isDigit() -> kept.append(char)
            (char == '.' || char == ',') && !seenSeparator -> {
                seenSeparator = true
                kept.append(char)
            }
        }
    }
    return kept.take(maxLength).toString()
}

/** Digits only, for reps. */
fun filterDigits(text: String, maxLength: Int): String = text.filter { it.isDigit() }.take(maxLength)
