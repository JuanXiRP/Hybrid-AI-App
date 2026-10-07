package com.example.hybrid_ai_app.core.domain.model

/** Where a guided run stands at one moment. [segment] is null once the run [finished]. */
data class RunProgress(
    val segmentIndex: Int,
    val segment: RunSegment?,
    val remainingSec: Int,
    val next: RunSegment?,
    val finished: Boolean,
)

/** A signal for the athlete, fired on a whole second of the run. */
sealed interface RunCue {
    /** The last seconds of a segment, counting down: [seconds] is 3, 2, then 1. */
    data class Countdown(val seconds: Int) : RunCue

    /** A segment has just begun. */
    data class PhaseStart(val phase: RunPhase) : RunCue

    /** The whole structure is over. */
    data object Finished : RunCue
}

/**
 * Maps elapsed seconds onto a [RunStructure]: which segment the athlete is in, and which cues
 * fall on which seconds. Pure, so the service that plays the cues has nothing left to get wrong
 * except the clock.
 */
class RunTimeline(structure: RunStructure) {

    val segments: List<RunSegment> = structure.segments()
    val totalSec: Int = segments.sumOf { it.durationSec }

    /** The second each segment begins on. */
    private val starts: List<Int> = segments.runningFold(0) { at, segment -> at + segment.durationSec }.dropLast(1)

    fun progressAt(elapsedSec: Int): RunProgress {
        val elapsed = elapsedSec.coerceAtLeast(0)
        if (elapsed >= totalSec) {
            return RunProgress(segmentIndex = segments.size, segment = null, remainingSec = 0, next = null, finished = true)
        }
        val index = starts.indexOfLast { it <= elapsed }
        val segment = segments[index]
        return RunProgress(
            segmentIndex = index,
            segment = segment,
            remainingSec = starts[index] + segment.durationSec - elapsed,
            next = segments.getOrNull(index + 1),
            finished = false,
        )
    }

    /**
     * Every cue on the seconds after [fromSec] up to and including [toSec], oldest first. A caller
     * that starts the run passes -1 so the cue on second 0 is included. When a stretch of seconds
     * was skipped the caller should play only the last cue, never a burst of them.
     */
    fun cuesBetween(fromSec: Int, toSec: Int): List<RunCue> = buildList {
        for (second in (fromSec + 1)..toSec) {
            cueAt(second)?.let(::add)
        }
    }

    private fun cueAt(second: Int): RunCue? {
        if (totalSec == 0 || second < 0 || second > totalSec) return null
        if (second == totalSec) return RunCue.Finished
        val startIndex = starts.indexOf(second)
        if (startIndex >= 0) return RunCue.PhaseStart(segments[startIndex].phase)

        // A countdown belongs to the segment that is about to end, and only when it is long enough
        // for three beeps not to crowd its own start.
        val index = starts.indexOfLast { it <= second }
        val segment = segments[index]
        val untilEnd = starts[index] + segment.durationSec - second
        return if (segment.durationSec >= COUNTDOWN_MIN_SEGMENT_SEC && untilEnd in 1..COUNTDOWN_SECONDS) {
            RunCue.Countdown(untilEnd)
        } else {
            null
        }
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val COUNTDOWN_MIN_SEGMENT_SEC = 10
    }
}
