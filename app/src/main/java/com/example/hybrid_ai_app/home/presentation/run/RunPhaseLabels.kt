package com.example.hybrid_ai_app.home.presentation.run

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.RunPhase
import com.example.hybrid_ai_app.core.domain.model.RunSegment

@StringRes
fun RunPhase.labelRes(): Int = when (this) {
    RunPhase.WARMUP -> R.string.run_phase_warmup
    RunPhase.CONTINUOUS -> R.string.run_phase_continuous
    RunPhase.WORK -> R.string.run_phase_work
    RunPhase.REST -> R.string.run_phase_rest
    RunPhase.COOLDOWN -> R.string.run_phase_cooldown
}

/** One colour per kind of effort, shared by the summary bar and the live banner. */
@Composable
fun RunPhase.color(): Color = when (this) {
    RunPhase.WARMUP, RunPhase.COOLDOWN -> MaterialTheme.colorScheme.tertiary
    RunPhase.CONTINUOUS, RunPhase.WORK -> MaterialTheme.colorScheme.primary
    RunPhase.REST -> MaterialTheme.colorScheme.secondary
}

/** The segment as the athlete reads it: the phase, plus a count such as 3/6 inside intervals. */
@Composable
fun RunSegment.label(): String {
    val phase = stringResource(phase.labelRes())
    val repeat = repeat
    val totalRepeats = totalRepeats
    return if (repeat != null && totalRepeats != null) {
        stringResource(R.string.run_banner_repeat_format, phase, repeat, totalRepeats)
    } else {
        phase
    }
}

/** Same text as the composable label, for the notification the foreground service posts. */
fun RunSegment.label(context: Context): String {
    val phase = context.getString(phase.labelRes())
    val repeat = repeat
    val totalRepeats = totalRepeats
    return if (repeat != null && totalRepeats != null) {
        context.getString(R.string.run_banner_repeat_format, phase, repeat, totalRepeats)
    } else {
        phase
    }
}
