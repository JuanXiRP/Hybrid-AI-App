package com.example.hybrid_ai_app.home.presentation.run

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.domain.model.CompletedRun
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.model.RunMainBlock
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import com.example.hybrid_ai_app.core.domain.model.RunStructure
import com.example.hybrid_ai_app.core.domain.model.distanceKm
import com.example.hybrid_ai_app.core.domain.repository.ActiveRunRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.core.util.IdProvider
import com.example.hybrid_ai_app.core.util.TimeProvider
import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.activeRun
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.expiredEntitlement
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.trialEntitlement
import com.example.hybrid_ai_app.testing.userProgressEntity
import com.example.hybrid_ai_app.testing.weekDto
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The run screen's owner. The regression this suite exists for: reaching the screen of a run that
 * is already in progress used to restart it from zero. The run is a [FakeActiveRun] holding real
 * state, so the assertions are about what was stored, which is what the tracking service follows.
 */
class RunSessionViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private class FakeClock(var now: Long = FIXED_TIMESTAMP) : TimeProvider {
        override fun nowMillis(): Long = now
    }

    private class FakeActiveRun(initial: ActiveRun? = null) : ActiveRunRepository {
        val current = MutableStateFlow(initial)
        var points: List<RunPoint> = emptyList()
        var starts = 0

        override fun observe() = current

        override suspend fun get() = current.value

        override suspend fun start(run: ActiveRun) {
            starts++
            points = emptyList()
            current.value = run
        }

        override suspend fun points() = points

        override suspend fun addPoint(point: RunPoint) {
            points = points + point
        }

        override suspend fun pause(accumulatedMs: Long) {
            current.value = current.value?.copy(accumulatedMs = accumulatedMs, resumedAt = null)
        }

        override suspend fun resume(atMillis: Long) {
            current.value = current.value?.copy(resumedAt = atMillis)
        }

        override suspend fun clear() {
            current.value = null
            points = emptyList()
        }
    }

    private val weekNumber = 1
    private val dayIndex = 4
    private val runInstruction = exerciseDto(name = "Zone 2 Run", sets = "1", reps = "30 minutes", rpe = "3")
    private val plan = workoutPlanEntity(
        weeks = listOf(
            weekDto(
                weekNumber = weekNumber,
                days = List(7) { index ->
                    if (index == dayIndex) {
                        dayDto(dayName = "Friday - Zone 2 Run", workoutType = "cardio", exercises = listOf(runInstruction))
                    } else {
                        dayDto(dayName = "Day ${index + 1}")
                    }
                },
            ),
        ),
    )
    private val structure = RunStructure(
        warmupSec = 600,
        main = RunMainBlock.Intervals(repeats = 6, workSec = 60, restSec = 60),
        cooldownSec = 300,
    )

    private lateinit var plans: WorkoutPlanRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var entitlementFlow: MutableStateFlow<Entitlement>
    private lateinit var runs: FakeActiveRun
    private val clock = FakeClock()
    private val ids = IdProvider { "run-id" }

    @Before
    fun setUp() {
        plans = mockk(relaxed = true)
        entitlementManager = mockk(relaxed = true)
        entitlementFlow = MutableStateFlow(trialEntitlement())
        runs = FakeActiveRun()
        clock.now = FIXED_TIMESTAMP

        every { entitlementManager.entitlement } returns entitlementFlow
        every { plans.getActivePlan() } returns flowOf(plan)
        coEvery { plans.completeRun(any()) } returns Result.success(Unit)
    }

    private fun plannedHandle() = SavedStateHandle(
        mapOf(RunSessionViewModel.KEY_WEEK to weekNumber, RunSessionViewModel.KEY_DAY to dayIndex),
    )

    private fun extraHandle() = SavedStateHandle(mapOf(RunSessionViewModel.KEY_KIND to RunSessionViewModel.KIND_RUN))

    private fun viewModel(handle: SavedStateHandle = plannedHandle()) = RunSessionViewModel(
        savedStateHandle = handle,
        activeRun = runs,
        plans = plans,
        entitlementManager = entitlementManager,
        time = clock,
        ids = ids,
    )

    // ------------------------------------------------------------------------------------
    // What the screen shows on arrival
    // ------------------------------------------------------------------------------------

    @Test
    fun `with no run in progress the setup comes first`() = runTest {
        // Arrange
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            assertEquals(RunSessionUiState.Loading, awaitItem())
            assertEquals(RunSessionUiState.Setup, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `arriving at a run already in progress shows it as stored and never starts it again`() = runTest {
        // Regression: re-entering the screen (a tab switch, a recreated activity, a reopened app)
        // used to clear the path and restart the clock from zero.
        // Arrange
        val inProgress = activeRun(weekNumber = weekNumber, dayIndex = dayIndex, accumulatedMs = 600_000, startedAt = FIXED_TIMESTAMP - 900_000)
        runs = FakeActiveRun(inProgress)
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            assertEquals(RunSessionUiState.Loading, awaitItem())
            assertEquals(RunSessionUiState.Running(inProgress), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, runs.starts)
    }

    @Test
    fun `a run in progress for another day is a conflict`() = runTest {
        // Arrange
        val other = activeRun(weekNumber = weekNumber, dayIndex = 1)
        runs = FakeActiveRun(other)
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            assertEquals(RunSessionUiState.Conflict(other), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `settling a conflict by resuming opens the other run, by discarding it shows the setup`() = runTest {
        // Arrange
        val other = activeRun(isExtra = true)
        runs = FakeActiveRun(other)
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.events.test {
                vm.resolveConflict(resume = true)
                assertEquals(RunSessionEvent.OpenRun(other), awaitItem())
            }
            vm.resolveConflict(resume = false)
            assertEquals(RunSessionUiState.Setup, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(runs.current.value)
    }

    // ------------------------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------------------------

    @Test
    fun `starting stores the run for the screen's own day, running from now`() = runTest {
        // Arrange
        every { plans.getUserProgress() } returns flowOf(userProgressEntity(currentWeekNumber = 1, currentDayIndex = 1))
        val vm = viewModel()

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.start(structure)
            val running = awaitItem() as RunSessionUiState.Running
            cancelAndIgnoreRemainingEvents()

            // Assert
            assertEquals(
                activeRun(
                    clientId = "run-id",
                    weekNumber = weekNumber,
                    dayIndex = dayIndex,
                    isExtra = false,
                    title = "Friday - Zone 2 Run",
                    structure = structure,
                    startedAt = FIXED_TIMESTAMP,
                    accumulatedMs = 0,
                    resumedAt = FIXED_TIMESTAMP,
                ),
                running.run,
            )
        }
    }

    @Test
    fun `an extra run is stamped with the plan's today and has no title of its own`() = runTest {
        // Arrange
        every { plans.getUserProgress() } returns flowOf(userProgressEntity(currentWeekNumber = 3, currentDayIndex = 2))
        val vm = viewModel(extraHandle())

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.start(structure = null)
            val run = (awaitItem() as RunSessionUiState.Running).run
            cancelAndIgnoreRemainingEvents()

            // Assert
            assertTrue(run.isExtra)
            assertEquals(3, run.weekNumber)
            assertEquals(2, run.dayIndex)
            assertEquals("", run.title)
            assertNull(run.structure)
        }
    }

    @Test
    fun `an expired trial cannot start a run and opens the paywall`() = runTest {
        // Arrange
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()

        // Act & Assert
        vm.events.test {
            vm.start(structure)
            assertEquals(RunSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED), awaitItem())
        }
        advanceUntilIdle()
        assertEquals(0, runs.starts)
    }

    // ------------------------------------------------------------------------------------
    // Pausing
    // ------------------------------------------------------------------------------------

    @Test
    fun `pausing banks the elapsed time and resuming restarts the clock from now`() = runTest {
        // Arrange
        runs = FakeActiveRun(activeRun(weekNumber = weekNumber, dayIndex = dayIndex, accumulatedMs = 0, resumedAt = FIXED_TIMESTAMP))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            awaitItem()
            clock.now = FIXED_TIMESTAMP + 120_000
            vm.pause()
            val paused = (awaitItem() as RunSessionUiState.Running).run
            assertEquals(120_000, paused.accumulatedMs)
            assertTrue(paused.isPaused)

            clock.now = FIXED_TIMESTAMP + 600_000
            vm.resume()
            val resumed = (awaitItem() as RunSessionUiState.Running).run
            assertEquals(FIXED_TIMESTAMP + 600_000, resumed.resumedAt)
            assertEquals("the pause is not counted", 120_000, resumed.elapsedMs(FIXED_TIMESTAMP + 600_000))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------------------------
    // Finishing and discarding
    // ------------------------------------------------------------------------------------

    @Test
    fun `finishing saves the stored time and path against the run's own day and closes`() = runTest {
        // Arrange
        val run = activeRun(clientId = "run-id", weekNumber = weekNumber, dayIndex = dayIndex, resumedAt = FIXED_TIMESTAMP)
        runs = FakeActiveRun(run)
        runs.points = listOf(RunPoint(40.0, -3.0), RunPoint(40.001, -3.0))
        val saved = slot<CompletedRun>()
        coEvery { plans.completeRun(capture(saved)) } returns Result.success(Unit)
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            awaitItem()
            clock.now = FIXED_TIMESTAMP + 1_800_000
            vm.events.test {
                vm.finish()
                assertEquals(RunSessionEvent.Finished, awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("run-id", saved.captured.clientId)
        assertEquals(weekNumber, saved.captured.weekNumber)
        assertEquals(dayIndex, saved.captured.dayIndex)
        assertEquals(1800L, saved.captured.durationSec)
        assertEquals(FIXED_TIMESTAMP + 1_800_000, saved.captured.finishedAt)
        assertEquals(runs.points, saved.captured.path)
        assertEquals(runs.points.distanceKm(), saved.captured.distanceKm, 0.0)
        assertEquals(
            listOf(loggedExerciseEntity(name = "Zone 2 Run", sets = "1", reps = "30 minutes", weight = "", rpe = "3")),
            saved.captured.instruction,
        )
    }

    @Test
    fun `an extra run is saved as extra and with no plan instruction`() = runTest {
        // Arrange
        runs = FakeActiveRun(activeRun(isExtra = true, title = ""))
        val vm = viewModel(extraHandle())

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.finish()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        coVerify(exactly = 1) { plans.completeRun(match { it.isExtra && it.instruction.isEmpty() }) }
    }

    @Test
    fun `a failed save keeps the run on screen and reports it`() = runTest {
        // Arrange
        val run = activeRun(weekNumber = weekNumber, dayIndex = dayIndex)
        runs = FakeActiveRun(run)
        coEvery { plans.completeRun(any()) } returns Result.failure(IllegalStateException("disk full"))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.events.test {
                vm.finish()
                assertEquals(RunSessionEvent.SaveFailed, awaitItem())
            }
            assertEquals(RunSessionUiState.Running(run), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an expired trial cannot finish a run and nothing is written`() = runTest {
        // Arrange
        runs = FakeActiveRun(activeRun(weekNumber = weekNumber, dayIndex = dayIndex))
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.events.test {
                vm.finish()
                assertEquals(RunSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED), awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { plans.completeRun(any()) }
    }

    @Test
    fun `discarding clears the run, even after the trial ended, and closes`() = runTest {
        // Arrange
        runs = FakeActiveRun(activeRun(weekNumber = weekNumber, dayIndex = dayIndex))
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.events.test {
                vm.discard()
                assertEquals(RunSessionEvent.Finished, awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(runs.current.value)
        coVerify(exactly = 0) { plans.completeRun(any()) }
    }
}
