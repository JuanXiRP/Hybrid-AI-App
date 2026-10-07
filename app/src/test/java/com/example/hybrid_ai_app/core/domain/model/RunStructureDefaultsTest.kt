package com.example.hybrid_ai_app.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RunStructureDefaultsTest {

    // Some plans write minutes with an apostrophe, as in 25 followed by one.
    private val apostrophe = Char(39)

    @Test
    fun `two or more sets become intervals with the work time read from the reps`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Intervals", sets = "4", reps = "2 min")

        // Assert
        assertEquals(RunMainBlock.Intervals(repeats = 4, workSec = 120, restSec = 60), structure.main)
    }

    @Test
    fun `interval work time can be written in seconds`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Intervals", sets = "6", reps = "45 s")

        // Assert
        assertEquals(RunMainBlock.Intervals(repeats = 6, workSec = 45, restSec = 60), structure.main)
    }

    @Test
    fun `interval work time falls back to one minute when the reps give no time`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Intervals", sets = "3", reps = "-")

        // Assert
        assertEquals(RunMainBlock.Intervals(repeats = 3, workSec = 60, restSec = 60), structure.main)
    }

    @Test
    fun `a single set is one continuous run read from the reps`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Easy run", sets = "1", reps = "40 min")

        // Assert
        assertEquals(RunMainBlock.Continuous(2400), structure.main)
    }

    @Test
    fun `the minutes can come from the exercise name`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Zone 2 Run 45 min", sets = "-", reps = "-")

        // Assert
        assertEquals(RunMainBlock.Continuous(2700), structure.main)
    }

    @Test
    fun `an apostrophe counts as minutes`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Easy run 25$apostrophe", sets = "-", reps = "-")

        // Assert
        assertEquals(RunMainBlock.Continuous(1500), structure.main)
    }

    @Test
    fun `nothing readable falls back to thirty minutes`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Run", sets = "-", reps = "-")

        // Assert
        assertEquals(RunMainBlock.Continuous(1800), structure.main)
    }

    @Test
    fun `warm-up and cool-down default to ten and five minutes`() {
        // Arrange / Act
        val structure = defaultRunStructure(name = "Run", sets = "-", reps = "-")

        // Assert
        assertEquals(600, structure.warmupSec)
        assertEquals(300, structure.cooldownSec)
    }

    @Test
    fun `absurd values are clamped to the setup limits`() {
        // Arrange / Act
        val intervals = defaultRunStructure(name = "Run", sets = "50", reps = "999 min")
        val continuous = defaultRunStructure(name = "Run", sets = "1", reps = "999 min")

        // Assert
        assertEquals(RunMainBlock.Intervals(RunLimits.MAX_REPEATS, RunLimits.MAX_WORK_SEC, 60), intervals.main)
        assertEquals(RunMainBlock.Continuous(RunLimits.MAX_CONTINUOUS_MIN * 60), continuous.main)
    }
}
