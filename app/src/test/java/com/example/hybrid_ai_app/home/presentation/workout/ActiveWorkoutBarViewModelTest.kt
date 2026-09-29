package com.example.hybrid_ai_app.home.presentation.workout

import app.cash.turbine.test
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.workoutSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The minimized bar only mirrors the stored session: present while one is in progress, else null. */
class ActiveWorkoutBarViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private fun viewModel(stored: MutableStateFlow<WorkoutSession?>): ActiveWorkoutBarViewModel {
        val repository = mockk<ActiveWorkoutRepository>()
        every { repository.observe() } returns stored
        return ActiveWorkoutBarViewModel(repository)
    }

    @Test
    fun `there is no bar until a session exists`() = runTest {
        // Arrange
        val vm = viewModel(MutableStateFlow(null))

        // Act & Assert
        vm.session.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session in progress is exposed for the bar`() = runTest {
        // Arrange
        val session = workoutSession(title = "Upper Body")
        val vm = viewModel(MutableStateFlow(session))

        // Act & Assert
        vm.session.test {
            assertNull("the initial value, before the repository is read", awaitItem())
            assertEquals(session, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the bar goes away when the session is cleared`() = runTest {
        // Arrange
        val stored = MutableStateFlow<WorkoutSession?>(workoutSession())
        val vm = viewModel(stored)

        // Act & Assert
        vm.session.test {
            awaitItem()
            awaitItem()
            stored.value = null
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change to the session, such as a rest starting, reaches the bar`() = runTest {
        // Arrange
        val stored = MutableStateFlow<WorkoutSession?>(workoutSession())
        val vm = viewModel(stored)

        // Act & Assert
        vm.session.test {
            awaitItem()
            awaitItem()
            val resting = workoutSession(restEndsAt = 1_789_725_720_000L, restTotalSec = 120)
            stored.value = resting
            assertEquals(resting, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
