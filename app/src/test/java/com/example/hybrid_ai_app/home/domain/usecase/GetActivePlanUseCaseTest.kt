package com.example.hybrid_ai_app.home.domain.usecase

import com.example.hybrid_ai_app.home.domain.model.ActivePlan
import com.example.hybrid_ai_app.home.domain.repository.PlanRepository
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.TestIds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Date

/**
 * The one use case in the project. Its whole job is refusing to call the network without a token.
 */
class GetActivePlanUseCaseTest {

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private val repository: PlanRepository = mockk()
    private val useCase = GetActivePlanUseCase(repository)

    private fun activePlan(id: String) = ActivePlan(
        id = id,
        startDate = Date(0),
        durationWeeks = 8,
        goal = "both",
        weeks = emptyList(),
        isActive = true,
    )

    @Test
    fun `a valid token is passed straight through to the repository`() {
        runTest {
            // Arrange
            val token = TestIds.uniqueToken()
            val planId = TestIds.uniqueObjectId()
            coEvery { repository.getActivePlan(token) } returns
                Result.success(activePlan(planId))

            // Act
            val result = useCase(token)

            // Assert
            assertEquals(planId, result.getOrNull()!!.id)
            coVerify(exactly = 1) { repository.getActivePlan(token) }
        }
    }

    @Test
    fun `a blank token fails without touching the network`() {
        runTest {
            // Arrange — no repository stub at all, so any call would fail the test
            val blank = "   "

            // Act
            val result = useCase(blank)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Authentication token is missing", result.exceptionOrNull()!!.message)
            coVerify(exactly = 0) { repository.getActivePlan(any()) }
        }
    }

    @Test
    fun `an empty token is refused the same way as a blank one`() {
        runTest {
            // Arrange
            val empty = ""

            // Act
            val result = useCase(empty)

            // Assert
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.getActivePlan(any()) }
        }
    }

    @Test
    fun `a repository failure is propagated unchanged rather than rewrapped`() {
        runTest {
            // Arrange
            val token = TestIds.uniqueToken()
            val cause = Exception("Network failure. Check internet connectivity.")
            coEvery { repository.getActivePlan(token) } returns Result.failure(cause)

            // Act
            val result = useCase(token)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(cause, result.exceptionOrNull())
        }
    }
}
