package com.example.hybrid_ai_app.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTimelineTest {

    // Layout: warm-up first, then work 1, rest, work 2, and the cool-down last.
    private val warmupSec = 20
    private val workSec = 12
    private val restSec = 5
    private val cooldownSec = 10
    private val structure = RunStructure(
        warmupSec = warmupSec,
        main = RunMainBlock.Intervals(repeats = 2, workSec = workSec, restSec = restSec),
        cooldownSec = cooldownSec,
    )
    private val timeline = RunTimeline(structure)
    private val workStart = warmupSec
    private val restStart = workStart + workSec
    private val total = warmupSec + workSec + restSec + workSec + cooldownSec

    @Test
    fun `progress at the start is the warm-up with the first work next`() {
        // Arrange / Act
        val progress = timeline.progressAt(0)

        // Assert
        assertEquals(RunPhase.WARMUP, progress.segment?.phase)
        assertEquals(warmupSec, progress.remainingSec)
        assertEquals(RunPhase.WORK, progress.next?.phase)
        assertFalse(progress.finished)
    }

    @Test
    fun `progress on a boundary belongs to the segment that begins there`() {
        // Arrange / Act
        val progress = timeline.progressAt(workStart)

        // Assert
        assertEquals(RunPhase.WORK, progress.segment?.phase)
        assertEquals(1, progress.segment?.repeat)
        assertEquals(workSec, progress.remainingSec)
    }

    @Test
    fun `progress one second before a boundary has one second left`() {
        // Arrange / Act
        val progress = timeline.progressAt(restStart + restSec - 1)

        // Assert
        assertEquals(RunPhase.REST, progress.segment?.phase)
        assertEquals(1, progress.remainingSec)
    }

    @Test
    fun `progress in the last segment has no next one`() {
        // Arrange / Act
        val progress = timeline.progressAt(total - 1)

        // Assert
        assertEquals(RunPhase.COOLDOWN, progress.segment?.phase)
        assertNull(progress.next)
    }

    @Test
    fun `progress at and after the end is finished`() {
        // Arrange / Act
        val atEnd = timeline.progressAt(total)
        val afterEnd = timeline.progressAt(total + 100)

        // Assert
        assertTrue(atEnd.finished)
        assertNull(atEnd.segment)
        assertEquals(0, atEnd.remainingSec)
        assertEquals(atEnd, afterEnd)
    }

    @Test
    fun `negative elapsed time is read as the start`() {
        // Arrange / Act
        val progress = timeline.progressAt(-5)

        // Assert
        assertEquals(timeline.progressAt(0), progress)
    }

    @Test
    fun `starting the run cues the warm-up on second zero`() {
        // Arrange / Act
        val cues = timeline.cuesBetween(-1, 0)

        // Assert
        assertEquals(listOf<RunCue>(RunCue.PhaseStart(RunPhase.WARMUP)), cues)
    }

    @Test
    fun `the last three seconds count down and the boundary starts the next phase`() {
        // Arrange / Act
        val cues = timeline.cuesBetween(workStart - 4, workStart)

        // Assert
        assertEquals(
            listOf(
                RunCue.Countdown(3),
                RunCue.Countdown(2),
                RunCue.Countdown(1),
                RunCue.PhaseStart(RunPhase.WORK),
            ),
            cues,
        )
    }

    @Test
    fun `a segment shorter than ten seconds has no countdown`() {
        // Arrange / Act
        val cues = timeline.cuesBetween(restStart - 1, restStart + restSec)

        // Assert
        assertEquals(listOf(RunCue.PhaseStart(RunPhase.REST), RunCue.PhaseStart(RunPhase.WORK)), cues)
    }

    @Test
    fun `the end counts down and then finishes`() {
        // Arrange / Act
        val cues = timeline.cuesBetween(total - 4, total)

        // Assert
        assertEquals(
            listOf(RunCue.Countdown(3), RunCue.Countdown(2), RunCue.Countdown(1), RunCue.Finished),
            cues,
        )
    }

    @Test
    fun `nothing is cued after the end`() {
        // Arrange / Act
        val cues = timeline.cuesBetween(total, total + 30)

        // Assert
        assertEquals(emptyList<RunCue>(), cues)
    }

    @Test
    fun `skipped seconds still return every cue, oldest first`() {
        // Arrange / Act
        val cues = timeline.cuesBetween(-1, workStart + 5)

        // Assert
        assertEquals(RunCue.PhaseStart(RunPhase.WARMUP), cues.first())
        assertEquals(RunCue.PhaseStart(RunPhase.WORK), cues.last())
    }

    @Test
    fun `a short structure with no countdown still starts and finishes`() {
        // Arrange
        val short = RunTimeline(RunStructure(warmupSec = 8, main = RunMainBlock.Continuous(0), cooldownSec = 0))

        // Act
        val cues = short.cuesBetween(-1, 8)

        // Assert
        assertEquals(listOf(RunCue.PhaseStart(RunPhase.WARMUP), RunCue.Finished), cues)
    }

    @Test
    fun `an empty structure is finished at once and never cues`() {
        // Arrange
        val empty = RunTimeline(RunStructure(warmupSec = 0, main = RunMainBlock.Continuous(0), cooldownSec = 0))

        // Act
        val progress = empty.progressAt(0)
        val cues = empty.cuesBetween(-1, 5)

        // Assert
        assertTrue(progress.finished)
        assertEquals(emptyList<RunCue>(), cues)
    }
}
