package com.example.hybrid_ai_app.home.presentation.run

import com.example.hybrid_ai_app.core.domain.model.RunMainBlock
import com.example.hybrid_ai_app.core.domain.model.RunStructure

/**
 * How the main part of the run is shaped. [FREE] has no structure at all: GPS and a stopwatch, no
 * phases and no cues. It is last because the mode is kept by ordinal (see [RunSetupConfig.toInts]).
 */
enum class RunMainMode { CONTINUOUS, INTERVALS, FREE }

/**
 * What the setup screen edits. It keeps the values of both main modes at once, so switching from
 * intervals to continuous and back does not lose what the athlete had typed into the other one.
 */
data class RunSetupConfig(
    val warmupMin: Int,
    val mode: RunMainMode,
    val continuousMin: Int,
    val repeats: Int,
    val workSec: Int,
    val restSec: Int,
    val cooldownMin: Int,
) {
    /** The guided structure, or null for a free run. */
    fun toStructure(): RunStructure? {
        val main = when (mode) {
            RunMainMode.CONTINUOUS -> RunMainBlock.Continuous(continuousMin * 60)
            RunMainMode.INTERVALS -> RunMainBlock.Intervals(repeats, workSec, restSec)
            RunMainMode.FREE -> return null
        }
        return RunStructure(warmupSec = warmupMin * 60, main = main, cooldownSec = cooldownMin * 60)
    }

    /** The config as plain ints, the form SavedStateHandle can keep. */
    fun toInts(): IntArray = intArrayOf(warmupMin, mode.ordinal, continuousMin, repeats, workSec, restSec, cooldownMin)

    companion object {
        private const val DEFAULT_CONTINUOUS_MIN = 30
        private const val DEFAULT_REPEATS = 6
        private const val DEFAULT_INTERVAL_SEC = 60

        /** Seeds the config from a structure; the mode the structure does not use gets defaults. */
        fun from(structure: RunStructure): RunSetupConfig {
            val base = RunSetupConfig(
                warmupMin = structure.warmupSec / 60,
                mode = RunMainMode.CONTINUOUS,
                continuousMin = DEFAULT_CONTINUOUS_MIN,
                repeats = DEFAULT_REPEATS,
                workSec = DEFAULT_INTERVAL_SEC,
                restSec = DEFAULT_INTERVAL_SEC,
                cooldownMin = structure.cooldownSec / 60,
            )
            return when (val main = structure.main) {
                is RunMainBlock.Continuous -> base.copy(continuousMin = main.durationSec / 60)
                is RunMainBlock.Intervals -> base.copy(
                    mode = RunMainMode.INTERVALS,
                    repeats = main.repeats,
                    workSec = main.workSec,
                    restSec = main.restSec,
                )
            }
        }

        /** The inverse of [toInts]; null when the array is not one this build wrote. */
        fun fromInts(values: IntArray): RunSetupConfig? {
            if (values.size != 7) return null
            val mode = RunMainMode.entries.getOrNull(values[1]) ?: return null
            return RunSetupConfig(values[0], mode, values[2], values[3], values[4], values[5], values[6])
        }
    }
}
