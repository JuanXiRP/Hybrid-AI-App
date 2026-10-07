package com.example.hybrid_ai_app.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the athlete is doing during one [RunSegment] of a guided run. */
enum class RunPhase { WARMUP, CONTINUOUS, WORK, REST, COOLDOWN }

/**
 * The main part of a guided run: one continuous effort, or repeated work/rest intervals.
 *
 * Serializable because the run in progress is stored with its structure (see `ActiveRun`); the
 * serial names are fixed so renaming a class does not orphan a run stored by an earlier build.
 */
@Serializable
sealed interface RunMainBlock {
    @Serializable
    @SerialName("continuous")
    data class Continuous(val durationSec: Int) : RunMainBlock

    @Serializable
    @SerialName("intervals")
    data class Intervals(val repeats: Int, val workSec: Int, val restSec: Int) : RunMainBlock
}

/**
 * One timed stretch of a guided run. [repeat] and [totalRepeats] are set only for the work and rest
 * segments of an interval block, where [repeat] counts from 1.
 */
data class RunSegment(
    val phase: RunPhase,
    val durationSec: Int,
    val repeat: Int? = null,
    val totalRepeats: Int? = null,
)

/** The bounds the setup screen enforces, kept next to the model they protect. */
object RunLimits {
    const val MAX_WARMUP_MIN = 60
    const val MAX_COOLDOWN_MIN = 60
    const val MIN_CONTINUOUS_MIN = 1
    const val MAX_CONTINUOUS_MIN = 180
    const val MIN_REPEATS = 1
    const val MAX_REPEATS = 30
    const val MIN_WORK_SEC = 15
    const val MAX_WORK_SEC = 1200
    const val MAX_REST_SEC = 600

    /** The step of the work and rest steppers. */
    const val STEP_SEC = 15
}

/**
 * A run described as warm-up, main block and cool-down, all by time. A zero warm-up or cool-down is
 * simply left out, so "no warm-up" needs no special case.
 */
@Serializable
data class RunStructure(
    val warmupSec: Int,
    val main: RunMainBlock,
    val cooldownSec: Int,
) {
    /** The ordered segments. An interval block has no rest after its last work interval. */
    fun segments(): List<RunSegment> = buildList {
        if (warmupSec > 0) add(RunSegment(RunPhase.WARMUP, warmupSec))
        when (main) {
            is RunMainBlock.Continuous -> {
                if (main.durationSec > 0) add(RunSegment(RunPhase.CONTINUOUS, main.durationSec))
            }
            is RunMainBlock.Intervals -> for (repeat in 1..main.repeats) {
                if (main.workSec > 0) add(RunSegment(RunPhase.WORK, main.workSec, repeat, main.repeats))
                if (repeat < main.repeats && main.restSec > 0) {
                    add(RunSegment(RunPhase.REST, main.restSec, repeat, main.repeats))
                }
            }
        }
        if (cooldownSec > 0) add(RunSegment(RunPhase.COOLDOWN, cooldownSec))
    }

    fun totalSec(): Int = segments().sumOf { it.durationSec }
}
