package com.example.hybrid_ai_app.home.presentation

import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.expiredEntitlement
import com.example.hybrid_ai_app.testing.fullWeekDto
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.trialEntitlement
import com.example.hybrid_ai_app.testing.userProgressEntity
import com.example.hybrid_ai_app.testing.weekDto
import com.example.hybrid_ai_app.testing.workoutLogEntity
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
 * The dashboard: which week and day the user is on, and marking a session complete.
 *
 * Two things shape these tests. `uiState` is `stateIn(WhileSubscribed(5000))`, so it must be
 * collected before it leaves `Loading` — and `logCurrentWorkoutAsCompleted` reads `uiState.value`,
 * so the flow has to be live before that call does anything at all. And the read-only check is
 * enforced client-side on purpose: the sync is fire-and-forget, so a backend 402 would be
 * swallowed into a log line and the user would believe the session saved.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var repository: WorkoutPlanRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var entitlementFlow: MutableStateFlow<Entitlement>

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        entitlementManager = mockk(relaxed = true)
        preferencesManager = mockk()
        entitlementFlow = MutableStateFlow(trialEntitlement())
        // A relaxed mock would hand back a mock StateFlow whose .value is itself a mock, and the
        // read-only check would silently never fire.
        every { entitlementManager.entitlement } returns entitlementFlow
        every { preferencesManager.userProfilePicFlow } returns flowOf(null)
    }

    private fun viewModel() = HomeViewModel(repository, entitlementManager, preferencesManager)

    private fun givenPlan(
        plan: com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity? =
            workoutPlanEntity(),
        progress: UserProgressEntity? = userProgressEntity(),
        logs: List<WorkoutLogEntity> = emptyList(),
    ) {
        every { repository.getActivePlan() } returns flowOf(plan)
        every { repository.getUserProgress() } returns flowOf(progress)
        every { repository.getLogsForWeek(any()) } returns flowOf(logs)
    }

    // ------------------------------------------------------------------------------------
    // uiState
    // ------------------------------------------------------------------------------------

    @Test
    fun `the state starts at Loading before anything collects`() = runTest {
        // Arrange
        givenPlan()

        // Act
        val vm = viewModel()

        // Assert
        assertEquals(HomeUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `no cached plan yields Empty`() = runTest {
        // Arrange
        givenPlan(plan = null)
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a plan and progress resolve to the current week and day`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(weekNumber = 1, days = listOf(dayDto(dayName = "W1D1"))),
                weekDto(
                    weekNumber = 2,
                    days = listOf(dayDto(dayName = "W2D1"), dayDto(dayName = "W2D2")),
                ),
            ),
        )
        givenPlan(
            plan = plan,
            progress = userProgressEntity(currentWeekNumber = 2, currentDayIndex = 1),
        )
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertEquals(2, state.currentWeekNumber)
            assertEquals(1, state.currentDayIndex)
            assertEquals(2, state.currentWeek.weekNumber)
            assertEquals("W2D2", state.currentDay!!.dayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no stored progress starts the user at week one day zero`() = runTest {
        // Arrange
        givenPlan(progress = null)
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertEquals(1, state.currentWeekNumber)
            assertEquals(0, state.currentDayIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `progress past the end of the plan falls back to the first week`() = runTest {
        // A finished plan leaves progress pointing at a week that no longer exists. Falling back
        // keeps the dashboard rendering instead of going blank.
        // Arrange
        val plan = workoutPlanEntity(weeks = listOf(weekDto(weekNumber = 1)))
        givenPlan(plan = plan, progress = userProgressEntity(currentWeekNumber = 99))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertEquals(1, state.currentWeek.weekNumber)
            assertEquals("the requested week number is still reported", 99, state.currentWeekNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a plan with no weeks at all yields Empty`() = runTest {
        // Arrange
        givenPlan(plan = workoutPlanEntity(weeks = emptyList()))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day index beyond the week's days leaves currentDay null without failing`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(weekDto(days = listOf(dayDto(dayName = "Only day")))),
        )
        givenPlan(plan = plan, progress = userProgressEntity(currentDayIndex = 5))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertNull(state.currentDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the weekly completion strip always has seven slots`() = runTest {
        // Arrange
        givenPlan(logs = listOf(workoutLogEntity(dayIndex = 0), workoutLogEntity(dayIndex = 3)))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertEquals(7, state.weeklyCompletion.size)
            assertEquals(
                listOf(true, false, false, true, false, false, false),
                state.weeklyCompletion,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a log with an out-of-range day index is ignored rather than crashing`() = runTest {
        // Arrange
        givenPlan(
            logs = listOf(
                workoutLogEntity(dayIndex = 0),
                workoutLogEntity(dayIndex = 7),
                workoutLogEntity(dayIndex = -1),
            ),
        )
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertEquals(7, state.weeklyCompletion.size)
            assertTrue(state.weeklyCompletion[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an incomplete log leaves its day unmarked`() = runTest {
        // Arrange
        givenPlan(logs = listOf(workoutLogEntity(dayIndex = 2, isCompleted = false)))
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val state = awaitItem() as HomeUiState.Success
            assertTrue(state.weeklyCompletion.none { it })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repository failure surfaces as Error`() = runTest {
        // Arrange
        every { repository.getActivePlan() } returns flow { throw IllegalStateException("db gone") }
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            assertEquals(HomeUiState.Error("db gone"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure with no message falls back to the mapping-error copy`() = runTest {
        // Arrange
        every { repository.getActivePlan() } returns flow { throw IllegalStateException() }
        val vm = viewModel()

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            assertEquals(HomeUiState.Error("SSOT mapping error"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------------------------
    // logCurrentWorkoutAsCompleted
    // ------------------------------------------------------------------------------------

    @Test
    fun `completing a workout persists the log and advances to the next day`() = runTest {
        // Arrange
        givenPlan(
            plan = workoutPlanEntity(weeks = listOf(fullWeekDto(weekNumber = 1))),
            progress = userProgressEntity(currentWeekNumber = 1, currentDayIndex = 2),
        )
        val vm = viewModel()
        val log = slot<WorkoutLogEntity>()
        val progress = slot<UserProgressEntity>()
        coEvery {
            repository.completeWorkout(capture(log), capture(progress), any(), any())
        } returns Unit

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.logCurrentWorkoutAsCompleted()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        coVerify(exactly = 1) { repository.completeWorkout(any(), any(), any(), any()) }
        assertEquals(1, log.captured.weekNumber)
        assertEquals(2, log.captured.dayIndex)
        assertTrue(log.captured.isCompleted)
        assertEquals("progress moves to the next day", 3, progress.captured.currentDayIndex)
        assertEquals("still the same week", 1, progress.captured.currentWeekNumber)
    }

    @Test
    fun `finishing day seven rolls over into the next week`() = runTest {
        // Arrange
        givenPlan(
            plan = workoutPlanEntity(
                weeks = listOf(fullWeekDto(weekNumber = 1), fullWeekDto(weekNumber = 2)),
            ),
            progress = userProgressEntity(currentWeekNumber = 1, currentDayIndex = 6),
        )
        val vm = viewModel()
        val progress = slot<UserProgressEntity>()
        coEvery {
            repository.completeWorkout(any(), capture(progress), any(), any())
        } returns Unit

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.logCurrentWorkoutAsCompleted()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(2, progress.captured.currentWeekNumber)
        assertEquals(0, progress.captured.currentDayIndex)
    }

    @Test
    fun `the logged metrics the user typed are persisted`() = runTest {
        // Arrange
        givenPlan()
        val vm = viewModel()
        val metrics = listOf(
            loggedExerciseEntity(name = "Back Squat", weight = "105", rpe = "9"),
        )
        val log = slot<WorkoutLogEntity>()
        coEvery { repository.completeWorkout(capture(log), any(), any(), any()) } returns Unit

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.logCurrentWorkoutAsCompleted(metrics)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(metrics, log.captured.loggedExercises)
    }

    @Test
    fun `the day's workout type and name are passed so the sync can route the request`() = runTest {
        // The repository sends a strength payload or a run payload depending on this string.
        // Arrange
        givenPlan(
            plan = workoutPlanEntity(
                weeks = listOf(
                    weekDto(
                        days = listOf(dayDto(dayName = "Tempo Run", workoutType = "cardio")),
                    ),
                ),
            ),
        )
        val vm = viewModel()
        val workoutType = slot<String>()
        val dayName = slot<String>()
        coEvery {
            repository.completeWorkout(any(), any(), capture(workoutType), capture(dayName))
        } returns Unit

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.logCurrentWorkoutAsCompleted()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals("cardio", workoutType.captured)
        assertEquals("Tempo Run", dayName.captured)
    }

    @Test
    fun `a day the plan cannot resolve falls back to rest so the call still succeeds`() = runTest {
        // Arrange
        givenPlan(
            plan = workoutPlanEntity(
                weeks = listOf(weekDto(days = listOf(dayDto(dayName = "Only day")))),
            ),
            progress = userProgressEntity(currentDayIndex = 4),
        )
        val vm = viewModel()
        val workoutType = slot<String>()
        val dayName = slot<String>()
        coEvery {
            repository.completeWorkout(any(), any(), capture(workoutType), capture(dayName))
        } returns Unit

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.logCurrentWorkoutAsCompleted()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals("rest", workoutType.captured)
        assertEquals("Workout", dayName.captured)
    }

    @Test
    fun `an expired trial blocks the write and opens the paywall instead`() = runTest {
        // The client has to enforce this: the backend sync is fire-and-forget, so a 402 there
        // would be swallowed and the user would think the session saved.
        // Arrange
        givenPlan()
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()

        // Act
        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.logCurrentWorkoutAsCompleted()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(PremiumRequiredReason.TRIAL_EXPIRED, vm.premiumPrompt.value)
        coVerify(exactly = 0) { repository.completeWorkout(any(), any(), any(), any()) }
    }

    @Test
    fun `the read-only check runs before the state check so it fires even with no plan`() = runTest {
        // Arrange
        givenPlan(plan = null)
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()

        // Act
        vm.logCurrentWorkoutAsCompleted()
        advanceUntilIdle()

        // Assert
        assertEquals(PremiumRequiredReason.TRIAL_EXPIRED, vm.premiumPrompt.value)
    }

    @Test
    fun `nothing is written when the dashboard has not resolved a plan yet`() = runTest {
        // Arrange — uiState is never collected, so it stays Loading
        givenPlan()
        val vm = viewModel()

        // Act
        vm.logCurrentWorkoutAsCompleted()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.completeWorkout(any(), any(), any(), any()) }
        assertNull(vm.premiumPrompt.value)
    }

    @Test
    fun `dismissing the paywall clears the prompt`() = runTest {
        // Arrange
        givenPlan(plan = null)
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()
        vm.logCurrentWorkoutAsCompleted()
        advanceUntilIdle()

        // Act
        vm.dismissPremiumPrompt()

        // Assert
        assertNull(vm.premiumPrompt.value)
    }

    @Test
    fun `the premium prompt starts empty`() = runTest {
        // Arrange
        givenPlan()

        // Act
        val vm = viewModel()

        // Assert
        assertNull(vm.premiumPrompt.value)
    }

    // ------------------------------------------------------------------------------------
    // toggleWorkoutCompletion
    // ------------------------------------------------------------------------------------

    @Test
    fun `toggling a day delegates to the repository`() = runTest {
        // Arrange
        givenPlan()
        coEvery { repository.toggleDayStatus(any(), any()) } returns Unit
        val vm = viewModel()

        // Act
        vm.toggleWorkoutCompletion(weekNumber = 3, dayIndex = 4)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { repository.toggleDayStatus(3, 4) }
    }

    @Test
    fun `a failed toggle is swallowed rather than crashing the dashboard`() = runTest {
        // Documenting current behaviour: the catch is empty, so the checkbox silently reverts on
        // the next emission with no message. Worth knowing before someone relies on an error here.
        // Arrange
        givenPlan()
        coEvery { repository.toggleDayStatus(any(), any()) } throws IllegalStateException("locked")
        val vm = viewModel()

        // Act
        vm.toggleWorkoutCompletion(weekNumber = 1, dayIndex = 0)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { repository.toggleDayStatus(1, 0) }
    }

    // ------------------------------------------------------------------------------------
    // Pass-throughs
    // ------------------------------------------------------------------------------------

    @Test
    fun `the entitlement flow is exposed for the trial countdown banner`() = runTest {
        // Arrange
        givenPlan()
        val vm = viewModel()

        // Act
        entitlementFlow.value = trialEntitlement(trialDaysLeft = 3)

        // Assert
        assertEquals(3, vm.entitlement.value.trialDaysLeft)
    }

    @Test
    fun `the profile picture path is exposed straight from preferences`() = runTest {
        // Arrange
        every { preferencesManager.userProfilePicFlow } returns flowOf("/data/avatar.png")
        givenPlan()
        val vm = viewModel()

        // Act & Assert
        vm.localProfilePicPath.test {
            assertEquals("/data/avatar.png", awaitItem())
            awaitComplete()
        }
    }
}
