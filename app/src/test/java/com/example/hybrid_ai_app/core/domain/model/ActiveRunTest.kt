package com.example.hybrid_ai_app.core.domain.model

import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.activeRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The run's clock is rebuilt from two anchors, which is what lets it survive a process death. */
class ActiveRunTest {

    @Test
    fun `a running run counts the banked time plus the time since it resumed`() {
        // Arrange
        val run = activeRun(accumulatedMs = 60_000, resumedAt = FIXED_TIMESTAMP)

        // Act
        val elapsed = run.elapsedMs(FIXED_TIMESTAMP + 30_000)

        // Assert
        assertEquals(90_000, elapsed)
        assertFalse(run.isPaused)
    }

    @Test
    fun `a paused run reads only the banked time, however long it has been paused`() {
        // Arrange
        val run = activeRun(accumulatedMs = 60_000, resumedAt = null)

        // Act
        val elapsed = run.elapsedMs(FIXED_TIMESTAMP + 3_600_000)

        // Assert
        assertEquals(60_000, elapsed)
        assertTrue(run.isPaused)
    }

    @Test
    fun `a clock set back never makes the run go backwards`() {
        // Arrange
        val run = activeRun(accumulatedMs = 60_000, resumedAt = FIXED_TIMESTAMP)

        // Act
        val elapsed = run.elapsedMs(FIXED_TIMESTAMP - 5_000)

        // Assert
        assertEquals(60_000, elapsed)
    }

    @Test
    fun `a planned run belongs to its own week and day only`() {
        // Arrange
        val run = activeRun(weekNumber = 2, dayIndex = 4)

        // Act & Assert
        assertTrue(run.isFor(weekNumber = 2, dayIndex = 4, isExtra = false))
        assertFalse(run.isFor(weekNumber = 2, dayIndex = 3, isExtra = false))
        assertFalse(run.isFor(weekNumber = 2, dayIndex = 4, isExtra = true))
    }

    @Test
    fun `an extra run is the extra run, whatever day it was stamped with`() {
        // Arrange
        val run = activeRun(weekNumber = 2, dayIndex = 4, isExtra = true)

        // Act & Assert
        assertTrue(run.isFor(weekNumber = 0, dayIndex = 0, isExtra = true))
        assertFalse(run.isFor(weekNumber = 2, dayIndex = 4, isExtra = false))
    }

    @Test
    fun `the distance of a path is the sum of its legs`() {
        // Arrange: three points 0.001 degrees of latitude apart, about 111 m each
        val path = listOf(RunPoint(40.0, -3.0), RunPoint(40.001, -3.0), RunPoint(40.002, -3.0))

        // Act
        val km = path.distanceKm()

        // Assert
        assertEquals(0.2224, km, 0.001)
    }

    @Test
    fun `a path of fewer than two points has no distance`() {
        // Act & Assert
        assertEquals(0.0, emptyList<RunPoint>().distanceKm(), 0.0)
        assertEquals(0.0, listOf(RunPoint(40.0, -3.0)).distanceKm(), 0.0)
    }
}
