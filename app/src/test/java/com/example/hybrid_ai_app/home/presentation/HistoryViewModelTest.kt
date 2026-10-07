package com.example.hybrid_ai_app.home.presentation

import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.loggedSetEntity
import com.example.hybrid_ai_app.testing.weekDto
import com.example.hybrid_ai_app.testing.workoutLogEntity
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The performance history list, which joins logged workouts against the active plan.
 *
 * `uiState` is a `stateIn(WhileSubscribed(5000))` flow, so it stays at `Loading` until something
 * collects it — every test here uses Turbine rather than reading `.value`, which would otherwise
 * assert against the initial value and pass regardless of the mapping.
 */
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var repository: WorkoutPlanRepository
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        repository = mockk()
        preferencesManager = mockk()
        every { preferencesManager.userProfilePicFlow } returns flowOf(null)
    }

    private fun viewModel(
        logs: List<WorkoutLogEntity>,
        plan: WorkoutPlanEntity?,
    ): HistoryViewModel {
        every { repository.getAllWorkoutLogs() } returns flowOf(logs)
        every { repository.getActivePlan() } returns flowOf(plan)
        return HistoryViewModel(repository, preferencesManager)
    }

    /** What the ViewModel's own SimpleDateFormat produces, so assertions are zone-independent. */
    private fun expectedDate(timestamp: Long): String = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(timestamp))

    // ------------------------------------------------------------------------------------
    // Empty and error
    // ------------------------------------------------------------------------------------

    @Test
    fun `no logs yields Empty even when a plan exists`() = runTest {
        // Arrange
        val vm = viewModel(logs = emptyList(), plan = workoutPlanEntity())

        // Act & Assert
        vm.uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            assertEquals(HistoryUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logs without a plan are still listed, because they carry their own title`() = runTest {
        // Regenerating a plan clears it while the history stays, so a missing plan must not blank
        // this screen (it used to yield Empty, hiding every log the athlete had).
        // Arrange
        val vm = viewModel(
            logs = listOf(workoutLogEntity(id = 4, title = "Upper Body", workoutType = "strength")),
            plan = null,
        )

        // Act & Assert
        vm.uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals(4L, item.logId)
            assertEquals("Upper Body", item.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repository failure surfaces as Error rather than an empty screen`() = runTest {
        // Arrange
        every { repository.getAllWorkoutLogs() } returns
            kotlinx.coroutines.flow.flow { throw IllegalStateException("db closed") }
        every { repository.getActivePlan() } returns flowOf(workoutPlanEntity())
        val vm = HistoryViewModel(repository, preferencesManager)

        // Act & Assert
        vm.uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            assertEquals(HistoryUiState.Error("db closed"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure with no message falls back to generic copy`() = runTest {
        // Arrange
        every { repository.getAllWorkoutLogs() } returns
            kotlinx.coroutines.flow.flow { throw IllegalStateException() }
        every { repository.getActivePlan() } returns flowOf(workoutPlanEntity())
        val vm = HistoryViewModel(repository, preferencesManager)

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            assertEquals(
                HistoryUiState.Error("Error loading performance history"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------------------------

    @Test
    fun `a logged session takes its title from the matching plan day`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(
                    weekNumber = 2,
                    days = listOf(dayDto(dayName = "Deadlift Day", workoutType = "strength")),
                ),
            ),
        )
        val vm = viewModel(
            logs = listOf(workoutLogEntity(weekNumber = 2, dayIndex = 0)),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals("Deadlift Day", item.title)
            assertEquals(2, item.weekNumber)
            assertFalse(item.isCardio)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a log pointing at a week the plan no longer has still renders`() = runTest {
        // Plans get regenerated while history persists, so a log can outlive its week. Dropping
        // the row or crashing would lose the user's training record.
        // Arrange
        val plan = workoutPlanEntity(weeks = listOf(weekDto(weekNumber = 1)))
        val vm = viewModel(
            logs = listOf(workoutLogEntity(weekNumber = 99, loggedExercises = emptyList())),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertNull("the screen supplies the localized fallback", item.title)
            assertEquals("no names to list, so the screen supplies the localized line", "", item.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cardio day is flagged when nothing was logged`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(days = listOf(dayDto(dayName = "Tempo Run", workoutType = "cardio"))),
            ),
        )
        val vm = viewModel(
            logs = listOf(workoutLogEntity(loggedExercises = emptyList())),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertTrue(item.isCardio)
            assertEquals("", item.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the summary lists up to three exercise names`() = runTest {
        // Arrange
        val vm = viewModel(
            logs = listOf(
                workoutLogEntity(
                    loggedExercises = listOf(
                        loggedExerciseEntity(name = "Squat"),
                        loggedExerciseEntity(name = "Bench"),
                        loggedExerciseEntity(name = "Row"),
                    ),
                ),
            ),
            plan = workoutPlanEntity(),
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals("Squat, Bench, Row", item.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a fourth exercise is elided with an ellipsis rather than overflowing the card`() = runTest {
        // Arrange
        val vm = viewModel(
            logs = listOf(
                workoutLogEntity(
                    loggedExercises = listOf(
                        loggedExerciseEntity(name = "Squat"),
                        loggedExerciseEntity(name = "Bench"),
                        loggedExerciseEntity(name = "Row"),
                        loggedExerciseEntity(name = "Curl"),
                    ),
                ),
            ),
            plan = workoutPlanEntity(),
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals("Squat, Bench, Row...", item.summary)
            assertEquals(4, item.loggedMetrics.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every logged metric is carried through for the expanded card`() = runTest {
        // Arrange
        val vm = viewModel(
            logs = listOf(
                workoutLogEntity(
                    loggedExercises = listOf(
                        loggedExerciseEntity(
                            name = "Squat",
                            sets = "4",
                            reps = "6",
                            weight = "100",
                            rpe = "8",
                        ),
                    ),
                ),
            ),
            plan = workoutPlanEntity(),
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val metric = (awaitItem() as HistoryUiState.Success).items.single()
                .loggedMetrics.single()
            assertEquals("Squat", metric.name)
            assertEquals("4", metric.sets)
            assertEquals("6", metric.reps)
            assertEquals("100", metric.weight)
            assertEquals("8", metric.rpe)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a log's own title wins over the plan day at the same position`() = runTest {
        // The plan may have been regenerated since, so the day now at week/day index can be a
        // different session altogether; the log remembers what it was called.
        // Arrange
        val plan = workoutPlanEntity(weeks = listOf(weekDto(days = listOf(dayDto(dayName = "Brand New Day")))))
        val vm = viewModel(
            logs = listOf(workoutLogEntity(dayIndex = 0, title = "Old Push Day", workoutType = "strength")),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals("Old Push Day", item.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an untitled extra session never borrows the name of the plan day it was done on`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(weeks = listOf(weekDto(days = listOf(dayDto(dayName = "Lower Body")))))
        val vm = viewModel(
            logs = listOf(workoutLogEntity(dayIndex = 0, title = "", workoutType = "cardio", isExtra = true)),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertTrue(item.isExtra)
            assertTrue(item.isCardio)
            assertNull("the screen names it", item.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a log's own type wins over the plan day when deciding if it was cardio`() = runTest {
        // Arrange — the plan day at that position is cardio now, but the log says it was strength
        val plan = workoutPlanEntity(weeks = listOf(weekDto(days = listOf(dayDto(workoutType = "cardio")))))
        val vm = viewModel(
            logs = listOf(workoutLogEntity(title = "Legs", workoutType = "strength")),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertFalse(item.isCardio)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a run log is flagged as cardio`() = runTest {
        // Arrange
        val vm = viewModel(
            logs = listOf(workoutLogEntity(title = "Easy 5k", workoutType = "run", loggedExercises = emptyList())),
            plan = null,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertTrue(item.isCardio)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank title falls back to the plan day`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(weeks = listOf(weekDto(days = listOf(dayDto(dayName = "Plan Day")))))
        val vm = viewModel(logs = listOf(workoutLogEntity(title = "  ")), plan = plan)

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals("Plan Day", item.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a strength log with per-set detail can be edited, a legacy one cannot`() = runTest {
        // Arrange
        val detailed = workoutLogEntity(
            id = 1,
            title = "Upper Body",
            workoutType = "strength",
            loggedExercises = listOf(loggedExerciseEntity(setLogs = listOf(loggedSetEntity()))),
        )
        val legacy = workoutLogEntity(
            id = 2,
            title = "Upper Body",
            workoutType = "strength",
            loggedExercises = listOf(loggedExerciseEntity(setLogs = emptyList())),
        )
        val vm = viewModel(logs = listOf(detailed, legacy), plan = null)

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val items = (awaitItem() as HistoryUiState.Success).items.associateBy { it.logId }
            assertTrue(items.getValue(1).isEditable)
            assertFalse(items.getValue(2).isEditable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cardio log is never editable`() = runTest {
        // Arrange
        val vm = viewModel(
            logs = listOf(
                workoutLogEntity(
                    workoutType = "cardio",
                    loggedExercises = listOf(loggedExerciseEntity(setLogs = listOf(loggedSetEntity()))),
                ),
            ),
            plan = null,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            assertFalse((awaitItem() as HistoryUiState.Success).items.single().isEditable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the notes, duration and per-set detail are carried through for the detail sheet`() = runTest {
        // Arrange
        val sets = listOf(loggedSetEntity(type = "warmup"), loggedSetEntity(type = "normal"))
        val vm = viewModel(
            logs = listOf(
                workoutLogEntity(
                    notes = "Felt strong",
                    durationSec = 3600,
                    loggedExercises = listOf(loggedExerciseEntity(notes = "Paused reps", setLogs = sets)),
                ),
            ),
            plan = null,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals("Felt strong", item.notes)
            assertEquals(3600L, item.durationSec)
            assertEquals("Paused reps", item.loggedMetrics.single().notes)
            assertEquals(sets, item.loggedMetrics.single().setLogs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the day number is one-based for display while the log stores a zero-based index`() = runTest {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(days = listOf(dayDto(dayName = "A"), dayDto(dayName = "B"))),
            ),
        )
        val vm = viewModel(
            logs = listOf(workoutLogEntity(dayIndex = 1)),
            plan = plan,
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals(2, item.dayNumber)
            assertEquals("B", item.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sessions are listed newest first`() = runTest {
        // Arrange — supplied oldest-first on purpose
        val oldest = workoutLogEntity(id = 1, timestamp = FIXED_TIMESTAMP - 172_800_000)
        val middle = workoutLogEntity(id = 2, timestamp = FIXED_TIMESTAMP - 86_400_000)
        val newest = workoutLogEntity(id = 3, timestamp = FIXED_TIMESTAMP)
        val vm = viewModel(
            logs = listOf(oldest, middle, newest),
            plan = workoutPlanEntity(),
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val items = (awaitItem() as HistoryUiState.Success).items
            assertEquals(listOf(3L, 2L, 1L), items.map { it.logId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the timestamp is formatted for display`() = runTest {
        // Asserted against the same formatter the ViewModel uses, so the test does not depend on
        // the machine's locale or time zone.
        // Arrange
        val vm = viewModel(
            logs = listOf(workoutLogEntity(timestamp = FIXED_TIMESTAMP)),
            plan = workoutPlanEntity(),
        )

        // Act & Assert
        vm.uiState.test {
            awaitItem()
            val item = (awaitItem() as HistoryUiState.Success).items.single()
            assertEquals(expectedDate(FIXED_TIMESTAMP), item.formattedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the profile picture path is exposed straight from preferences`() = runTest {
        // Arrange
        every { preferencesManager.userProfilePicFlow } returns flowOf("/data/pic.jpg")
        val vm = viewModel(logs = emptyList(), plan = null)

        // Act & Assert
        vm.localProfilePicPath.test {
            assertEquals("/data/pic.jpg", awaitItem())
            awaitComplete()
        }
    }
}
