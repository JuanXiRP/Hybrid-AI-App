package com.example.hybrid_ai_app.settings.presentation

import app.cash.turbine.test
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.premiumEntitlement
import com.example.hybrid_ai_app.testing.trialEntitlement
import com.example.hybrid_ai_app.testing.userDto
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The settings screen: profile display and editing, preferences, sign-out and plan reset.
 *
 * `init { fetchUserProfile() }` launches a coroutine at construction, so every test stubs
 * `getUserProfile()` *before* building the ViewModel — a relaxed mock returning a mock `Result`
 * would leave `profileState` in a shape no assertion could read.
 */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var userRepository: UserRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var workoutPlanRepository: WorkoutPlanRepository
    private lateinit var entitlementFlow: MutableStateFlow<Entitlement>

    @Before
    fun setUp() {
        preferencesManager = mockk(relaxed = true)
        userRepository = mockk()
        entitlementManager = mockk(relaxed = true)
        workoutPlanRepository = mockk(relaxed = true)
        entitlementFlow = MutableStateFlow(trialEntitlement())

        every { preferencesManager.languageFlow } returns flowOf("en")
        every { preferencesManager.darkModeFlow } returns flowOf(false)
        every { preferencesManager.userNameFlow } returns flowOf(null)
        every { preferencesManager.userProfilePicFlow } returns flowOf(null)
        every { entitlementManager.entitlement } returns entitlementFlow
    }

    private fun viewModel() = SettingsViewModel(
        preferencesManager,
        userRepository,
        entitlementManager,
        workoutPlanRepository,
    )

    // ------------------------------------------------------------------------------------
    // Profile loading
    // ------------------------------------------------------------------------------------

    @Test
    fun `the profile is fetched at construction and published on success`() = runTest {
        // Arrange
        val user = userDto(name = "Ada")
        coEvery { userRepository.getUserProfile() } returns Result.success(user)

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(ProfileState.Success(user), vm.profileState.value)
        coVerify(exactly = 1) { userRepository.getUserProfile() }
    }

    @Test
    fun `the state is Loading until the fetch resolves`() = runTest {
        // StandardTestDispatcher queues the init coroutine rather than running it eagerly, which
        // is what lets this assertion see the intermediate state at all.
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())

        // Act
        val vm = viewModel()

        // Assert
        assertEquals(ProfileState.Loading, vm.profileState.value)
    }

    @Test
    fun `a failed fetch surfaces the repository's message`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns
            Result.failure(Exception("Error fetching profile: HTTP 401"))

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(
            ProfileState.Error("Error fetching profile: HTTP 401"),
            vm.profileState.value,
        )
    }

    @Test
    fun `a failure with no message falls back to generic copy`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.failure(Exception())

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertEquals(ProfileState.Error("Failed to load profile"), vm.profileState.value)
    }

    @Test
    fun `a thrown exception is caught rather than crashing the screen`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } throws IllegalStateException("boom")

        // Act
        val vm = viewModel()
        advanceUntilIdle()

        // Assert
        assertTrue(vm.profileState.value is ProfileState.Error)
    }

    // ------------------------------------------------------------------------------------
    // updateProfileMetrics
    // ------------------------------------------------------------------------------------

    @Test
    fun `editing metrics sends the new values and keeps the untouched ones`() = runTest {
        // The screen only edits three fields, but the backend PATCH requires the whole profile,
        // so the rest has to come from the cached user. Dropping them would blank the profile.
        // Arrange
        val user = userDto(
            age = 31,
            weight = 80.0,
            height = 182.0,
            sex = "male",
            goal = "both",
            fitnessLevel = "advanced",
            daysAvailable = 4,
            planDuration = 12,
            injuries = listOf("left knee"),
        )
        coEvery { userRepository.getUserProfile() } returns Result.success(user)
        val sent = slot<ProfileUpdateRequest>()
        coEvery { userRepository.updateProfile(capture(sent)) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = 78.5, goal = "strength", daysAvailable = 5)
        advanceUntilIdle()

        // Assert
        assertEquals(78.5, sent.captured.weight, 0.0)
        assertEquals("strength", sent.captured.goal)
        assertEquals(5, sent.captured.daysAvailable)
        assertEquals("untouched fields come from the cached profile", 31, sent.captured.age)
        assertEquals(182.0, sent.captured.height, 0.0)
        assertEquals("advanced", sent.captured.fitnessLevel)
        assertEquals(12, sent.captured.planDuration)
        assertEquals(listOf("left knee"), sent.captured.injuries)
    }

    @Test
    fun `a blank goal keeps the user's existing goal rather than clearing it`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns
            Result.success(userDto(goal = "endurance"))
        val sent = slot<ProfileUpdateRequest>()
        coEvery { userRepository.updateProfile(capture(sent)) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = null, goal = "   ", daysAvailable = null)
        advanceUntilIdle()

        // Assert
        assertEquals("endurance", sent.captured.goal)
    }

    @Test
    fun `null edits fall back to the cached values`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns
            Result.success(userDto(weight = 75.0, daysAvailable = 3))
        val sent = slot<ProfileUpdateRequest>()
        coEvery { userRepository.updateProfile(capture(sent)) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = null, goal = "both", daysAvailable = null)
        advanceUntilIdle()

        // Assert
        assertEquals(75.0, sent.captured.weight, 0.0)
        assertEquals(3, sent.captured.daysAvailable)
    }

    @Test
    fun `a profile with empty optional fields still produces a valid request`() = runTest {
        // Arrange — a user who never finished onboarding
        coEvery { userRepository.getUserProfile() } returns Result.success(
            userDto(
                age = null,
                weight = null,
                height = null,
                sex = null,
                goal = null,
                fitnessLevel = null,
                daysAvailable = null,
                planDuration = null,
            ),
        )
        val sent = slot<ProfileUpdateRequest>()
        coEvery { userRepository.updateProfile(capture(sent)) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = null, goal = "", daysAvailable = null)
        advanceUntilIdle()

        // Assert
        assertEquals(0, sent.captured.age)
        assertEquals(0.0, sent.captured.weight, 0.0)
        assertEquals("", sent.captured.sex)
        assertEquals("planDuration defaults to 4, not 0", 4, sent.captured.planDuration)
    }

    @Test
    fun `a successful update re-fetches the profile so the screen shows server truth`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        coEvery { userRepository.updateProfile(any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = 80.0, goal = "both", daysAvailable = 4)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 2) { userRepository.getUserProfile() }
    }

    @Test
    fun `a failed update does not re-fetch`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        coEvery { userRepository.updateProfile(any()) } returns
            Result.failure(Exception("Backend error: 400"))
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = 80.0, goal = "both", daysAvailable = 4)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { userRepository.getUserProfile() }
    }

    @Test
    fun `nothing is sent while the profile has not loaded`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.failure(Exception("offline"))
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = 80.0, goal = "both", daysAvailable = 4)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { userRepository.updateProfile(any()) }
    }

    @Test
    fun `the updating flag is cleared even when the update throws`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        coEvery { userRepository.updateProfile(any()) } throws IllegalStateException("boom")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.updateProfileMetrics(weight = 80.0, goal = "both", daysAvailable = 4)
        advanceUntilIdle()

        // Assert
        assertFalse("a stuck spinner would block the screen", vm.isUpdating.value)
    }

    @Test
    fun `the updating flag toggles around the request`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        coEvery { userRepository.updateProfile(any()) } returns Result.success(Unit)
        val vm = viewModel()
        advanceUntilIdle()

        // Act & Assert
        vm.isUpdating.test {
            assertFalse(awaitItem())
            vm.updateProfileMetrics(weight = 80.0, goal = "both", daysAvailable = 4)
            assertTrue("spinner on", awaitItem())
            advanceUntilIdle()
            assertFalse("spinner off", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------------------------

    @Test
    fun `saving the language delegates to preferences`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        val vm = viewModel()

        // Act
        vm.saveLanguage("es")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { preferencesManager.saveLanguage("es") }
    }

    @Test
    fun `toggling dark mode delegates to preferences`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        val vm = viewModel()

        // Act
        vm.toggleDarkMode(true)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { preferencesManager.toggleDarkMode(true) }
    }

    @Test
    fun `updating the local name delegates to preferences`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        val vm = viewModel()

        // Act
        vm.updateLocalName("Ada L.")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { preferencesManager.saveLocalUserName("Ada L.") }
    }

    @Test
    fun `updating the profile picture delegates to preferences`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        val vm = viewModel()

        // Act
        vm.updateProfilePicture("/data/avatar.png")
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { preferencesManager.saveLocalProfilePic("/data/avatar.png") }
    }

    @Test
    fun `the preference flows are exposed for the screen to collect`() = runTest {
        // Arrange
        every { preferencesManager.languageFlow } returns flowOf("es")
        every { preferencesManager.darkModeFlow } returns flowOf(true)
        every { preferencesManager.userNameFlow } returns flowOf("Ada")
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())

        // Act
        val vm = viewModel()

        // Assert
        vm.currentLanguage.test {
            assertEquals("es", awaitItem())
            awaitComplete()
        }
        vm.isDarkMode.test {
            assertTrue(awaitItem())
            awaitComplete()
        }
        vm.localUserName.test {
            assertEquals("Ada", awaitItem())
            awaitComplete()
        }
    }

    // ------------------------------------------------------------------------------------
    // Sign-out
    // ------------------------------------------------------------------------------------

    @Test
    fun `logout clears the token and the entitlement before navigating away`() = runTest {
        // Entitlement is per-account: leaving it behind would show the next user the previous
        // user's premium status.
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        val vm = viewModel()
        var navigated = false

        // Act
        vm.logout { navigated = true }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { preferencesManager.clearToken() }
        coVerify(exactly = 1) { entitlementManager.clear() }
        assertTrue("the caller is told to navigate to the auth graph", navigated)
    }

    // ------------------------------------------------------------------------------------
    // Wipe and regenerate
    // ------------------------------------------------------------------------------------

    @Test
    fun `wiping clears the plan and progress then reports completion`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        coEvery { workoutPlanRepository.clearActivePlanAndProgress() } returns Unit
        val vm = viewModel()
        advanceUntilIdle()
        var cleared = false

        // Act
        vm.wipeDataAndRegenerate { cleared = true }
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { workoutPlanRepository.clearActivePlanAndProgress() }
        assertTrue(cleared)
        assertFalse(vm.isUpdating.value)
    }

    @Test
    fun `a failed wipe reports an error and does not claim success`() = runTest {
        // Arrange
        coEvery { userRepository.getUserProfile() } returns Result.success(userDto())
        coEvery { workoutPlanRepository.clearActivePlanAndProgress() } throws
            IllegalStateException("database is locked")
        val vm = viewModel()
        advanceUntilIdle()
        var cleared = false

        // Act
        vm.wipeDataAndRegenerate { cleared = true }
        advanceUntilIdle()

        // Assert
        assertFalse("the caller must not navigate on failure", cleared)
        assertEquals(
            ProfileState.Error("Failed to clear plan: database is locked"),
            vm.profileState.value,
        )
        assertFalse(vm.isUpdating.value)
    }

    // ------------------------------------------------------------------------------------
    // Entitlement
    // ------------------------------------------------------------------------------------

    @Test
    fun `the upgrade card reads live entitlement, not the once-fetched profile flag`() = runTest {
        // The regression this guards: the card used to read UserDto.isPremium, fetched once in
        // init, so after buying, popping back from the paywall still showed "Go Premium".
        // Arrange
        coEvery { userRepository.getUserProfile() } returns
            Result.success(userDto(isPremium = false))
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.entitlement.value.isPremium)

        // Act — a purchase completes elsewhere and the manager republishes
        entitlementFlow.value = premiumEntitlement()

        // Assert
        assertTrue("settings must see the purchase immediately", vm.entitlement.value.isPremium)
    }
}
