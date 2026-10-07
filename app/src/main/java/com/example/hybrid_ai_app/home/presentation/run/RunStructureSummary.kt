package com.example.hybrid_ai_app.home.presentation.run

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.RunMainBlock
import com.example.hybrid_ai_app.core.domain.model.RunPhase
import com.example.hybrid_ai_app.core.domain.model.RunStructure
import com.example.hybrid_ai_app.home.presentation.workout.formatClock

/**
 * The run the athlete configured, in one line: `Warm-up 10:00 · 6 × 1:00 / 1:00 · Cool-down 5:00`,
 * or "Free run" when it has no structure. It is what the run screen shows instead of the plan's
 * own wording, because the athlete may have changed it.
 */
@Composable
fun runStructureSummary(structure: RunStructure?): String {
    if (structure == null) return stringResource(R.string.run_summary_free)

    val parts = buildList {
        if (structure.warmupSec > 0) {
            add(stringResource(R.string.run_summary_part_format, stringResource(RunPhase.WARMUP.labelRes()), formatClock(structure.warmupSec.toLong())))
        }
        when (val main = structure.main) {
            is RunMainBlock.Continuous -> add(
                stringResource(R.string.run_summary_part_format, stringResource(RunPhase.CONTINUOUS.labelRes()), formatClock(main.durationSec.toLong())),
            )
            is RunMainBlock.Intervals -> add(
                stringResource(
                    R.string.run_summary_intervals_format,
                    main.repeats,
                    formatClock(main.workSec.toLong()),
                    formatClock(main.restSec.toLong()),
                ),
            )
        }
        if (structure.cooldownSec > 0) {
            add(stringResource(R.string.run_summary_part_format, stringResource(RunPhase.COOLDOWN.labelRes()), formatClock(structure.cooldownSec.toLong())))
        }
    }
    return parts.joinToString(" · ")
}

/** The name of a run: the plan day's, or "Extra run" for one added on top of the plan. */
@Composable
fun runTitle(title: String, isExtra: Boolean): String = title.ifBlank {
    stringResource(if (isExtra) R.string.extra_run_title else R.string.executing_workout_fallback)
}
