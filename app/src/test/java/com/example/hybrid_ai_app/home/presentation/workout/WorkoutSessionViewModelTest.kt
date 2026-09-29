package com.example.hybrid_ai_app.home.presentation.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.ExerciseCatalogRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.core.util.IdProvider
import com.example.hybrid_ai_app.core.util.TimeProvider
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.catalogExercise
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.expiredEntitlement
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.loggedSetEntity
import com.example.hybrid_ai_app.testing.trialEntitlement
import com.example.hybrid_ai_app.testing.weekDto
import com.example.hybrid_ai_app.testing.workoutLogEntity
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import com.example.hybrid_ai_app.testing.workoutSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The strength workout screen's brain: creating, resuming and finishing a session, the set table,
 * the rest timer and the exercise management around it.
 *
 * Three things keep these tests deterministic:
 *  - time comes from a [FakeClock] and identifiers from [SequentialIds], both injected;
 *  - the session in progress is a [FakeActiveWorkout] holding real state, so assertions are about
 *    what was persisted rather than which method happened to be called;
 *  - the ViewModel's application-scope writer runs on the test scheduler, so `advanceUntilIdle()` is
 *    all it takes for a queued save to land.
 */
class WorkoutSessionViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private class FakeClock(var now: Long = FIXED_TIMESTAMP) : TimeProvider {
        override fun nowMillis(): Long = now
    }

    private class SequentialIds : IdProvider {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private class FakeActiveWorkout(initial: WorkoutSession? = null) : ActiveWorkoutRepository {
        val current = MutableStateFlow(initial)
        val saves = mutableListOf<WorkoutSession>()
        var clears = 0

        override fun observe() = current

        override suspend fun save(session: WorkoutSession) {
            saves += session
            current.value = session
        }

        override suspend fun clear() {
            clears++
            current.value = null
        }
    }

    private lateinit var plans: WorkoutPlanRepository
    private lateinit var catalog: ExerciseCatalogRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var entitlementFlow: MutableStateFlow<Entitlement>
    private lateinit var activeWorkout: FakeActiveWorkout
    private val clock = FakeClock()
    private val ids = SequentialIds()

    /** A plan whose week 1 has a lower-body day (index 0) and an upper-body day (index 1). */
    private val plan = workoutPlanEntity(
        weeks = listOf(
            weekDto(
                weekNumber = 1,
                days = listOf(
                    dayDto(
                        dayName = "Lower Body",
                        exercises = listOf(
                            exerciseDto(name = "Back Squat", sets = "3", reps = "5", rpe = "8", exerciseId = "Barbell_Squat"),
                            exerciseDto(name = "Leg Curl", sets = "2", reps = "10", rpe = "7", exerciseId = null),
                        ),
                    ),
                    dayDto(
                        dayName = "Upper Body",
                        exercises = listOf(exerciseDto(name = "Bench Press", sets = "3", reps = "8", rpe = "8")),
                    ),
                ),
            ),
            weekDto(weekNumber = 2, days = listOf(dayDto(dayName = "Week Two Day"), dayDto(dayName = "Week Two Second"))),
        ),
    )

    @After
    fun tearDown() {
        applicationScopes.forEach { it.cancel() }
    }

    @Before
    fun setUp() {
        plans = mockk(relaxed = true)
        catalog = mockk(relaxed = true)
        entitlementManager = mockk(relaxed = true)
        entitlementFlow = MutableStateFlow(trialEntitlement())
        activeWorkout = FakeActiveWorkout()
        clock.now = FIXED_TIMESTAMP

        every { entitlementManager.entitlement } returns entitlementFlow
        every { plans.getActivePlan() } returns flowOf(plan)
        coEvery { plans.getLastPerformance(any(), any(), any()) } returns emptyList()
        coEvery { catalog.resolve(any(), any()) } returns null
        coEvery { plans.completeSession(any(), any(), any()) } returns Result.success(Unit)
        coEvery { plans.updateWorkoutLog(any()) } returns Result.success(Unit)
    }

    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(
            mapOf(WorkoutSessionViewModel.KEY_WEEK to 1, WorkoutSessionViewModel.KEY_DAY to 0),
        ),
    ) = WorkoutSessionViewModel(
        savedStateHandle = handle,
        plans = plans,
        activeWorkout = activeWorkout,
        catalog = catalog,
        entitlementManager = entitlementManager,
        time = clock,
        ids = ids,
        appScope = applicationScope(),
    )

    private val applicationScopes = mutableListOf<CoroutineScope>()

    /**
     * A stand-in for the application scope: driven by the test's scheduler, so `advanceUntilIdle()`
     * runs a queued save, but NOT a child of the test scope. The writer loops until the ViewModel
     * is cleared, and a child would keep `runTest` waiting for it forever. (`backgroundScope` is no
     * substitute: `advanceUntilIdle()` deliberately skips work that only it has queued.)
     */
    private fun TestScope.applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)).also { applicationScopes += it }

    private fun editHandle(logId: Long) = SavedStateHandle(mapOf(WorkoutSessionViewModel.KEY_LOG_ID to logId))

    private fun WorkoutSessionViewModel.active(): WorkoutSessionUiState.Active = uiState.value as WorkoutSessionUiState.Active

    private fun WorkoutSessionViewModel.session(): WorkoutSession = active().session

    /** A started view model over a session with two ticked-able exercises, ready to be edited. */
    private fun TestScope.startedViewModel(): WorkoutSessionViewModel {
        val vm = viewModel()
        advanceUntilIdle()
        return vm
    }

    // ------------------------------------------------------------------------------------
    // Creating, resuming, conflicting
    // ------------------------------------------------------------------------------------

    @Test
    fun `with no session in progress one is created from the plan day and persisted`() = runTest {
        // Arrange & Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        val session = vm.session()
        assertEquals("Lower Body", session.title)
        assertEquals(1, session.weekNumber)
        assertEquals(0, session.dayIndex)
        assertEquals(FIXED_TIMESTAMP, session.startedAt)
        assertEquals(listOf("Back Squat", "Leg Curl"), session.exercises.map { it.name })
        assertEquals(listOf(3, 2), session.exercises.map { it.sets.size })
        assertEquals("persisted immediately, so the minimized bar can see it", session, activeWorkout.current.value)
    }

    @Test
    fun `a new session's sets carry the plan's targets as placeholders and nothing else`() = runTest {
        // Arrange & Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        val set = vm.session().exercises.first().sets.first()
        assertEquals("5", set.targetReps)
        assertEquals("8", set.targetRpe)
        assertEquals("", set.weight)
        assertEquals("", set.reps)
        assertFalse(set.completed)
    }

    @Test
    fun `a session already in progress for the same day is resumed, not recreated`() = runTest {
        // Arrange
        val existing = workoutSession(weekNumber = 1, dayIndex = 0, title = "Resumed", startedAt = FIXED_TIMESTAMP - 60_000L)
        activeWorkout = FakeActiveWorkout(existing)

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(existing, vm.session())
    }

    @Test
    fun `a session for a different day is a conflict the athlete must settle`() = runTest {
        // Arrange
        val existing = workoutSession(weekNumber = 1, dayIndex = 1, title = "Upper Body")
        activeWorkout = FakeActiveWorkout(existing)

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        val state = vm.uiState.value as WorkoutSessionUiState.Conflict
        assertEquals(existing, state.existing)
        assertEquals(SessionDay(weekNumber = 1, dayIndex = 0), state.requested)
    }

    @Test
    fun `resolving a conflict by resuming shows the session already in progress`() = runTest {
        // Arrange
        val existing = workoutSession(weekNumber = 1, dayIndex = 1, title = "Upper Body")
        activeWorkout = FakeActiveWorkout(existing)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.resolveConflict(resume = true)
        advanceUntilIdle()

        // Assert
        assertEquals(existing, vm.session())
        assertEquals("nothing was discarded", 0, activeWorkout.clears)
    }

    @Test
    fun `resolving a conflict by discarding drops the old session and starts the requested one`() = runTest {
        // Arrange
        activeWorkout = FakeActiveWorkout(workoutSession(weekNumber = 1, dayIndex = 1, title = "Upper Body"))
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.resolveConflict(resume = false)
        advanceUntilIdle()

        // Assert
        assertEquals(1, activeWorkout.clears)
        assertEquals("Lower Body", vm.session().title)
        assertEquals(0, vm.session().dayIndex)
    }

    @Test
    fun `resolving with no conflict on screen does nothing`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.resolveConflict(resume = false)
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
        assertEquals(0, activeWorkout.clears)
    }

    @Test
    fun `a day the plan does not have is an error`() = runTest {
        // Arrange
        val handle = SavedStateHandle(
            mapOf(WorkoutSessionViewModel.KEY_WEEK to 9, WorkoutSessionViewModel.KEY_DAY to 0),
        )

        // Act
        val vm = viewModel(handle)
        advanceUntilIdle()

        // Assert
        assertEquals(WorkoutSessionUiState.Error(WorkoutSessionError.NO_PLAN_DAY), vm.uiState.value)
        assertTrue(activeWorkout.saves.isEmpty())
    }

    @Test
    fun `no cached plan is an error`() = runTest {
        // Arrange
        every { plans.getActivePlan() } returns flowOf(null)

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(WorkoutSessionUiState.Error(WorkoutSessionError.NO_PLAN_DAY), vm.uiState.value)
    }

    @Test
    fun `a route with neither a day nor a log is an error`() = runTest {
        // Act
        val vm = viewModel(SavedStateHandle())
        advanceUntilIdle()

        // Assert
        assertEquals(WorkoutSessionUiState.Error(WorkoutSessionError.NO_PLAN_DAY), vm.uiState.value)
    }

    @Test
    fun `a lapsed trial cannot start a session, and nothing is written`() = runTest {
        // Arrange
        entitlementFlow.value = expiredEntitlement()

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(WorkoutSessionUiState.Error(WorkoutSessionError.READ_ONLY), vm.uiState.value)
        assertTrue(activeWorkout.saves.isEmpty())
        vm.events.test {
            assertEquals(WorkoutSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a lapsed trial can still resume a session that was already in progress`() = runTest {
        // Arrange — reading what exists writes nothing
        val existing = workoutSession(weekNumber = 1, dayIndex = 0)
        activeWorkout = FakeActiveWorkout(existing)
        entitlementFlow.value = expiredEntitlement()

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(existing, vm.session())
    }

    // ------------------------------------------------------------------------------------
    // Previous performance and catalog entries
    // ------------------------------------------------------------------------------------

    @Test
    fun `each exercise is matched to its previous sets by id and name`() = runTest {
        // Arrange
        val squatSets = listOf(loggedSetEntity(weight = "100", reps = "5"))
        coEvery { plans.getLastPerformance("Barbell_Squat", "Back Squat", any()) } returns squatSets

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        val squatId = vm.session().exercises.first().id
        val curlId = vm.session().exercises.last().id
        assertEquals(squatSets, vm.active().previousByExercise[squatId])
        assertEquals(emptyList<Any>(), vm.active().previousByExercise[curlId])
    }

    @Test
    fun `an exercise that resolves to a catalog entry gets it, and one that does not gets none`() = runTest {
        // Arrange
        val entry = catalogExercise(id = "Barbell_Squat", name = "Barbell Squat")
        coEvery { catalog.resolve("Barbell_Squat", "Back Squat") } returns entry

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        val squatId = vm.session().exercises.first().id
        val curlId = vm.session().exercises.last().id
        assertEquals(entry, vm.active().catalogByExercise[squatId])
        assertNull(vm.active().catalogByExercise[curlId])
    }

    // ------------------------------------------------------------------------------------
    // Sets
    // ------------------------------------------------------------------------------------

    @Test
    fun `typed values land in the right field of the right set`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        val target = exercise.sets[1]

        // Act
        vm.updateSet(exercise.id, target.id, SetField.WEIGHT, "82.5")
        vm.updateSet(exercise.id, target.id, SetField.REPS, "5")
        vm.updateSet(exercise.id, target.id, SetField.ACTUAL_RPE, "9")
        advanceUntilIdle()

        // Assert
        val sets = vm.session().exercises.first().sets
        assertEquals("82.5", sets[1].weight)
        assertEquals("5", sets[1].reps)
        assertEquals("9", sets[1].actualRpe)
        assertEquals("the other sets are untouched", "", sets[0].weight)
        assertEquals("", sets[2].weight)
    }

    @Test
    fun `every change is persisted, and the stored session is the one on screen`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "100")
        advanceUntilIdle()

        // Assert
        assertEquals(vm.session(), activeWorkout.current.value)
    }

    @Test
    fun `a burst of changes writes the latest state without losing it`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        val set = exercise.sets[0]

        // Act — typing "100" one keystroke at a time
        vm.updateSet(exercise.id, set.id, SetField.WEIGHT, "1")
        vm.updateSet(exercise.id, set.id, SetField.WEIGHT, "10")
        vm.updateSet(exercise.id, set.id, SetField.WEIGHT, "100")
        advanceUntilIdle()

        // Assert
        assertEquals("100", activeWorkout.current.value!!.exercises.first().sets[0].weight)
    }

    @Test
    fun `ticking a set starts the exercise's rest of two minutes`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        clock.now = FIXED_TIMESTAMP + 5_000L

        // Act
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()

        // Assert
        val session = vm.session()
        assertTrue(session.exercises.first().sets[0].completed)
        assertEquals(clock.now + 120_000L, session.restEndsAt)
        assertEquals(120, session.restTotalSec)
    }

    @Test
    fun `the rest lasts as long as the exercise says`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.setExerciseRest(exercise.id, 90)

        // Act
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()

        // Assert
        assertEquals(FIXED_TIMESTAMP + 90_000L, vm.session().restEndsAt)
        assertEquals(90, vm.session().restTotalSec)
    }

    @Test
    fun `an exercise with its rest turned off starts no timer`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.setExerciseRest(exercise.id, 0)

        // Act
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()

        // Assert
        assertTrue(vm.session().exercises.first().sets[0].completed)
        assertNull(vm.session().restEndsAt)
    }

    @Test
    fun `unticking a set leaves a running rest alone`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        val restEndsAt = vm.session().restEndsAt

        // Act
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()

        // Assert
        assertFalse(vm.session().exercises.first().sets[0].completed)
        assertEquals(restEndsAt, vm.session().restEndsAt)
    }

    @Test
    fun `ticking an unknown set changes nothing`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.toggleSetCompleted("no-such-exercise", "no-such-set")
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `a new set copies the last set's targets but none of its typed values`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.updateSet(exercise.id, exercise.sets.last().id, SetField.WEIGHT, "100")
        vm.toggleSetCompleted(exercise.id, exercise.sets.last().id)

        // Act
        vm.addSet(exercise.id)
        advanceUntilIdle()

        // Assert
        val sets = vm.session().exercises.first().sets
        assertEquals(4, sets.size)
        assertEquals("5", sets.last().targetReps)
        assertEquals("8", sets.last().targetRpe)
        assertEquals("", sets.last().weight)
        assertFalse(sets.last().completed)
        assertEquals(SetType.NORMAL, sets.last().type)
        assertEquals("set ids stay distinct", 4, sets.map { it.id }.toSet().size)
    }

    @Test
    fun `a set can be added to an exercise that has none`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        exercise.sets.forEach { vm.removeSet(exercise.id, it.id) }

        // Act
        vm.addSet(exercise.id)
        advanceUntilIdle()

        // Assert
        val set = vm.session().exercises.first().sets.single()
        assertEquals("", set.targetReps)
        assertEquals("", set.targetRpe)
    }

    @Test
    fun `removing a set removes only that set`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.removeSet(exercise.id, exercise.sets[1].id)
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(exercise.sets[0].id, exercise.sets[2].id), vm.session().exercises.first().sets.map { it.id })
    }

    @Test
    fun `a set's type can be changed`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.setType(exercise.id, exercise.sets[1].id, SetType.DROP)
        advanceUntilIdle()

        // Assert
        assertEquals(
            listOf(SetType.NORMAL, SetType.DROP, SetType.NORMAL),
            vm.session().exercises.first().sets.map { it.type },
        )
    }

    @Test
    fun `converting a set to a warm-up converts every set before it too`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.convertToWarmupUpTo(exercise.id, exercise.sets[1].id)
        advanceUntilIdle()

        // Assert
        assertEquals(
            listOf(SetType.WARMUP, SetType.WARMUP, SetType.NORMAL),
            vm.session().exercises.first().sets.map { it.type },
        )
    }

    @Test
    fun `converting to a warm-up leaves the values and other exercises alone`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "40")
        val other = vm.session().exercises.last()

        // Act
        vm.convertToWarmupUpTo(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()

        // Assert
        assertEquals("40", vm.session().exercises.first().sets[0].weight)
        assertEquals(other, vm.session().exercises.last())
    }

    @Test
    fun `converting an unknown set changes nothing`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.convertToWarmupUpTo(before.exercises.first().id, "no-such-set")
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    // ------------------------------------------------------------------------------------
    // Rest timer
    // ------------------------------------------------------------------------------------

    private fun TestScope.viewModelWithRunningRest(): WorkoutSessionViewModel {
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `fifteen seconds can be added to a running rest`() = runTest {
        // Arrange
        val vm = viewModelWithRunningRest()
        val endsAt = vm.session().restEndsAt!!

        // Act
        vm.adjustRest(+15)
        advanceUntilIdle()

        // Assert
        assertEquals(endsAt + 15_000L, vm.session().restEndsAt)
        assertEquals(135, vm.session().restTotalSec)
    }

    @Test
    fun `fifteen seconds can be taken off a running rest`() = runTest {
        // Arrange
        val vm = viewModelWithRunningRest()
        val endsAt = vm.session().restEndsAt!!

        // Act
        vm.adjustRest(-15)
        advanceUntilIdle()

        // Assert
        assertEquals(endsAt - 15_000L, vm.session().restEndsAt)
        assertEquals(105, vm.session().restTotalSec)
    }

    @Test
    fun `taking off more than remains ends the rest`() = runTest {
        // Arrange
        val vm = viewModelWithRunningRest()
        clock.now = FIXED_TIMESTAMP + 110_000L // 10 s left

        // Act
        vm.adjustRest(-15)
        advanceUntilIdle()

        // Assert
        assertNull(vm.session().restEndsAt)
        assertNull(vm.session().restTotalSec)
    }

    @Test
    fun `adjusting a rest that is not running does nothing`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.adjustRest(+15)
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `adjusting a rest that already ran out does nothing`() = runTest {
        // Arrange
        val vm = viewModelWithRunningRest()
        clock.now = FIXED_TIMESTAMP + 600_000L
        val before = vm.session()

        // Act
        vm.adjustRest(+15)
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `skipping ends a running rest`() = runTest {
        // Arrange
        val vm = viewModelWithRunningRest()

        // Act
        vm.skipRest()
        advanceUntilIdle()

        // Assert
        assertNull(vm.session().restEndsAt)
        assertNull(vm.session().restTotalSec)
        assertEquals(vm.session(), activeWorkout.current.value)
    }

    @Test
    fun `skipping with no rest running changes nothing`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val savesBefore = activeWorkout.saves.size

        // Act
        vm.skipRest()
        advanceUntilIdle()

        // Assert
        assertEquals(savesBefore, activeWorkout.saves.size)
    }

    @Test
    fun `a manual rest can be started from the timer button`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.startManualRest(45)
        advanceUntilIdle()

        // Assert
        assertEquals(FIXED_TIMESTAMP + 45_000L, vm.session().restEndsAt)
        assertEquals(45, vm.session().restTotalSec)
    }

    @Test
    fun `a manual rest of no time is ignored`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.startManualRest(0)
        advanceUntilIdle()

        // Assert
        assertNull(vm.session().restEndsAt)
    }

    @Test
    fun `a manual rest replaces one already running`() = runTest {
        // Arrange
        val vm = viewModelWithRunningRest()

        // Act
        vm.startManualRest(30)
        advanceUntilIdle()

        // Assert
        assertEquals(FIXED_TIMESTAMP + 30_000L, vm.session().restEndsAt)
        assertEquals(30, vm.session().restTotalSec)
    }

    // ------------------------------------------------------------------------------------
    // Exercises
    // ------------------------------------------------------------------------------------

    @Test
    fun `adding a catalog exercise appends it with three empty sets and no target rpe`() = runTest {
        // Arrange
        coEvery { catalog.getById("Cable_Fly") } returns catalogExercise(id = "Cable_Fly", name = "Cable Fly")
        val vm = startedViewModel()

        // Act
        vm.addExercise("Cable_Fly")
        advanceUntilIdle()

        // Assert
        val added = vm.session().exercises.last()
        assertEquals("Cable Fly", added.name)
        assertEquals("Cable_Fly", added.exerciseId)
        assertEquals(3, added.sets.size)
        assertTrue(added.sets.all { it.targetRpe == "" && it.targetReps == "" && it.weight == "" })
        assertEquals("two planned exercises plus the added one", 3, vm.session().exercises.size)
    }

    @Test
    fun `an added exercise gets its previous sets looked up`() = runTest {
        // Arrange
        val previous = listOf(loggedSetEntity(weight = "30", reps = "12"))
        coEvery { catalog.getById("Cable_Fly") } returns catalogExercise(id = "Cable_Fly", name = "Cable Fly")
        coEvery { plans.getLastPerformance("Cable_Fly", "Cable Fly", any()) } returns previous
        val vm = startedViewModel()

        // Act
        vm.addExercise("Cable_Fly")
        advanceUntilIdle()

        // Assert
        assertEquals(previous, vm.active().previousByExercise[vm.session().exercises.last().id])
    }

    @Test
    fun `adding an id the catalog does not know adds nothing`() = runTest {
        // Arrange
        coEvery { catalog.getById("Nope") } returns null
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.addExercise("Nope")
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `replacing an exercise keeps its sets and targets but clears what was entered`() = runTest {
        // Arrange
        coEvery { catalog.getById("Leg_Press") } returns catalogExercise(id = "Leg_Press", name = "Leg Press")
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.setType(exercise.id, exercise.sets[0].id, SetType.WARMUP)
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "60")
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.REPS, "8")
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.ACTUAL_RPE, "6")
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)

        // Act
        vm.replaceExercise(exercise.id, "Leg_Press")
        advanceUntilIdle()

        // Assert
        val replaced = vm.session().exercises.first()
        assertEquals("Leg Press", replaced.name)
        assertEquals("Leg_Press", replaced.exerciseId)
        assertEquals("the same card, so its position and key hold", exercise.id, replaced.id)
        assertEquals("the set count is kept", 3, replaced.sets.size)
        assertEquals("the set type is kept", SetType.WARMUP, replaced.sets[0].type)
        assertEquals("the targets are kept", "5", replaced.sets[0].targetReps)
        assertEquals("", replaced.sets[0].weight)
        assertEquals("", replaced.sets[0].reps)
        assertEquals("", replaced.sets[0].actualRpe)
        assertFalse(replaced.sets[0].completed)
    }

    @Test
    fun `a replaced exercise gets its previous sets and catalog entry looked up again`() = runTest {
        // Arrange
        val entry = catalogExercise(id = "Leg_Press", name = "Leg Press")
        val previous = listOf(loggedSetEntity(weight = "180", reps = "10"))
        coEvery { catalog.getById("Leg_Press") } returns entry
        coEvery { catalog.resolve("Leg_Press", "Leg Press") } returns entry
        coEvery { plans.getLastPerformance("Leg_Press", "Leg Press", any()) } returns previous
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.replaceExercise(exercise.id, "Leg_Press")
        advanceUntilIdle()

        // Assert
        assertEquals(previous, vm.active().previousByExercise[exercise.id])
        assertEquals(entry, vm.active().catalogByExercise[exercise.id])
    }

    @Test
    fun `replacing with an id the catalog does not know changes nothing`() = runTest {
        // Arrange
        coEvery { catalog.getById("Nope") } returns null
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.replaceExercise(before.exercises.first().id, "Nope")
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `an exercise that no longer resolves loses its stale catalog entry`() = runTest {
        // Arrange — the squat resolved before, the replacement (a name only) does not
        val squat = catalogExercise(id = "Barbell_Squat", name = "Barbell Squat")
        coEvery { catalog.resolve("Barbell_Squat", "Back Squat") } returns squat
        coEvery { catalog.getById("Custom") } returns catalogExercise(id = "Custom", name = "Custom Move")
        coEvery { catalog.resolve("Custom", "Custom Move") } returns null
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        assertEquals(squat, vm.active().catalogByExercise[exercise.id])

        // Act
        vm.replaceExercise(exercise.id, "Custom")
        advanceUntilIdle()

        // Assert
        assertNull(vm.active().catalogByExercise[exercise.id])
    }

    @Test
    fun `removing an exercise removes only that exercise`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val first = vm.session().exercises.first()

        // Act
        vm.removeExercise(first.id)
        advanceUntilIdle()

        // Assert
        assertEquals(listOf("Leg Curl"), vm.session().exercises.map { it.name })
    }

    @Test
    fun `exercises can be reordered`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.moveExercise(from = 0, to = 1)
        advanceUntilIdle()

        // Assert
        assertEquals(listOf("Leg Curl", "Back Squat"), vm.session().exercises.map { it.name })
    }

    @Test
    fun `moving to an impossible position or onto itself changes nothing`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val before = vm.session()

        // Act
        vm.moveExercise(from = 0, to = 5)
        vm.moveExercise(from = -1, to = 0)
        vm.moveExercise(from = 1, to = 1)
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `an exercise note is kept on that exercise`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.setExerciseNotes(exercise.id, "Belt on")
        advanceUntilIdle()

        // Assert
        assertEquals("Belt on", vm.session().exercises.first().notes)
        assertEquals("", vm.session().exercises.last().notes)
    }

    @Test
    fun `an exercise's rest is limited to a sane range`() = runTest {
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()

        // Act
        vm.setExerciseRest(exercise.id, -30)
        val negative = vm.session().exercises.first().restSeconds
        vm.setExerciseRest(exercise.id, 100_000)
        val huge = vm.session().exercises.first().restSeconds

        // Assert
        assertEquals(0, negative)
        assertEquals(3600, huge)
    }

    @Test
    fun `the rest choices offered include off and the two-minute default`() {
        // Act & Assert
        assertEquals(listOf(0, 30, 60, 90, 120, 150, 180, 240, 300), WorkoutSessionViewModel.REST_CHOICES_SECONDS)
    }

    // ------------------------------------------------------------------------------------
    // Catalog queries the sheets make
    // ------------------------------------------------------------------------------------

    @Test
    fun `alternatives are those of the exercise's catalog entry`() = runTest {
        // Arrange
        val squat = catalogExercise(id = "Barbell_Squat", name = "Barbell Squat")
        val press = catalogExercise(id = "Leg_Press", name = "Leg Press")
        coEvery { catalog.resolve("Barbell_Squat", "Back Squat") } returns squat
        coEvery { catalog.alternativesFor("Barbell_Squat") } returns listOf(press)
        val vm = startedViewModel()

        // Act
        val alternatives = vm.alternativesFor(vm.session().exercises.first().id)

        // Assert
        assertEquals(listOf(press), alternatives)
    }

    @Test
    fun `an exercise with no catalog entry has no alternatives to offer`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        val alternatives = vm.alternativesFor(vm.session().exercises.last().id)

        // Assert
        assertTrue(alternatives.isEmpty())
        coVerify(exactly = 0) { catalog.alternativesFor(any(), any()) }
    }

    @Test
    fun `alternatives for an exercise that is not on screen are empty`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act & Assert
        assertTrue(vm.alternativesFor("no-such-exercise").isEmpty())
    }

    @Test
    fun `the search and the muscle list come from the catalog`() = runTest {
        // Arrange
        val results = listOf(catalogExercise(id = "Cable_Fly", name = "Cable Fly"))
        coEvery { catalog.search("fly", "chest") } returns results
        coEvery { catalog.muscles() } returns listOf("chest", "lats")
        val vm = startedViewModel()

        // Act & Assert
        assertEquals(results, vm.searchExercises("fly", "chest"))
        assertEquals(listOf("chest", "lats"), vm.muscles())
    }

    // ------------------------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------------------------

    @Test
    fun `the workout note is kept`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.setWorkoutNotes("Felt strong")
        advanceUntilIdle()

        // Assert
        assertEquals("Felt strong", vm.session().notes)
        assertEquals("Felt strong", activeWorkout.current.value!!.notes)
    }

    @Test
    fun `the start time cannot be moved in a live session`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.setStartedAt(FIXED_TIMESTAMP - 999_000L)
        advanceUntilIdle()

        // Assert
        assertEquals(FIXED_TIMESTAMP, vm.session().startedAt)
    }

    // ------------------------------------------------------------------------------------
    // Finishing
    // ------------------------------------------------------------------------------------

    private fun TestScope.viewModelWithOneTickedSet(): WorkoutSessionViewModel {
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `finishing with unticked sets asks first, and says how many`() = runTest {
        // Arrange — five sets in the plan, one ticked, so four are incomplete
        val vm = viewModelWithOneTickedSet()

        // Act
        vm.requestFinish()

        // Assert
        vm.events.test {
            assertEquals(WorkoutSessionEvent.ConfirmIncomplete(count = 4), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { plans.completeSession(any(), any(), any()) }
    }

    @Test
    fun `finishing with every set ticked finishes straight away`() = runTest {
        // Arrange
        val vm = startedViewModel()
        vm.session().exercises.forEach { exercise -> exercise.sets.forEach { vm.toggleSetCompleted(exercise.id, it.id) } }
        advanceUntilIdle()

        // Act
        vm.requestFinish()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { plans.completeSession(any(), false, any()) }
        vm.events.test {
            assertEquals(WorkoutSessionEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requesting to finish with nothing on screen does nothing`() = runTest {
        // Arrange
        every { plans.getActivePlan() } returns flowOf(null)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.requestFinish()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { plans.completeSession(any(), any(), any()) }
    }

    @Test
    fun `finishing completes the session with every set counted`() = runTest {
        // Arrange
        val vm = viewModelWithOneTickedSet()
        val expected = vm.session()
        clock.now = FIXED_TIMESTAMP + 1_800_000L

        // Act
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { plans.completeSession(expected, false, FIXED_TIMESTAMP + 1_800_000L) }
    }

    @Test
    fun `finishing can discard the unticked sets`() = runTest {
        // Arrange
        val vm = viewModelWithOneTickedSet()
        val expected = vm.session()

        // Act
        vm.finish(discardIncomplete = true)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { plans.completeSession(expected, true, any()) }
    }

    @Test
    fun `the session is logged against the day in the route, not the athlete's progress`() = runTest {
        // Bug fixed here: the log used to be attributed to whichever day the progress row named,
        // so opening another day from the quick-start sheet and finishing it logged the wrong one.
        // Arrange — the route asks for week 2, day 1
        val handle = SavedStateHandle(
            mapOf(WorkoutSessionViewModel.KEY_WEEK to 2, WorkoutSessionViewModel.KEY_DAY to 1),
        )
        val vm = viewModel(handle)
        advanceUntilIdle()
        val logged = slot<WorkoutSession>()
        coEvery { plans.completeSession(capture(logged), any(), any()) } returns Result.success(Unit)

        // Act
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        assertEquals(2, logged.captured.weekNumber)
        assertEquals(1, logged.captured.dayIndex)
        assertEquals("Week Two Second", logged.captured.title)
    }

    @Test
    fun `finishing signals the screen to close`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        vm.events.test {
            assertEquals(WorkoutSessionEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing is written after the session has been finished`() = runTest {
        // A queued snapshot landing after the log is saved and the session cleared would bring the
        // finished session back to life.
        // Arrange
        val vm = startedViewModel()
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()
        val savesAtFinish = activeWorkout.saves.size

        // Act — an action that slips in after finishing
        vm.setWorkoutNotes("too late")
        advanceUntilIdle()

        // Assert
        assertEquals(savesAtFinish, activeWorkout.saves.size)
    }

    @Test
    fun `a failed save keeps the session, reports it, and keeps persisting`() = runTest {
        // Arrange
        coEvery { plans.completeSession(any(), any(), any()) } returns Result.failure(IllegalStateException("disk full"))
        val vm = startedViewModel()

        // Act
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()
        vm.setWorkoutNotes("still here")
        advanceUntilIdle()

        // Assert
        vm.events.test {
            assertEquals(WorkoutSessionEvent.SaveFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("still here", vm.session().notes)
        assertEquals("the session is still being persisted", "still here", activeWorkout.current.value!!.notes)
    }

    @Test
    fun `discarding clears the session and closes the screen`() = runTest {
        // Arrange
        val vm = startedViewModel()

        // Act
        vm.discard()
        advanceUntilIdle()

        // Assert
        assertEquals(1, activeWorkout.clears)
        assertNull(activeWorkout.current.value)
        vm.events.test {
            assertEquals(WorkoutSessionEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { plans.completeSession(any(), any(), any()) }
    }

    // ------------------------------------------------------------------------------------
    // Read-only trial
    // ------------------------------------------------------------------------------------

    @Test
    fun `a lapsed trial turns every change into a paywall and writes nothing`() = runTest {
        // The client enforces this itself: the backend push is detached, so a 402 there would be
        // swallowed and the athlete would believe the workout saved.
        // Arrange
        val vm = startedViewModel()
        val exercise = vm.session().exercises.first()
        val before = vm.session()
        val savesBefore = activeWorkout.saves.size
        entitlementFlow.value = expiredEntitlement()

        // Act
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "100")
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        vm.addSet(exercise.id)
        vm.setWorkoutNotes("nope")
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
        assertEquals(savesBefore, activeWorkout.saves.size)
        vm.events.test {
            repeat(4) {
                assertEquals(WorkoutSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED), awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a lapsed trial cannot finish a workout`() = runTest {
        // Arrange
        val vm = startedViewModel()
        entitlementFlow.value = expiredEntitlement()

        // Act
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { plans.completeSession(any(), any(), any()) }
        vm.events.test {
            assertEquals(WorkoutSessionEvent.ShowPaywall(PremiumRequiredReason.TRIAL_EXPIRED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a lapsed trial cannot add or replace exercises`() = runTest {
        // Arrange
        coEvery { catalog.getById(any()) } returns catalogExercise(id = "Cable_Fly", name = "Cable Fly")
        val vm = startedViewModel()
        val before = vm.session()
        entitlementFlow.value = expiredEntitlement()

        // Act
        vm.addExercise("Cable_Fly")
        vm.replaceExercise(before.exercises.first().id, "Cable_Fly")
        advanceUntilIdle()

        // Assert
        assertEquals(before, vm.session())
    }

    @Test
    fun `a lapsed trial can still discard a draft it can no longer finish`() = runTest {
        // Otherwise a session left from before the trial lapsed would sit in the minimized bar
        // with no way to dismiss it.
        // Arrange
        val vm = startedViewModel()
        entitlementFlow.value = expiredEntitlement()

        // Act
        vm.discard()
        advanceUntilIdle()

        // Assert
        assertEquals(1, activeWorkout.clears)
    }

    @Test
    fun `a lapsed trial cannot discard an existing session to start another`() = runTest {
        // Arrange
        activeWorkout = FakeActiveWorkout(workoutSession(weekNumber = 1, dayIndex = 1))
        val vm = viewModel()
        advanceUntilIdle()
        entitlementFlow.value = expiredEntitlement()

        // Act
        vm.resolveConflict(resume = false)
        advanceUntilIdle()

        // Assert
        assertEquals(0, activeWorkout.clears)
        assertTrue(vm.uiState.value is WorkoutSessionUiState.Conflict)
    }

    // ------------------------------------------------------------------------------------
    // Edit mode
    // ------------------------------------------------------------------------------------

    private fun storedLog(
        id: Long = 7,
        startedAt: Long? = FIXED_TIMESTAMP,
        durationSec: Long? = 3600,
    ): WorkoutLogEntity = workoutLogEntity(
        id = id,
        weekNumber = 1,
        dayIndex = 0,
        timestamp = FIXED_TIMESTAMP + 3_600_000L,
        title = "Lower Body",
        workoutType = "strength",
        startedAt = startedAt,
        durationSec = durationSec,
        loggedExercises = listOf(
            loggedExerciseEntity(
                name = "Back Squat",
                exerciseId = "Barbell_Squat",
                setLogs = listOf(loggedSetEntity(weight = "100", reps = "5"), loggedSetEntity(weight = "105", reps = "3")),
            ),
        ),
    )

    @Test
    fun `a past workout opens as a session in edit mode`() = runTest {
        // Arrange
        val log = storedLog()
        coEvery { plans.getWorkoutLog(7) } returns log

        // Act
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()

        // Assert
        assertTrue(vm.isEditMode)
        assertTrue(vm.active().isEditMode)
        assertEquals(log.clientId, vm.session().clientId)
        assertEquals("Lower Body", vm.session().title)
        assertEquals(listOf("100", "105"), vm.session().exercises.single().sets.map { it.weight })
    }

    @Test
    fun `editing never touches the session in progress`() = runTest {
        // Arrange
        val liveSession = workoutSession(weekNumber = 3, dayIndex = 3, title = "Live One")
        activeWorkout = FakeActiveWorkout(liveSession)
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()
        val exercise = vm.session().exercises.single()

        // Act
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "110")
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        assertEquals(liveSession, activeWorkout.current.value)
        assertTrue(activeWorkout.saves.isEmpty())
        assertEquals(0, activeWorkout.clears)
    }

    @Test
    fun `ticking a set in edit mode starts no rest`() = runTest {
        // Arrange
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()
        val exercise = vm.session().exercises.single()

        // Act
        vm.toggleSetCompleted(exercise.id, exercise.sets[0].id)
        advanceUntilIdle()

        // Assert
        assertNull(vm.session().restEndsAt)
    }

    @Test
    fun `the rest timer is inert in edit mode`() = runTest {
        // Arrange
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()

        // Act
        vm.startManualRest(60)
        vm.adjustRest(15)
        advanceUntilIdle()

        // Assert
        assertNull(vm.session().restEndsAt)
    }

    @Test
    fun `saving an edit updates the stored log under its own row and client id`() = runTest {
        // Arrange
        val log = storedLog()
        coEvery { plans.getWorkoutLog(7) } returns log
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()
        val exercise = vm.session().exercises.single()
        vm.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "110")
        val saved = slot<WorkoutLogEntity>()
        coEvery { plans.updateWorkoutLog(capture(saved)) } returns Result.success(Unit)

        // Act
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        assertEquals(7L, saved.captured.id)
        assertEquals(log.clientId, saved.captured.clientId)
        assertEquals("110", saved.captured.loggedExercises.single().setLogs[0].weight)
        assertEquals(log.timestamp, saved.captured.timestamp)
        coVerify(exactly = 0) { plans.completeSession(any(), any(), any()) }
        vm.events.test {
            assertEquals(WorkoutSessionEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving the start time moves the finish with it and keeps the workout's length`() = runTest {
        // Arrange
        val log = storedLog(durationSec = 3600)
        coEvery { plans.getWorkoutLog(7) } returns log
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()
        val newStart = FIXED_TIMESTAMP - 86_400_000L // the day before
        val saved = slot<WorkoutLogEntity>()
        coEvery { plans.updateWorkoutLog(capture(saved)) } returns Result.success(Unit)

        // Act
        vm.setStartedAt(newStart)
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        assertEquals(newStart, saved.captured.startedAt)
        assertEquals(newStart + 3_600_000L, saved.captured.timestamp)
        assertEquals(3600L, saved.captured.durationSec)
    }

    @Test
    fun `a failed edit save is reported and the edit is kept`() = runTest {
        // Arrange
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        coEvery { plans.updateWorkoutLog(any()) } returns Result.failure(IllegalStateException("locked"))
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()

        // Act
        vm.setWorkoutNotes("keep me")
        vm.finish(discardIncomplete = false)
        advanceUntilIdle()

        // Assert
        vm.events.test {
            assertEquals(WorkoutSessionEvent.SaveFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("keep me", vm.session().notes)
    }

    @Test
    fun `a log that no longer exists is an error`() = runTest {
        // Arrange
        coEvery { plans.getWorkoutLog(99) } returns null

        // Act
        val vm = viewModel(editHandle(99))
        advanceUntilIdle()

        // Assert
        assertEquals(WorkoutSessionUiState.Error(WorkoutSessionError.LOG_NOT_FOUND), vm.uiState.value)
    }

    @Test
    fun `unsaved edits survive the process being killed`() = runTest {
        // The state lives in the SavedStateHandle, which the system restores into a fresh
        // ViewModel after process death — simulated here by handing the same handle to a new one.
        // Arrange
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val handle = editHandle(7)
        val first = viewModel(handle)
        advanceUntilIdle()
        val exercise = first.session().exercises.single()
        first.updateSet(exercise.id, exercise.sets[0].id, SetField.WEIGHT, "999")
        first.setWorkoutNotes("unsaved thought")
        advanceUntilIdle()

        // Act
        val second = viewModel(handle)
        advanceUntilIdle()

        // Assert
        assertEquals("unsaved thought", second.session().notes)
        assertEquals("999", second.session().exercises.single().sets[0].weight)
    }

    @Test
    fun `an unreadable saved edit falls back to the stored log`() = runTest {
        // Arrange
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val handle = editHandle(7).also { it["editSession"] = "{ not json" }

        // Act
        val vm = viewModel(handle)
        advanceUntilIdle()

        // Assert
        assertEquals("Lower Body", vm.session().title)
        assertEquals("100", vm.session().exercises.single().sets[0].weight)
    }

    @Test
    fun `previous sets for an edited workout come from before it started`() = runTest {
        // Arrange
        val log = storedLog(startedAt = FIXED_TIMESTAMP)
        coEvery { plans.getWorkoutLog(7) } returns log

        // Act
        viewModel(editHandle(7))
        advanceUntilIdle()

        // Assert
        coVerify { plans.getLastPerformance("Barbell_Squat", "Back Squat", FIXED_TIMESTAMP) }
    }

    @Test
    fun `discarding an edit just leaves, leaving the stored log and any live session alone`() = runTest {
        // Arrange
        activeWorkout = FakeActiveWorkout(workoutSession())
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()

        // Act
        vm.discard()
        advanceUntilIdle()

        // Assert
        assertEquals(0, activeWorkout.clears)
        vm.events.test {
            assertEquals(WorkoutSessionEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { plans.updateWorkoutLog(any()) }
    }

    @Test
    fun `finishing an edit with unticked sets can discard or complete them`() = runTest {
        // Arrange
        coEvery { plans.getWorkoutLog(7) } returns storedLog()
        val vm = viewModel(editHandle(7))
        advanceUntilIdle()
        val exercise = vm.session().exercises.single()
        vm.toggleSetCompleted(exercise.id, exercise.sets[1].id) // unticks the second stored set
        val saved = slot<WorkoutLogEntity>()
        coEvery { plans.updateWorkoutLog(capture(saved)) } returns Result.success(Unit)

        // Act
        vm.requestFinish()
        vm.finish(discardIncomplete = true)
        advanceUntilIdle()

        // Assert
        assertEquals("only the ticked set is kept", 1, saved.captured.loggedExercises.single().setLogs.size)
        vm.events.test {
            assertEquals(WorkoutSessionEvent.ConfirmIncomplete(count = 1), awaitItem())
            assertEquals(WorkoutSessionEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------------------------

    @Test
    fun `each new session gets its own client id`() = runTest {
        // Arrange
        val first = startedViewModel().session().clientId
        activeWorkout = FakeActiveWorkout()

        // Act
        val second = startedViewModel().session().clientId

        // Assert
        assertNotEquals(first, second)
    }
}
