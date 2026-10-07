package com.example.hybrid_ai_app.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RunStructureTest {

    private fun intervals(repeats: Int, workSec: Int, restSec: Int) = RunMainBlock.Intervals(repeats, workSec, restSec)

    @Test
    fun `a continuous run is warm-up, one effort and cool-down in order`() {
        // Arrange
        val structure = RunStructure(warmupSec = 300, main = RunMainBlock.Continuous(1500), cooldownSec = 120)

        // Act
        val segments = structure.segments()

        // Assert
        assertEquals(
            listOf(
                RunSegment(RunPhase.WARMUP, 300),
                RunSegment(RunPhase.CONTINUOUS, 1500),
                RunSegment(RunPhase.COOLDOWN, 120),
            ),
            segments,
        )
        assertEquals(1920, structure.totalSec())
    }

    @Test
    fun `intervals alternate work and rest and end on work`() {
        // Arrange
        val structure = RunStructure(warmupSec = 0, main = intervals(repeats = 3, workSec = 60, restSec = 30), cooldownSec = 0)

        // Act
        val segments = structure.segments()

        // Assert
        assertEquals(
            listOf(
                RunSegment(RunPhase.WORK, 60, repeat = 1, totalRepeats = 3),
                RunSegment(RunPhase.REST, 30, repeat = 1, totalRepeats = 3),
                RunSegment(RunPhase.WORK, 60, repeat = 2, totalRepeats = 3),
                RunSegment(RunPhase.REST, 30, repeat = 2, totalRepeats = 3),
                RunSegment(RunPhase.WORK, 60, repeat = 3, totalRepeats = 3),
            ),
            segments,
        )
        assertEquals(240, structure.totalSec())
    }

    @Test
    fun `a zero rest leaves the rest segments out`() {
        // Arrange
        val structure = RunStructure(warmupSec = 0, main = intervals(repeats = 2, workSec = 45, restSec = 0), cooldownSec = 0)

        // Act
        val phases = structure.segments().map { it.phase }

        // Assert
        assertEquals(listOf(RunPhase.WORK, RunPhase.WORK), phases)
    }

    @Test
    fun `a zero warm-up and cool-down are left out`() {
        // Arrange
        val structure = RunStructure(warmupSec = 0, main = RunMainBlock.Continuous(600), cooldownSec = 0)

        // Act
        val phases = structure.segments().map { it.phase }

        // Assert
        assertEquals(listOf(RunPhase.CONTINUOUS), phases)
    }

    @Test
    fun `an empty structure has no segments and no duration`() {
        // Arrange
        val structure = RunStructure(warmupSec = 0, main = RunMainBlock.Continuous(0), cooldownSec = 0)

        // Act
        val segments = structure.segments()

        // Assert
        assertEquals(emptyList<RunSegment>(), segments)
        assertEquals(0, structure.totalSec())
    }

    @Test
    fun `a zero work length drops the work segments but keeps the rests between them`() {
        // Arrange
        val structure = RunStructure(warmupSec = 0, main = intervals(repeats = 2, workSec = 0, restSec = 20), cooldownSec = 0)

        // Act
        val segments = structure.segments()

        // Assert
        assertEquals(listOf(RunSegment(RunPhase.REST, 20, repeat = 1, totalRepeats = 2)), segments)
    }
}
