package com.example.hybrid_ai_app.home.presentation.run

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.hybrid_ai_app.core.domain.model.RunLimits
import com.example.hybrid_ai_app.core.domain.model.RunMainBlock
import com.example.hybrid_ai_app.core.domain.model.defaultRunStructure
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.weekDto
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RunSetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private val plans = mockk<WorkoutPlanRepository>()

    private val weekNumber = 2
    private val dayIndex = 1
    private val run = exerciseDto(name = "Easy run", sets = "1", reps = "40 min", rpe = "5")
    private val runDay = dayDto(dayName = "Easy Run", workoutType = "cardio", exercises = listOf(run))

    private fun planWith(day: com.example.hybrid_ai_app.core.data.remote.dto.DayDto = runDay) = workoutPlanEntity(
        weeks = listOf(
            weekDto(weekNumber = weekNumber, days = listOf(dayDto(workoutType = "rest", exercises = emptyList()), day)),
        ),
    )

    private fun handle(day: Int = dayIndex) = SavedStateHandle(
        mapOf(RunSetupViewModel.KEY_WEEK to weekNumber, RunSetupViewModel.KEY_DAY to day),
    )

    private fun viewModel(
        savedState: SavedStateHandle = handle(),
        plan: com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity? = planWith(),
    ): RunSetupViewModel {
        every { plans.getActivePlan() } returns flowOf(plan)
        return RunSetupViewModel(savedState, plans)
    }

    private fun RunSetupViewModel.success(): RunSetupUiState.Success = uiState.value as RunSetupUiState.Success

    @Test
    fun `it loads the day with a structure read from the plan`() = runTest {
        // Arrange
        val viewModel = viewModel()

        // Act / Assert
        viewModel.uiState.test {
            assertEquals(RunSetupUiState.Loading, awaitItem())
            val state = awaitItem() as RunSetupUiState.Success
            assertEquals(runDay.dayName, state.dayName)
            assertEquals(run, state.instruction)
            assertEquals(defaultRunStructure(run.name, run.sets, run.reps), state.structure)
        }
    }

    @Test
    fun `a plan with several sets starts in intervals mode`() = runTest {
        // Arrange
        val intervals = dayDto(workoutType = "cardio", exercises = listOf(exerciseDto(name = "Intervals", sets = "5", reps = "1 min")))
        val viewModel = viewModel(plan = planWith(intervals))

        // Act
        advanceUntilIdle()

        // Assert
        val config = viewModel.success().config
        assertEquals(RunMainMode.INTERVALS, config.mode)
        assertEquals(5, config.repeats)
    }

    @Test
    fun `a day that is not in the plan is an error`() = runTest {
        // Arrange
        val viewModel = viewModel(savedState = handle(day = 9))

        // Act
        advanceUntilIdle()

        // Assert
        assertEquals(RunSetupUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `no cached plan is an error`() = runTest {
        // Arrange
        val viewModel = viewModel(plan = null)

        // Act
        advanceUntilIdle()

        // Assert
        assertEquals(RunSetupUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `a failing plan read is an error and not a crash`() = runTest {
        // Arrange
        every { plans.getActivePlan() } returns flow { throw IllegalStateException("db gone") }
        val viewModel = RunSetupViewModel(handle(), plans)

        // Act
        advanceUntilIdle()

        // Assert
        assertEquals(RunSetupUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `an extra run needs no plan day and starts as a free run`() = runTest {
        // Arrange
        val viewModel = viewModel(
            savedState = SavedStateHandle(mapOf(RunSetupViewModel.KEY_KIND to RunSetupViewModel.KIND_RUN)),
            plan = null,
        )

        // Act
        advanceUntilIdle()

        // Assert
        val state = viewModel.success()
        assertTrue(state.isExtra)
        assertNull(state.instruction)
        assertEquals(RunMainMode.FREE, state.config.mode)
        assertNull(state.structure)
        assertTrue(state.canStart)
    }

    @Test
    fun `free mode has no structure and can start with no duration`() = runTest {
        // Arrange
        val viewModel = viewModel()
        advanceUntilIdle()

        // Act
        viewModel.setMode(RunMainMode.FREE)

        // Assert
        val state = viewModel.success()
        assertNull(state.structure)
        assertEquals(0, state.totalSec)
        assertTrue(state.canStart)
    }

    @Test
    fun `setters clamp to the limits`() = runTest {
        // Arrange
        val viewModel = viewModel()
        advanceUntilIdle()

        // Act
        viewModel.setWarmupMin(999)
        viewModel.setCooldownMin(999)
        viewModel.setContinuousMin(0)
        viewModel.setRepeats(0)
        viewModel.setWorkSec(1)
        viewModel.setRestSec(99999)

        // Assert
        val config = viewModel.success().config
        assertEquals(RunLimits.MAX_WARMUP_MIN, config.warmupMin)
        assertEquals(RunLimits.MAX_COOLDOWN_MIN, config.cooldownMin)
        assertEquals(RunLimits.MIN_CONTINUOUS_MIN, config.continuousMin)
        assertEquals(RunLimits.MIN_REPEATS, config.repeats)
        assertEquals(RunLimits.MIN_WORK_SEC, config.workSec)
        assertEquals(RunLimits.MAX_REST_SEC, config.restSec)
    }

    @Test
    fun `a warm-up can be switched off`() = runTest {
        // Arrange
        val viewModel = viewModel()
        advanceUntilIdle()

        // Act
        viewModel.setWarmupMin(-5)

        // Assert
        assertEquals(0, viewModel.success().config.warmupMin)
        assertEquals(0, viewModel.success().structure!!.warmupSec)
    }

    @Test
    fun `switching mode keeps what was set in the other one`() = runTest {
        // Arrange
        val viewModel = viewModel()
        advanceUntilIdle()
        val repeats = 8
        val minutes = 20

        // Act
        viewModel.setMode(RunMainMode.INTERVALS)
        viewModel.setRepeats(repeats)
        viewModel.setMode(RunMainMode.CONTINUOUS)
        viewModel.setContinuousMin(minutes)
        viewModel.setMode(RunMainMode.INTERVALS)

        // Assert
        val config = viewModel.success().config
        assertEquals(repeats, config.repeats)
        assertEquals(minutes, config.continuousMin)
        assertTrue(viewModel.success().structure!!.main is RunMainBlock.Intervals)
    }

    @Test
    fun `the structure follows the config and reports its total`() = runTest {
        // Arrange
        val viewModel = viewModel()
        advanceUntilIdle()

        // Act
        viewModel.setWarmupMin(2)
        viewModel.setCooldownMin(1)
        viewModel.setMode(RunMainMode.INTERVALS)
        viewModel.setRepeats(3)
        viewModel.setWorkSec(60)
        viewModel.setRestSec(30)

        // Assert
        val state = viewModel.success()
        assertEquals(RunMainBlock.Intervals(repeats = 3, workSec = 60, restSec = 30), state.structure!!.main)
        assertEquals(120 + 3 * 60 + 2 * 30 + 60, state.totalSec)
    }

    @Test
    fun `a new view model restores what the athlete had set`() = runTest {
        // Arrange
        val saved = handle()
        val first = viewModel(savedState = saved)
        advanceUntilIdle()
        first.setWarmupMin(3)
        first.setMode(RunMainMode.INTERVALS)
        first.setRepeats(7)

        // Act
        val second = viewModel(savedState = saved)
        advanceUntilIdle()

        // Assert
        assertEquals(first.success().config, second.success().config)
    }

    @Test
    fun `unreadable saved values fall back to the plan defaults`() = runTest {
        // Arrange
        val saved = handle()
        saved["run_setup_config"] = intArrayOf(1, 2, 3)
        val viewModel = viewModel(savedState = saved)

        // Act
        advanceUntilIdle()

        // Assert
        val expected = RunSetupConfig.from(defaultRunStructure(run.name, run.sets, run.reps))
        assertEquals(expected, viewModel.success().config)
    }

    @Test
    fun `a saved mode this build does not know falls back to the plan defaults`() = runTest {
        // Arrange
        val saved = handle()
        saved["run_setup_config"] = intArrayOf(1, 99, 3, 4, 5, 6, 7)
        val viewModel = viewModel(savedState = saved)

        // Act
        advanceUntilIdle()

        // Assert
        val expected = RunSetupConfig.from(defaultRunStructure(run.name, run.sets, run.reps))
        assertEquals(expected, viewModel.success().config)
    }

    @Test
    fun `a setter before the day has loaded is ignored`() = runTest {
        // Arrange
        val viewModel = viewModel()

        // Act
        viewModel.setWarmupMin(3)
        advanceUntilIdle()

        // Assert
        val defaults = RunSetupConfig.from(defaultRunStructure(run.name, run.sets, run.reps))
        assertEquals(defaults, viewModel.success().config)
    }
}
