package com.example.hybrid_ai_app.core.data.repository

import androidx.room.withTransaction
import com.example.hybrid_ai_app.core.data.local.AppDatabase
import com.example.hybrid_ai_app.core.data.local.dao.ActiveWorkoutDao
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.remote.NetworkJson
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutRunDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutStrengthDto
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.FIXED_TIMESTAMP
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.MockWebServerRule
import com.example.hybrid_ai_app.testing.loggedExerciseEntity
import com.example.hybrid_ai_app.testing.loggedSetEntity
import com.example.hybrid_ai_app.testing.sessionExercise
import com.example.hybrid_ai_app.testing.sessionSet
import com.example.hybrid_ai_app.testing.userProgressEntity
import com.example.hybrid_ai_app.testing.workoutLogEntity
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import com.example.hybrid_ai_app.testing.workoutSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection

/**
 * Plan and progress persistence, finishing a session, and the push of a finished log.
 *
 * Two pieces of scaffolding are unavoidable here and both are deliberate:
 *
 *  - `database.withTransaction { }` is a suspend extension on `RoomDatabase` that reaches for the
 *    database's own transaction executor, so mocking [AppDatabase] alone is not enough.
 *    `mockkStatic` on the generated `RoomDatabaseKt` facade lets the lambda run inline, which is
 *    what a real transaction does anyway from the caller's point of view.
 *  - the backend sync runs on an injected [TestScope] rather than an inline
 *    `CoroutineScope(Dispatchers.IO)`. That is what makes `advanceUntilIdle()` deterministic here;
 *    asserting on the sync otherwise means a `coVerify` with an arbitrary timeout that passes or
 *    fails on timing.
 *
 * A strength log is written with `syncPending = true` and pushed by `retryPendingSyncs`, which reads
 * the pending rows back from the DAO. The DAO here is a mock, so [wireLogStore] gives it a small
 * in-memory list to read from; the assertions on `syncPending` are then about what the repository
 * did to the stored rows, not about which mock method it happened to call.
 */
private const val SETTLE_ATTEMPTS = 200
private const val SETTLE_STEP_MILLIS = 10L

class WorkoutPlanRepositoryImplTest {

    @get:Rule
    val server = MockWebServerRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var database: AppDatabase
    private lateinit var planDao: WorkoutPlanDao
    private lateinit var progressDao: ProgressDao
    private lateinit var activeWorkoutDao: ActiveWorkoutDao

    @Before
    fun setUp() {
        database = mockk(relaxed = true)
        planDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        activeWorkoutDao = mockk(relaxed = true)

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
        activeWorkoutDao = activeWorkoutDao,
        api = server.api(UserApi::class.java),
        syncScope = scope,
    )

    /**
     * Backs the mocked [ProgressDao] with a list, for the writes and reads a sync depends on.
     * Returns the list so a test can assert on what ended up stored.
     */
    private fun wireLogStore(vararg initial: WorkoutLogEntity): MutableList<WorkoutLogEntity> {
        val logs = mutableListOf(*initial)
        coEvery { progressDao.insertWorkoutLog(any()) } coAnswers {
            val log = firstArg<WorkoutLogEntity>()
            logs.removeAll { it.clientId == log.clientId }
            logs.add(log)
        }
        coEvery { progressDao.updateWorkoutLog(any()) } coAnswers {
            val log = firstArg<WorkoutLogEntity>()
            logs.replaceAll { if (it.clientId == log.clientId) log else it }
        }
        coEvery { progressDao.getPendingSyncLogs() } coAnswers { logs.filter { it.syncPending } }
        coEvery { progressDao.setSyncPending(any(), any()) } coAnswers {
            val clientId = firstArg<String>()
            val pending = secondArg<Boolean>()
            logs.replaceAll { if (it.clientId == clientId) it.copy(syncPending = pending) else it }
        }
        return logs
    }

    private fun decodeStrength(body: String): WorkoutStrengthDto = NetworkJson.decodeFromString(body)

    /**
     * Waits, in real time, until [condition] holds.
     *
     * The sync is launched on the test scope, but its network call completes on OkHttp's own
     * threads, so `advanceUntilIdle()` (virtual time) returns while the response is still on its
     * way and the follow-up write has not happened yet. Suspending on the default dispatcher is what
     * lets that response arrive and be processed before the assertion runs.
     */
    private suspend fun TestScope.awaitSettled(condition: () -> Boolean) {
        repeat(SETTLE_ATTEMPTS) {
            advanceUntilIdle()
            if (condition()) return
            withContext(Dispatchers.Default) { delay(SETTLE_STEP_MILLIS) }
        }
        fail("the sync did not settle within ${SETTLE_ATTEMPTS * SETTLE_STEP_MILLIS} ms")
    }

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
    fun `a single log is read by id from the progress DAO`() = runTest {
        // Arrange
        val log = workoutLogEntity(id = 12)
        coEvery { progressDao.getWorkoutLogById(12) } returns log

        // Act
        val found = repository(this).getWorkoutLog(12)

        // Assert
        assertEquals(log, found)
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
    fun `each log inserted by toggling gets its own clientId`() = runTest {
        // The clientId is the backend's idempotency key and a unique index in Room, so two toggled
        // days sharing one would collide.
        // Arrange
        every { progressDao.getLogsForWeek(1) } returns flowOf(emptyList())
        val inserted = mutableListOf<WorkoutLogEntity>()
        coEvery { progressDao.insertWorkoutLog(capture(inserted)) } returns Unit
        val repository = repository(this)

        // Act
        repository.toggleDayStatus(weekNumber = 1, dayIndex = 0)
        repository.toggleDayStatus(weekNumber = 1, dayIndex = 1)

        // Assert
        assertTrue(inserted.all { it.clientId.isNotBlank() })
        assertNotEquals(inserted[0].clientId, inserted[1].clientId)
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
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val log = workoutLogEntity()
        val progress = userProgressEntity(currentDayIndex = 1)

        // Act
        repository(this).completeWorkout(log, progress, workoutType = "strength", dayName = "Push")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Any>()) }
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(match { it.clientId == log.clientId }) }
        coVerify(exactly = 1) { progressDao.insertOrUpdateProgress(progress) }
    }

    @Test
    fun `a log without a title or type gets the day it was logged for`() = runTest {
        // History reads these off the log, so they must be stamped even by the two dashboard
        // callers that build a bare log.
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(title = null, workoutType = null),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Lower Body",
        )
        advanceUntilIdle()

        // Assert
        assertEquals("Lower Body", stored.single().title)
        assertEquals("strength", stored.single().workoutType)
    }

    @Test
    fun `a title and type the log already has are kept`() = runTest {
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(title = "Pull Day", workoutType = "strength"),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Something Else",
        )
        advanceUntilIdle()

        // Assert
        assertEquals("Pull Day", stored.single().title)
    }

    @Test
    fun `a rest day is stored as not pending, because nothing is pushed for it`() = runTest {
        // Arrange
        val stored = wireLogStore()

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "rest",
            dayName = "Recovery",
        )
        advanceUntilIdle()

        // Assert
        assertFalse(stored.single().syncPending)
        assertEquals("no request should have been sent", 0, server.server.requestCount)
    }

    // ------------------------------------------------------------------------------------
    // completeWorkout — remote sync
    // ------------------------------------------------------------------------------------

    @Test
    fun `a strength session is pushed with a PUT to its own clientId`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
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
        assertEquals("PUT", request.method)
        assertEquals(BackendResponses.Routes.workoutsStrengthUpsert(log.clientId), request.path)
        val payload = decodeStrength(request.body.readUtf8())
        assertEquals("Lower Body", payload.routineType)
        assertEquals("Back Squat", payload.exercises.single().exerciseName)
        assertEquals(4, payload.exercises.single().sets)
        assertEquals(6, payload.exercises.single().reps)
        assertEquals(100.0, payload.exercises.single().actualWeight!!, 0.0)
    }

    @Test
    fun `the strength payload sends no userId because the backend reads it from the JWT`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

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
    fun `the strength payload carries the moment the session finished, not the moment it synced`() = runTest {
        // A retry hours later must not move the log to the wrong day on the backend.
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val log = workoutLogEntity(timestamp = FIXED_TIMESTAMP)

        // Act
        repository(this).completeWorkout(log, userProgressEntity(), workoutType = "strength", dayName = "Push")
        advanceUntilIdle()

        // Assert
        val payload = decodeStrength(server.takeRequest().body.readUtf8())
        assertEquals("2026-09-18T10:00:00Z", payload.date)
    }

    @Test
    fun `non-numeric logged values fall back rather than failing the sync`() = runTest {
        // The UI lets the user type freely ("60s", "bodyweight"), so the payload has to cope.
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
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
        val exercise = decodeStrength(server.takeRequest().body.readUtf8()).exercises.single()
        assertEquals("sets falls back to 1", 1, exercise.sets)
        assertEquals("reps falls back to 1", 1, exercise.reps)
        assertEquals(0.0, exercise.actualWeight!!, 0.0)
        assertEquals("the target rpe falls back to 8", 8, exercise.targetRpe)
    }

    @Test
    fun `a legacy log never presents its planned rpe as the rpe that was felt`() = runTest {
        // Bug fixed here: the summary `rpe` of a legacy log is the plan's TARGET, and it used to be
        // sent as both target and actual, so the coach was told every session landed on plan.
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val log = workoutLogEntity(loggedExercises = listOf(loggedExerciseEntity(rpe = "8")))

        // Act
        repository(this).completeWorkout(log, userProgressEntity(), workoutType = "strength", dayName = "Push")
        advanceUntilIdle()

        // Assert
        val exercise = decodeStrength(server.takeRequest().body.readUtf8()).exercises.single()
        assertEquals(8, exercise.targetRpe)
        assertNull(exercise.actualRpe)
    }

    @Test
    fun `a strength session names the planned day it closed`() = runTest {
        // Without these the backend can only count how many sessions exist this week, so the AI
        // coach has to guess which one is next. With them the link is exact.
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val log = workoutLogEntity(weekNumber = 3, dayIndex = 2)

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Lower Body",
        )
        advanceUntilIdle()

        // Assert
        val payload = decodeStrength(server.takeRequest().body.readUtf8())
        assertEquals(3, payload.weekNumber)
        assertEquals(2, payload.dayIndex)
    }

    @Test
    fun `a strength log stays pending until the backend confirms it`() = runTest {
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        // Assert — before the sync has run, the log is already saved locally and pending
        assertTrue(stored.single().syncPending)

        // Act
        awaitSettled { !stored.single().syncPending }

        // Assert — the confirmation clears it
        assertFalse(stored.single().syncPending)
    }

    @Test
    fun `a cardio session names the planned day it closed`() = runTest {
        // Arrange
        server.enqueueEmpty(HttpURLConnection.HTTP_CREATED)
        val log = workoutLogEntity(weekNumber = 2, dayIndex = 4)

        // Act
        repository(this).completeWorkout(
            log,
            userProgressEntity(),
            workoutType = "cardio",
            dayName = "Tempo Run",
        )
        advanceUntilIdle()

        // Assert
        val payload = NetworkJson.decodeFromString<WorkoutRunDto>(server.takeRequest().body.readUtf8())
        assertEquals(2, payload.weekNumber)
        assertEquals(4, payload.dayIndex)
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
        assertEquals("POST", request.method)
        assertEquals(BackendResponses.Routes.WORKOUTS_RUN, request.path)
        val payload = NetworkJson.decodeFromString<WorkoutRunDto>(request.body.readUtf8())
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
        val payload = NetworkJson.decodeFromString<WorkoutRunDto>(server.takeRequest().body.readUtf8())
        assertEquals(0.0, payload.distance, 0.0)
        assertEquals(0, payload.duration)
        assertEquals(0, payload.targetPace)
        assertTrue(payload.gpsPath.isEmpty())
    }

    @Test
    fun `a failed cardio push does not fail the local write`() = runTest {
        // Arrange
        server.enqueueConnectionFailure()
        val log = workoutLogEntity()

        // Act — must not throw
        repository(this).completeWorkout(log, userProgressEntity(), workoutType = "cardio", dayName = "Run")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(match { it.clientId == log.clientId }) }
    }

    @Test
    fun `a rejected cardio push is logged and swallowed`() = runTest {
        // Arrange
        server.enqueueJson(BackendResponses.unauthorized(), code = HttpURLConnection.HTTP_UNAUTHORIZED)
        val log = workoutLogEntity()

        // Act — must not throw
        repository(this).completeWorkout(log, userProgressEntity(), workoutType = "cardio", dayName = "Run")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { progressDao.insertWorkoutLog(match { it.clientId == log.clientId }) }
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
    fun `a rejected strength push keeps the log pending and does not fail the local write`() = runTest {
        // Fire-and-forget by design: the user already saw the session marked complete locally,
        // and a failed push is retried later rather than surfaced as an error.
        // Arrange
        val stored = wireLogStore()
        server.enqueueJson(
            BackendResponses.unauthorized(),
            code = HttpURLConnection.HTTP_UNAUTHORIZED,
        )

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        assertEquals(1, stored.size)
        assertTrue("still pending, so a later trigger retries it", stored.single().syncPending)
    }

    @Test
    fun `a dropped connection during sync keeps the log pending and does not propagate`() = runTest {
        // Arrange
        val stored = wireLogStore()
        server.enqueueConnectionFailure()

        // Act — must not throw
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        assertTrue(stored.single().syncPending)
    }

    @Test
    fun `a 402 during sync is swallowed and retried later, which is why the client checks entitlement first`() = runTest {
        // The reason the ViewModels enforce read-only mode themselves: a 402 here reaches only a
        // log line, so without the client-side guard the user would believe the session saved.
        // Arrange
        val stored = wireLogStore()
        server.enqueueJson(
            BackendResponses.trialExpired(),
            code = HttpURLConnection.HTTP_PAYMENT_REQUIRED,
        )

        // Act
        repository(this).completeWorkout(
            workoutLogEntity(),
            userProgressEntity(),
            workoutType = "strength",
            dayName = "Push",
        )
        advanceUntilIdle()

        // Assert
        assertTrue(stored.single().syncPending)
    }

    // ------------------------------------------------------------------------------------
    // completeSession
    // ------------------------------------------------------------------------------------

    @Test
    fun `finishing a session writes the log, moves progress and clears the session in one transaction`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        coEvery { progressDao.getProgress() } returns userProgressEntity(currentWeekNumber = 1, currentDayIndex = 0)
        val session = workoutSession(weekNumber = 1, dayIndex = 0)

        // Act
        val result = repository(this).completeSession(session, discardIncomplete = false, finishedAt = FIXED_TIMESTAMP)
        advanceUntilIdle()

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Any>()) }
        coVerifyOrder {
            progressDao.insertWorkoutLog(match { it.clientId == session.clientId })
            progressDao.insertOrUpdateProgress(any())
            activeWorkoutDao.clear()
        }
    }

    @Test
    fun `the log is attributed to the session's own week and day, not to the athlete's progress`() = runTest {
        // Bug fixed here: the quick-start sheet lets any pending day of the week be opened, but the
        // log used to be written against whichever day the progress row pointed at.
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        coEvery { progressDao.getProgress() } returns userProgressEntity(currentWeekNumber = 1, currentDayIndex = 0)
        val session = workoutSession(weekNumber = 2, dayIndex = 4)

        // Act
        repository(this).completeSession(session, discardIncomplete = false, finishedAt = FIXED_TIMESTAMP)
        advanceUntilIdle()

        // Assert
        assertEquals(2, stored.single().weekNumber)
        assertEquals(4, stored.single().dayIndex)
    }

    @Test
    fun `finishing a day other than the current one does not move progress`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        coEvery { progressDao.getProgress() } returns userProgressEntity(currentWeekNumber = 1, currentDayIndex = 0)

        // Act
        repository(this).completeSession(
            workoutSession(weekNumber = 1, dayIndex = 3),
            discardIncomplete = false,
            finishedAt = FIXED_TIMESTAMP,
        )
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { progressDao.insertOrUpdateProgress(any()) }
    }

    @Test
    fun `finishing the current day advances progress to the next day`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val progress = userProgressEntity(userId = "athlete", currentWeekNumber = 2, currentDayIndex = 2)
        coEvery { progressDao.getProgress() } returns progress
        val written = slot<UserProgressEntity>()
        coEvery { progressDao.insertOrUpdateProgress(capture(written)) } returns Unit

        // Act
        repository(this).completeSession(
            workoutSession(weekNumber = 2, dayIndex = 2),
            discardIncomplete = false,
            finishedAt = FIXED_TIMESTAMP,
        )
        advanceUntilIdle()

        // Assert
        assertEquals("athlete", written.captured.userId)
        assertEquals(2, written.captured.currentWeekNumber)
        assertEquals(3, written.captured.currentDayIndex)
    }

    @Test
    fun `finishing the last day of the week rolls over to day 0 of the next week`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        coEvery { progressDao.getProgress() } returns userProgressEntity(currentWeekNumber = 2, currentDayIndex = 6)
        val written = slot<UserProgressEntity>()
        coEvery { progressDao.insertOrUpdateProgress(capture(written)) } returns Unit

        // Act
        repository(this).completeSession(
            workoutSession(weekNumber = 2, dayIndex = 6),
            discardIncomplete = false,
            finishedAt = FIXED_TIMESTAMP,
        )
        advanceUntilIdle()

        // Assert
        assertEquals(3, written.captured.currentWeekNumber)
        assertEquals(0, written.captured.currentDayIndex)
    }

    @Test
    fun `an athlete with no progress row is treated as being on week 1 day 0`() = runTest {
        // The dashboard reads a missing row the same way, so the two must agree.
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        coEvery { progressDao.getProgress() } returns null
        val written = slot<UserProgressEntity>()
        coEvery { progressDao.insertOrUpdateProgress(capture(written)) } returns Unit

        // Act
        repository(this).completeSession(
            workoutSession(weekNumber = 1, dayIndex = 0),
            discardIncomplete = false,
            finishedAt = FIXED_TIMESTAMP,
        )
        advanceUntilIdle()

        // Assert
        assertEquals("active_plan", written.captured.userId)
        assertEquals(1, written.captured.currentWeekNumber)
        assertEquals(1, written.captured.currentDayIndex)
    }

    @Test
    fun `discarding incomplete sets logs only the ticked ones`() = runTest {
        // Bug fixed here: every planned exercise used to be logged, ticked or not.
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(
                    name = "Bench Press",
                    sets = listOf(
                        sessionSet(weight = "80", reps = "5", completed = true),
                        sessionSet(weight = "80", reps = "5", completed = false),
                    ),
                ),
                sessionExercise(name = "Untouched", sets = listOf(sessionSet(), sessionSet())),
            ),
        )

        // Act
        repository(this).completeSession(session, discardIncomplete = true, finishedAt = FIXED_TIMESTAMP)
        advanceUntilIdle()

        // Assert
        val exercises = stored.single().loggedExercises
        assertEquals(listOf("Bench Press"), exercises.map { it.name })
        assertEquals(1, exercises.single().setLogs.size)
    }

    @Test
    fun `completing all sets logs every set as performed`() = runTest {
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val session = workoutSession(
            exercises = listOf(
                sessionExercise(
                    sets = listOf(sessionSet(completed = true), sessionSet(completed = false)),
                ),
            ),
        )

        // Act
        repository(this).completeSession(session, discardIncomplete = false, finishedAt = FIXED_TIMESTAMP)
        advanceUntilIdle()

        // Assert
        val sets = stored.single().loggedExercises.single().setLogs
        assertEquals(2, sets.size)
        assertTrue(sets.all { it.completed })
    }

    @Test
    fun `a finished session is pushed as one PUT carrying the per-set detail`() = runTest {
        // Arrange
        wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        val session = workoutSession(
            title = "Upper Body",
            startedAt = FIXED_TIMESTAMP,
            notes = "Felt strong",
            exercises = listOf(
                sessionExercise(
                    exerciseId = "Barbell_Bench_Press",
                    name = "Bench Press",
                    sets = listOf(
                        sessionSet(type = SetType.WARMUP, weight = "40", reps = "10", completed = true),
                        sessionSet(weight = "80", reps = "5", targetRpe = "8", actualRpe = "9", completed = true),
                        sessionSet(weight = "82.5", reps = "4", targetRpe = "8", actualRpe = "10", completed = true),
                    ),
                ),
            ),
        )

        // Act
        repository(this).completeSession(
            session,
            discardIncomplete = true,
            finishedAt = FIXED_TIMESTAMP + 3_600_000L,
        )
        advanceUntilIdle()

        // Assert
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(BackendResponses.Routes.workoutsStrengthUpsert(session.clientId), request.path)

        val payload = decodeStrength(request.body.readUtf8())
        assertEquals("Upper Body", payload.routineType)
        assertEquals(3600L, payload.durationSec)
        assertEquals("Felt strong", payload.notes)
        assertEquals("2026-09-18T10:00:00Z", payload.startedAt)

        val exercise = payload.exercises.single()
        assertEquals("Barbell_Bench_Press", exercise.exerciseId)
        assertEquals("the warm-up is not a working set", 2, exercise.sets)
        assertEquals("the reps of the last working set", 4, exercise.reps)
        assertEquals(82.5, exercise.actualWeight!!, 0.0)
        assertEquals("the mean of 9 and 10, rounded", 10, exercise.actualRpe)
        assertEquals(listOf("warmup", "normal", "normal"), exercise.setLogs!!.map { it.type })
    }

    @Test
    fun `finishing a session clears its pending flag once the backend confirms`() = runTest {
        // Arrange
        val stored = wireLogStore()
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).completeSession(workoutSession(), discardIncomplete = false, finishedAt = FIXED_TIMESTAMP)
        awaitSettled { stored.none { it.syncPending } }

        // Assert
        assertFalse(stored.single().syncPending)
    }

    @Test
    fun `a failed local write is reported and nothing is pushed`() = runTest {
        // Arrange
        wireLogStore()
        coEvery { progressDao.insertWorkoutLog(any()) } throws IllegalStateException("disk full")

        // Act
        val result = repository(this).completeSession(
            workoutSession(),
            discardIncomplete = false,
            finishedAt = FIXED_TIMESTAMP,
        )
        advanceUntilIdle()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(0, server.server.requestCount)
        coVerify(exactly = 0) { activeWorkoutDao.clear() }
    }

    // ------------------------------------------------------------------------------------
    // updateWorkoutLog
    // ------------------------------------------------------------------------------------

    @Test
    fun `editing a log saves it, marks it pending and pushes it under the same clientId`() = runTest {
        // Arrange
        val original = workoutLogEntity(
            id = 5,
            title = "Upper Body",
            workoutType = "strength",
            loggedExercises = listOf(
                loggedExerciseEntity(setLogs = listOf(loggedSetEntity(weight = "80", reps = "5"))),
            ),
        )
        val stored = wireLogStore(original)
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        val result = repository(this).updateWorkoutLog(original.copy(notes = "Edited"))
        awaitSettled { stored.none { it.syncPending } }

        // Assert
        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(BackendResponses.Routes.workoutsStrengthUpsert(original.clientId), request.path)
        assertEquals("Edited", decodeStrength(request.body.readUtf8()).notes)
        assertEquals("Edited", stored.single().notes)
        assertFalse("confirmed by the backend", stored.single().syncPending)
    }

    @Test
    fun `editing a log never touches progress`() = runTest {
        // Arrange
        val original = workoutLogEntity(id = 5)
        wireLogStore(original)
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).updateWorkoutLog(original)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { progressDao.insertOrUpdateProgress(any()) }
        coVerify(exactly = 0) { progressDao.updateProgress(any()) }
    }

    @Test
    fun `a failed edit write is reported`() = runTest {
        // Arrange
        wireLogStore()
        coEvery { progressDao.updateWorkoutLog(any()) } throws IllegalStateException("locked")

        // Act
        val result = repository(this).updateWorkoutLog(workoutLogEntity(id = 5))
        advanceUntilIdle()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(0, server.server.requestCount)
    }

    // ------------------------------------------------------------------------------------
    // getLastPerformance
    // ------------------------------------------------------------------------------------

    @Test
    fun `the previous sets are found by catalog id`() = runTest {
        // Arrange
        val sets = listOf(loggedSetEntity(weight = "80", reps = "5"), loggedSetEntity(weight = "85", reps = "3"))
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns listOf(
            workoutLogEntity(
                loggedExercises = listOf(
                    loggedExerciseEntity(name = "Some Other Name", exerciseId = "Barbell_Squat", setLogs = sets),
                ),
            ),
        )

        // Act
        val previous = repository(this).getLastPerformance(exerciseId = "Barbell_Squat", name = "Back Squat")

        // Assert
        assertEquals(sets, previous)
    }

    @Test
    fun `an id mismatch is not rescued by a matching name`() = runTest {
        // Two catalog exercises can share a display name in the athlete's mind; when both sides
        // carry an id, the id is the answer.
        // Arrange
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns listOf(
            workoutLogEntity(
                loggedExercises = listOf(
                    loggedExerciseEntity(
                        name = "Back Squat",
                        exerciseId = "Front_Squat",
                        setLogs = listOf(loggedSetEntity()),
                    ),
                ),
            ),
        )

        // Act
        val previous = repository(this).getLastPerformance(exerciseId = "Barbell_Squat", name = "Back Squat")

        // Assert
        assertTrue(previous.isEmpty())
    }

    @Test
    fun `the previous sets fall back to the name when the log has no id`() = runTest {
        // Arrange
        val sets = listOf(loggedSetEntity(weight = "60", reps = "8"))
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns listOf(
            workoutLogEntity(
                loggedExercises = listOf(loggedExerciseEntity(name = "  back squat ", exerciseId = null, setLogs = sets)),
            ),
        )

        // Act
        val previous = repository(this).getLastPerformance(exerciseId = "Barbell_Squat", name = "Back Squat")

        // Assert
        assertEquals(sets, previous)
    }

    @Test
    fun `a legacy log yields one synthesised set from its single weight`() = runTest {
        // Arrange
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns listOf(
            workoutLogEntity(
                loggedExercises = listOf(loggedExerciseEntity(name = "Back Squat", weight = "100", reps = "6")),
            ),
        )

        // Act
        val previous = repository(this).getLastPerformance(exerciseId = null, name = "Back Squat")

        // Assert
        val set = previous.single()
        assertEquals("100", set.weight)
        assertEquals("6", set.reps)
        assertTrue(set.completed)
    }

    @Test
    fun `a legacy log with no weight is skipped in favour of an older one`() = runTest {
        // A blank weight tells the athlete nothing, so the search keeps going back.
        // Arrange
        val older = listOf(loggedSetEntity(weight = "70", reps = "8"))
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns listOf(
            workoutLogEntity(loggedExercises = listOf(loggedExerciseEntity(name = "Back Squat", weight = ""))),
            workoutLogEntity(loggedExercises = listOf(loggedExerciseEntity(name = "Back Squat", setLogs = older))),
        )

        // Act
        val previous = repository(this).getLastPerformance(exerciseId = null, name = "Back Squat")

        // Assert
        assertEquals(older, previous)
    }

    @Test
    fun `an exercise never performed has no previous sets`() = runTest {
        // Arrange
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns listOf(
            workoutLogEntity(loggedExercises = listOf(loggedExerciseEntity(name = "Deadlift"))),
        )

        // Act
        val previous = repository(this).getLastPerformance(exerciseId = null, name = "Back Squat")

        // Assert
        assertTrue(previous.isEmpty())
    }

    @Test
    fun `only logs older than the given moment are considered`() = runTest {
        // Editing a past workout must compare against what came before it, not against itself.
        // Arrange
        coEvery { progressDao.getRecentWorkoutLogs(any(), any()) } returns emptyList()

        // Act
        repository(this).getLastPerformance(exerciseId = null, name = "Back Squat", beforeTimestamp = FIXED_TIMESTAMP)

        // Assert
        coVerify(exactly = 1) { progressDao.getRecentWorkoutLogs(FIXED_TIMESTAMP, any()) }
    }

    // ------------------------------------------------------------------------------------
    // retryPendingSyncs
    // ------------------------------------------------------------------------------------

    @Test
    fun `retrying pushes every pending log and clears each flag`() = runTest {
        // Arrange
        val first = workoutLogEntity(id = 1, syncPending = true)
        val second = workoutLogEntity(id = 2, syncPending = true)
        val stored = wireLogStore(first, second)
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).retryPendingSyncs()

        // Assert
        assertEquals(2, server.server.requestCount)
        assertTrue(stored.none { it.syncPending })
    }

    @Test
    fun `retrying with nothing pending sends nothing`() = runTest {
        // Arrange
        wireLogStore(workoutLogEntity(syncPending = false))

        // Act
        repository(this).retryPendingSyncs()

        // Assert
        assertEquals(0, server.server.requestCount)
    }

    @Test
    fun `a network failure stops the run instead of timing out once per pending log`() = runTest {
        // Offline, every remaining log would fail the same way after a long timeout.
        // Arrange
        val stored = wireLogStore(
            workoutLogEntity(id = 1, syncPending = true),
            workoutLogEntity(id = 2, syncPending = true),
        )
        server.enqueueConnectionFailure()

        // Act
        repository(this).retryPendingSyncs()

        // Assert
        assertEquals(1, server.server.requestCount)
        assertTrue(stored.all { it.syncPending })
    }

    @Test
    fun `a rejection of one log does not stop the others`() = runTest {
        // Arrange — the first is refused (say, a validation error), the second is fine
        val first = workoutLogEntity(id = 1, syncPending = true, timestamp = FIXED_TIMESTAMP)
        val second = workoutLogEntity(id = 2, syncPending = true, timestamp = FIXED_TIMESTAMP + 1)
        val stored = wireLogStore(first, second)
        server.enqueueJson(BackendResponses.error("bad log"), code = HttpURLConnection.HTTP_BAD_REQUEST)
        server.enqueueEmpty(HttpURLConnection.HTTP_OK)

        // Act
        repository(this).retryPendingSyncs()

        // Assert
        assertEquals(2, server.server.requestCount)
        assertTrue(stored.first { it.clientId == first.clientId }.syncPending)
        assertFalse(stored.first { it.clientId == second.clientId }.syncPending)
    }

    // ------------------------------------------------------------------------------------
    // clearActivePlanAndProgress
    // ------------------------------------------------------------------------------------

    @Test
    fun `wiping clears the plan, the progress and the session in progress together`() = runTest {
        // One transaction: a cleared plan with surviving progress would point at a week that no
        // longer exists.
        // Arrange & Act
        repository(this).clearActivePlanAndProgress()

        // Assert
        coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Any>()) }
        coVerify(exactly = 1) { planDao.clearPlan() }
        coVerify(exactly = 1) { progressDao.clearAllProgress() }
        coVerify(exactly = 1) { activeWorkoutDao.clear() }
    }

    @Test
    fun `wiping keeps the logs, because regenerating a plan must not erase history`() = runTest {
        // Logs carry their own title and type now, so they no longer depend on the plan they were
        // logged against.
        // Arrange & Act
        repository(this).clearActivePlanAndProgress()

        // Assert
        coVerify(exactly = 0) { progressDao.clearAllLogs() }
        coVerify(exactly = 0) { progressDao.deleteWorkoutLog(any()) }
    }
}
