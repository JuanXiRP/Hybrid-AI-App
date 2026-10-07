package com.example.hybrid_ai_app.core.data.repository

import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.local.dao.ActiveRunDao
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunEntity
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunPointEntity
import com.example.hybrid_ai_app.core.domain.model.RunMainBlock
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import com.example.hybrid_ai_app.core.domain.model.RunStructure
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.activeRun
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The run in progress round-trips through one row plus its points. A run survives process death
 * only if what is written reads back the same, structure included, so the round trip is the
 * contract.
 */
class ActiveRunRepositoryImplTest {

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var dao: ActiveRunDao
    private lateinit var row: MutableStateFlow<ActiveRunEntity?>

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        row = MutableStateFlow(null)
        // Back the mocked DAO with one row, so what is written is what is read.
        val written = slot<ActiveRunEntity>()
        coEvery { dao.start(capture(written)) } coAnswers { row.value = written.captured }
        coEvery { dao.get() } coAnswers { row.value }
        every { dao.observe() } returns row
    }

    private fun repository() = ActiveRunRepositoryImpl(dao)

    @Test
    fun `a started run reads back exactly as it was written, intervals included`() = runTest {
        // Arrange
        val run = activeRun(
            weekNumber = 3,
            dayIndex = 2,
            isExtra = true,
            title = "",
            structure = RunStructure(
                warmupSec = 600,
                main = RunMainBlock.Intervals(repeats = 8, workSec = 90, restSec = 45),
                cooldownSec = 300,
            ),
            accumulatedMs = 12_000,
            resumedAt = null,
        )
        val repository = repository()

        // Act
        repository.start(run)

        // Assert
        assertEquals(run, repository.get())
    }

    @Test
    fun `a continuous structure and a free run both survive the round trip`() = runTest {
        // Arrange
        val continuous = activeRun(structure = RunStructure(0, RunMainBlock.Continuous(1800), 0))
        val free = activeRun(structure = null)
        val repository = repository()

        // Act & Assert
        repository.start(continuous)
        assertEquals(continuous, repository.get())
        repository.start(free)
        assertEquals(free, repository.get())
    }

    @Test
    fun `a structure this build cannot read degrades to a free run instead of losing the run`() = runTest {
        // Arrange
        val run = activeRun()
        val repository = repository()
        repository.start(run)
        row.value = row.value?.copy(structureJson = "{\"from\":\"a future build\"}")

        // Act
        val restored = repository.get()

        // Assert
        assertNull(restored?.structure)
        assertEquals(run.clientId, restored?.clientId)
        assertEquals(run.startedAt, restored?.startedAt)
    }

    @Test
    fun `observing maps the row as it changes`() = runTest {
        // Arrange
        val run = activeRun()
        val repository = repository()

        // Act & Assert
        repository.observe().test {
            assertNull(awaitItem())
            repository.start(run)
            assertEquals(run, awaitItem())
            row.value = null
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `points are stored one by one and read back in order`() = runTest {
        // Arrange
        coEvery { dao.points() } returns listOf(
            ActiveRunPointEntity(id = 1, lat = 40.4168, lng = -3.7038),
            ActiveRunPointEntity(id = 2, lat = 40.4170, lng = -3.7040),
        )
        val repository = repository()

        // Act
        repository.addPoint(RunPoint(40.4171, -3.7041))
        val points = repository.points()

        // Assert
        coVerify(exactly = 1) { dao.insertPoint(match { it.lat == 40.4171 && it.lng == -3.7041 }) }
        assertEquals(listOf(RunPoint(40.4168, -3.7038), RunPoint(40.4170, -3.7040)), points)
    }

    @Test
    fun `pausing banks the time, never a negative one, and resuming restarts the clock`() = runTest {
        // Arrange
        val repository = repository()

        // Act
        repository.pause(-5)
        repository.pause(42_000)
        repository.resume(1_000)

        // Assert
        coVerify(exactly = 1) { dao.pause(0) }
        coVerify(exactly = 1) { dao.pause(42_000) }
        coVerify(exactly = 1) { dao.resume(1_000) }
    }

    @Test
    fun `clearing drops the run and its points`() = runTest {
        // Act
        repository().clear()

        // Assert
        coVerify(exactly = 1) { dao.clear() }
    }
}
