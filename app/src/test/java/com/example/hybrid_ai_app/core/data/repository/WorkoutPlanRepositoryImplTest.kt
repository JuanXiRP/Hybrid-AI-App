package com.example.hybrid_ai_app.core.data.repository

import androidx.room.withTransaction
import com.example.hybrid_ai_app.core.data.local.AppDatabase
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutRunDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutStrengthDto
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.MockWebServerRule
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.userProgressEntity
import com.example.hybrid_ai_app.testing.workoutLogEntity
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection

/**
 * Plan and progress persistence, plus the fire-and-forget push of a finished session.
 *
 * Two pieces of scaffolding are unavoidable here and both are deliberate:
 *
 *  - `database.withTransaction { }` is a suspend extension on `RoomDatabase` that reaches for the
 *    database's own transaction executor, so mocking [AppDatabase] alone is not enough.
 *    `mockkStatic` on the generated `RoomDatabaseKt` facade lets the lambda run inline, which is
 *    what a real transaction does anyway from the caller's point of view.
 *  - the backend sync runs on an injected [TestScope] rather than the inline
 *    `CoroutineScope(Dispatchers.IO)` it used to build itself. That change is what makes
 *    `advanceUntilIdle()` deterministic here; before it, asserting on the sync meant a `coVerify`
 *    with an arbitrary timeout that passed or failed on timing.
 */
class WorkoutPlanRepositoryImplTest {

    @get:Rule
    val server = MockWebServerRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var database: AppDatabase
    private lateinit var planDao: WorkoutPlanDao
    private lateinit var progressDao: ProgressDao

    @Before
    fun setUp() {
        database = mockk(relaxed = true)
        planDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)

        // Run the transaction body inline. Scoped per test and undone in @After so the static
        // mock never leaks into another suite.
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(transaction)) } coAnswers {
            transaction.captured.invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun repository(scope: TestScope) = WorkoutPlanRepositoryImpl(
        database = database,
        planDao = planDao,
        progressDao = progressDao,
        api = server.api(UserApi::class.java),
        syncScope = scope,
    )

    // ------------------------------------------------------------------------------------
    // Reads — straight delegations
    // ------------------------------------------------------------------------------------

    @Test
    fun `the active plan comes from the plan DAO`() = runTest {
        // Arrange
        val plan = workoutPlanEntity()
        every { planDao.getActivePlan() } returns flowOf(plan)

        // Act
        val emitted = repository(this).getActivePlan().first()

        // Assert
        assertEquals(plan, emitted)
    }

    @Test
    fun `progress and logs come from the progress DAO`() = runTest {
        // Arrange
        val progress = userProgressEntity(currentWeekNumber = 3)
        val logs = listOf(workoutLogEntity())
        every { progressDao.getUserProgress() } returns flowOf(progress)
        every { progressDao.getLogsForWeek(3) } returns flowOf(logs)
        every { progressDao.getAllWorkoutLogs() } returns flowOf(logs)
        val repository = repository(this)

        // Act & Assert
        assertEquals(progress, repository.getUserProgress().first())
        assertEquals(logs, repository.getLogsForWeek(3).first())
        assertEquals(logs, repository.getAllWorkoutLogs().first())
    }

    @Test
    fun `updating progress delegates to the DAO`() = runTest {
        // Arrange
        val progress = userProgressEntity(currentWeekNumber = 2, currentDayIndex = 3)

        // Act
        repository(this).updateProgress(progress)

        // Assert
        coVerify(exactly = 1) { progressDao.insertOrUpdateProgress(progress) }
    }

    // ------------------------------------------------------------------------------------
    // toggleDayStatus
    // ------------------------------------------------------------------------------------

    @Test
    fun `toggling an unlogged day inserts a completed log`() = runTest {
        // Arrange
        every { progressDao.getLogsForWeek(1) } returns flowOf(emptyList())
        val inserted = slot<WorkoutLogEntity>()
        coEvery { progressDao.insertWorkoutLog(capture(inserted)) } returns Unit

        // Act
        repository(this).toggleDayStatus(weekNumber = 1, dayIndex = 2)

        // Assert
        assertEquals(1, inserted.captured.weekNumber)
        assertEquals(2, inserted.captured.dayIndex)
        assertTrue(inserted.captured.isCompleted)
        coVerify(exactly = 0) { progressDao.deleteWorkoutLog(any()) }
    }

    @Test
    fun `toggling an already-logged day deletes that log`() = runTest {
        // Arrange
        val existing = workoutLogEntity(id = 7, weekNumber = 1, dayIndex = 2)
        every { progressDao.getLogsForWeek(1) } returns flowOf(listOf(existing))

        // Act
        repository(this).toggleDayStatus(weekNumber = 1, dayIndex = 2)

        // Assert
        coVerify(exactly = 1) { progressDao.deleteWorkoutLog(existing) }
        coVerify(exactly = 0) { progressDao.insertWorkoutLog(any()) }
    }

    @Test
    fun `toggling one day leaves other logged days in the week alone`() = runTest {
        // Arrange
        val otherDay = workoutLogEntity(id = 7, weekNumber = 1, dayIndex = 0)
        every { progressDao.getLogsForWeek(1) } returns flowOf(listOf(otherDay))
        val inserted = slot<WorkoutLogEntity>()
        coEvery { progressDao.insertWorkoutLog(capture(inserted)) } returns Unit

        // Act
        repository(this).toggleDayStatus(weekNumber = 1, dayIndex = 4)

        // Assert
        assertEquals(4, inserted.captured.dayIndex)
        coVerify(exactly = 0) { progressDao.deleteWorkoutLog(any()) }
    }

    // ------------------------------------------------------------------------------------
    // completeWorkout — local write
    // ------------------------------------------------------------------------------------

    @Test
    fun `completing a workout writes the log and the progress in one transaction`() = runTest {
        // Both or neither: a log without the progress bump would re-offer the same day, and a
        // bump without the log would lose the session from history.
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)
        val log = workoutLogEntity()
        val progress = userProgressEntity(currentDayIndex = 1)

        // Act
        repository(this).completeWorkout(log, progress, workoutType = "strength", dayName = "Push")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Any>()) }
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(log) }
        coVerify(exactly = 1) { progressDao.insertOrUpdateProgress(progress) }
    }

    // ------------------------------------------------------------------------------------
    // completeWorkout — remote sync
    // ------------------------------------------------------------------------------------

    @Test
    fun `a strength session is pushed to the strength endpoint`() = runTest {
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)
        val log = workoutLogEntity(
            loggedExercises = listOf(
                loggedExerciseEntity(name = "Back Squat", sets = "4", reps = "6", weight = "100", rpe = "8"),
            ),
        )

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Lower Body",
        )
        advanceUntilIdle()

        // Assert
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(BackendResponses.Routes.WORKOUTS_STRENGTH, request.path)
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<WorkoutStrengthDto>(request.body.readUtf8())
        assertEquals("Lower Body", payload.routineType)
        assertEquals("Back Squat", payload.exercises.single().exerciseName)
        assertEquals(4, payload.exercises.single().sets)
        assertEquals(6, payload.exercises.single().reps)
        assertEquals(100.0, payload.exercises.single().actualWeight!!, 0.0)
    }

    @Test
    fun `the strength payload sends no userId because the backend reads it from the JWT`() = runTest {
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        val body = server.takeRequest().body.readUtf8()
        assertTrue("userId must be omitted, got: $body", !body.contains("\"userId\""))
    }

    @Test
    fun `non-numeric logged values fall back rather than failing the sync`() = runTest {
        // The UI lets the user type freely ("60s", "bodyweight"), so the payload has to cope.
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)
        val log = workoutLogEntity(
            loggedExercises = listOf(
                loggedExerciseEntity(
                    name = "Plank",
                    sets = "three",
                    reps = "60s",
                    weight = "bodyweight",
                    rpe = "hard",
                ),
            ),
        )

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Core",
        )
        advanceUntilIdle()

        // Assert
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<WorkoutStrengthDto>(server.takeRequest().body.readUtf8())
        val exercise = payload.exercises.single()
        assertEquals("sets falls back to 1", 1, exercise.sets)
        assertEquals("reps falls back to 1", 1, exercise.reps)
        assertEquals(0.0, exercise.actualWeight!!, 0.0)
        assertEquals("rpe falls back to 8", 8, exercise.targetRpe)
    }

    @Test
    fun `a cardio session is pushed to the run endpoint`() = runTest {
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)
        val log = workoutLogEntity(
            loggedExercises = listOf(loggedExerciseEntity(rpe = "7")),
        )

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "cardio",
            dayName = "Tempo Run",
        )
        advanceUntilIdle()

        // Assert
        val request = server.takeRequest()
        assertEquals(BackendResponses.Routes.WORKOUTS_RUN, request.path)
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<WorkoutRunDto>(request.body.readUtf8())
        assertEquals("the user's RPE is the only real datum sent", 7, payload.rpe)
    }

    @Test
    fun `a run workoutType also reaches the run endpoint`() = runTest {
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "run",
            dayName = "Easy 5k",
        )
        advanceUntilIdle()

        // Assert
        assertEquals(BackendResponses.Routes.WORKOUTS_RUN, server.takeRequest().path)
    }

    @Test
    fun `the run payload is mostly placeholder data, which is a known gap`() = runTest {
        // Pinned as current behaviour, not as an endorsement: distance, duration and pace are all
        // hardcoded to zero and the GPS path is dropped, so a synced run carries no real metrics.
        // The tracking feature collects this data; wiring it through is separate work.
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "cardio",
            dayName = "Tempo",
        )
        advanceUntilIdle()

        // Assert
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<WorkoutRunDto>(server.takeRequest().body.readUtf8())
        assertEquals(0.0, payload.distance, 0.0)
        assertEquals(0, payload.duration)
        assertEquals(0, payload.targetPace)
        assertTrue(payload.gpsPath.isEmpty())
    }

    @Test
    fun `a rest day is not pushed anywhere`() = runTest {
        // Arrange — nothing enqueued: an unexpected request would fail the assertion below

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "rest",
            dayName = "Recovery",
        )
        advanceUntilIdle()

        // Assert
        assertEquals("no request should have been sent", 0, server.server.requestCount)
    }

    @Test
    fun `a failed sync does not fail the local write`() = runTest {
        // Fire-and-forget by design: the user already saw the session marked complete locally,
        // and a failed push is retried later rather than surfaced as an error.
        // Arrange
        server.enqueueJson(
            BackendResponses.unauthorized(),
            code = HttpURLConnection.HTTP_UNAUTHORIZED,
        )
        val log = workoutLogEntity()

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(log) }
    }

    @Test
    fun `a dropped connection during sync does not propagate`() = runTest {
        // Arrange
        server.enqueueConnectionFailure()
        val log = workoutLogEntity()

        // Act — must not throw
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(log) }
    }

    @Test
    fun `a 402 during sync is swallowed, which is why the client checks entitlement first`() = runTest {
        // The reason HomeViewModel enforces read-only mode itself: a 402 here reaches only a
        // log line, so without the client-side guard the user would believe the session saved.
        // Arrange
        server.enqueueJson(
            BackendResponses.trialExpired(),
            code = HttpURLConnection.HTTP_PAYMENT_REQUIRED,
        )
        val log = workoutLogEntity()

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(log) }
    }

    // ------------------------------------------------------------------------------------
    // clearActivePlanAndProgress
    // ------------------------------------------------------------------------------------

    @Test
    fun `wiping clears the plan, the progress and the logs together`() = runTest {
        // One transaction: a cleared plan with surviving progress would point at a week that no
        // longer exists.
        // Arrange & Act
        repository(this).clearActivePlanAndProgress()

        // Assert
        coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Any>()) }
        coVerify(exactly = 1) { planDao.clearPlan() }
        coVerify(exactly = 1) { progressDao.clearAllProgress() }
        coVerify(exactly = 1) { progressDao.clearAllLogs() }
    }
}
