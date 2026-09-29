package com.example.hybrid_ai_app.home.data.repository

import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.local.dao.ActiveWorkoutDao
import com.example.hybrid_ai_app.core.data.local.entity.ActiveWorkoutEntity
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.sessionExercise
import com.example.hybrid_ai_app.testing.sessionSet
import com.example.hybrid_ai_app.testing.workoutSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The session in progress round-trips through one JSON column. It survives process death only if
 * what is written can be read back, so the round trip is the contract.
 */
class ActiveWorkoutRepositoryImplTest {

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var dao: ActiveWorkoutDao

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
    }

    private fun repository() = ActiveWorkoutRepositoryImpl(dao)

    @Test
    fun `a saved session reads back exactly as it was written`() = runTest {
        // Arrange
        val session = workoutSession(
            weekNumber = 3,
            dayIndex = 2,
            notes = "Slept badly",
            restEndsAt = 1_789_725_720_000L,
            restTotalSec = 120,
            exercises = listOf(
                sessionExercise(
                    exerciseId = "Barbell_Squat",
                    name = "Back Squat",
                    notes = "Belt",
                    restSeconds = 180,
                    sets = listOf(
                        sessionSet(type = SetType.WARMUP, weight = "40", reps = "10", completed = true),
                        sessionSet(type = SetType.FAILURE, targetReps = "8-10", actualRpe = "10"),
                    ),
                ),
            ),
        )
        val written = slot<ActiveWorkoutEntity>()
        coEvery { dao.upsert(capture(written)) } returns Unit
        val repository = repository()

        // Act
        repository.save(session)
        every { dao.observe() } returns MutableStateFlow(written.captured)

        // Assert
        repository.observe().test {
            assertEquals(session, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaults are written out, so a session survives a default changing later`() = runTest {
        // Arrange
        val written = slot<ActiveWorkoutEntity>()
        coEvery { dao.upsert(capture(written)) } returns Unit

        // Act
        repository().save(workoutSession(exercises = listOf(sessionExercise(restSeconds = 120))))

        // Assert
        assertTrue(
            "restSeconds must be encoded even at its default: ${written.captured.sessionJson}",
            written.captured.sessionJson.contains("\"restSeconds\":120"),
        )
    }

    @Test
    fun `the week and day are lifted out of the JSON for cheap reads`() = runTest {
        // Arrange
        val written = slot<ActiveWorkoutEntity>()
        coEvery { dao.upsert(capture(written)) } returns Unit

        // Act
        repository().save(workoutSession(weekNumber = 4, dayIndex = 5))

        // Assert
        assertEquals(4, written.captured.weekNumber)
        assertEquals(5, written.captured.dayIndex)
        assertEquals("there is only ever one row", ActiveWorkoutEntity.ACTIVE_WORKOUT_ID, written.captured.id)
    }

    @Test
    fun `no stored session observes as null`() = runTest {
        // Arrange
        every { dao.observe() } returns MutableStateFlow(null)

        // Act & Assert
        repository().observe().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row that cannot be decoded observes as no session rather than crashing`() = runTest {
        // Arrange — a schema this build cannot read
        every { dao.observe() } returns MutableStateFlow(
            ActiveWorkoutEntity(weekNumber = 1, dayIndex = 0, sessionJson = "{ not json"),
        )

        // Act & Assert
        repository().observe().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session written by a build with more fields still reads`() = runTest {
        // Arrange — a downgrade must not throw the session away
        val json = """{"clientId":"c","weekNumber":1,"dayIndex":0,"title":"Legs","startedAt":1,""" +
            """"exercises":[],"futureField":true}"""
        every { dao.observe() } returns MutableStateFlow(
            ActiveWorkoutEntity(weekNumber = 1, dayIndex = 0, sessionJson = json),
        )

        // Act & Assert
        repository().observe().test {
            assertEquals("Legs", awaitItem()!!.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing removes the row`() = runTest {
        // Act
        repository().clear()

        // Assert
        coVerify(exactly = 1) { dao.clear() }
    }
}
