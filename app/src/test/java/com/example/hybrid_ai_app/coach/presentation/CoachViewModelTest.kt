package com.example.hybrid_ai_app.coach.presentation

import app.cash.turbine.test
import com.example.hybrid_ai_app.coach.data.ChatMessageDto
import com.example.hybrid_ai_app.coach.data.CoachRepository
import com.example.hybrid_ai_app.coach.data.presentation.MessageSender
import com.example.hybrid_ai_app.core.data.EntitlementManager
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.expiredEntitlement
import com.example.hybrid_ai_app.testing.trialEntitlement
import com.example.hybrid_ai_app.testing.workoutPlanEntity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The conversational coach screen.
 *
 * Three things make this the most awkward ViewModel to test, and the setup below handles each:
 * `init` launches a collector on the active-plan flow (so the mock must return a real flow, not a
 * relaxed stub), it refreshes the entitlement, and it seeds a canned greeting — so `messages`
 * already holds one item before any test acts. `PlanContextFormatter` is a Kotlin `object` rather
 * than an injected collaborator, so the plan context is exercised by feeding a real
 * [WorkoutPlanEntity] through the repository instead of stubbing the formatter.
 */
class CoachViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var coachRepository: CoachRepository
    private lateinit var workoutPlanRepository: WorkoutPlanRepository
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var entitlementFlow: MutableStateFlow<Entitlement>
    private lateinit var activePlanFlow: MutableStateFlow<WorkoutPlanEntity?>

    @Before
    fun setUp() {
        coachRepository = mockk()
        workoutPlanRepository = mockk()
        entitlementManager = mockk(relaxed = true)
        preferencesManager = mockk()
        entitlementFlow = MutableStateFlow(trialEntitlement())
        // A StateFlow, not a finite flow: init collects this forever, and a completing flow would
        // let the collector finish and stop reflecting later plan changes.
        activePlanFlow = MutableStateFlow(null)

        every { entitlementManager.entitlement } returns entitlementFlow
        every { workoutPlanRepository.getActivePlan() } returns activePlanFlow
        every { preferencesManager.userProfilePicFlow } returns flowOf(null)
        coEvery { entitlementManager.refresh() } returns Result.success(trialEntitlement())
    }

    private fun viewModel() = CoachViewModel(
        coachRepository,
        workoutPlanRepository,
        entitlementManager,
        preferencesManager,
    )

    // ------------------------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------------------------

    @Test
    fun `the screen opens with a single greeting from the coach`() = runTest {
        // Arrange & Act
        val vm = viewModel()

        // Assert
        assertEquals(1, vm.messages.size)
        assertEquals("welcome", vm.messages.single().id)
        assertEquals(MessageSender.COACH, vm.messages.single().sender)
    }

    @Test
    fun `the entitlement is refreshed on entry rather than trusted from cache`() = runTest {
        // The daily chat quota resets at UTC midnight and can be spent from another device.
        // Arrange & Act
        viewModel()
        advanceUntilIdle()

        // Assert
        coVerify(atLeast = 1) { entitlementManager.refresh() }
    }

    @Test
    fun `the active plan is observed so later changes are picked up`() = runTest {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns
            Result.success("Sure.")
        val context = slot<String?>()
        coEvery { coachRepository.sendMessage(any(), captureNullable(context), any()) } returns
            Result.success("Sure.")

        // Act — a plan arrives after construction
        activePlanFlow.value = workoutPlanEntity(goal = "endurance", durationWeeks = 12)
        advanceUntilIdle()
        vm.sendUserMessage("What is today?")
        advanceUntilIdle()

        // Assert
        assertTrue(
            "expected the newly arrived plan in the context, got ${context.captured}",
            context.captured!!.contains("Goal: endurance, Duration: 12 weeks"),
        )
    }

    // ------------------------------------------------------------------------------------
    // Sending a message
    // ------------------------------------------------------------------------------------

    @Test
    fun `a blank message is ignored entirely`() = runTest {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("   ")
        advanceUntilIdle()

        // Assert
        assertEquals("only the greeting remains", 1, vm.messages.size)
        coVerify(exactly = 0) { coachRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `the user's message appears immediately and the reply is appended`() = runTest {
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns
            Result.success("Aim for RPE 8.")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("How heavy should I squat?")
        advanceUntilIdle()

        // Assert
        assertEquals(3, vm.messages.size)
        assertEquals("How heavy should I squat?", vm.messages[1].text)
        assertEquals(MessageSender.USER, vm.messages[1].sender)
        assertEquals("Aim for RPE 8.", vm.messages[2].text)
        assertEquals(MessageSender.COACH, vm.messages[2].sender)
    }

    @Test
    fun `the optimistic bubble is shown before the network answers`() = runTest {
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns
            Result.success("Later.")
        val vm = viewModel()
        advanceUntilIdle()

        // Act — StandardTestDispatcher has not run the launched coroutine yet
        vm.sendUserMessage("Hello")

        // Assert
        assertEquals("the user's turn is already visible", 2, vm.messages.size)
        assertEquals(MessageSender.USER, vm.messages[1].sender)
    }

    @Test
    fun `the plan context is attached to every message`() = runTest {
        // Arrange
        activePlanFlow.value = workoutPlanEntity(goal = "both", durationWeeks = 8)
        val context = slot<String?>()
        coEvery { coachRepository.sendMessage(any(), captureNullable(context), any()) } returns
            Result.success("Sure.")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("What is today?")
        advanceUntilIdle()

        // Assert
        assertTrue(context.captured!!.startsWith("User's active training plan"))
    }

    @Test
    fun `no plan means no context rather than an empty string`() = runTest {
        // The repository omits the field entirely when it is null, which is what lets the backend
        // fall back to the plan it has stored.
        // Arrange
        activePlanFlow.value = null
        val context = slot<String?>()
        coEvery { coachRepository.sendMessage(any(), captureNullable(context), any()) } returns
            Result.success("Sure.")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert
        assertNull(context.captured)
    }

    @Test
    fun `the canned greeting is excluded from the history sent to the model`() = runTest {
        // It is UI copy, not something the user or the model said. Including it would teach the
        // model that it opens every conversation in Spanish.
        // Arrange
        val history = slot<List<ChatMessageDto>>()
        coEvery { coachRepository.sendMessage(any(), any(), capture(history)) } returns
            Result.success("Sure.")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("First question")
        advanceUntilIdle()

        // Assert
        assertTrue("the first turn carries no history", history.captured.isEmpty())
    }

    @Test
    fun `prior turns are sent as history with Gemini's role names`() = runTest {
        // Arrange
        val history = slot<List<ChatMessageDto>>()
        coEvery { coachRepository.sendMessage(any(), any(), capture(history)) } returns
            Result.success("Yes, with a lighter second session.")
        val vm = viewModel()
        advanceUntilIdle()
        vm.sendUserMessage("Is squatting twice a week fine?")
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("And deadlifts?")
        advanceUntilIdle()

        // Assert — compared as a whole value rather than probed index by index, so a reordering
        // or a dropped turn fails here instead of slipping through a positional check.
        assertEquals(
            listOf(
                ChatMessageDto(role = "user", content = "Is squatting twice a week fine?"),
                ChatMessageDto(role = "model", content = "Yes, with a lighter second session."),
            ),
            history.captured,
        )
    }

    @Test
    fun `history is snapshotted before the new turn is added`() = runTest {
        // Otherwise the message being sent would also appear in its own history.
        // Arrange
        val history = slot<List<ChatMessageDto>>()
        coEvery { coachRepository.sendMessage(any(), any(), capture(history)) } returns
            Result.success("Reply")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Only question")
        advanceUntilIdle()

        // Assert
        assertFalse(history.captured.any { it.content == "Only question" })
    }

    @Test
    fun `a successful reply refreshes the entitlement because the quota was spent`() = runTest {
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns Result.success("Hi")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert — once in init, once after the reply
        coVerify(atLeast = 2) { entitlementManager.refresh() }
    }

    @Test
    fun `the loading flag rises while waiting and falls when the reply lands`() = runTest {
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns Result.success("Hi")
        val vm = viewModel()
        advanceUntilIdle()

        // Act & Assert
        vm.isLoading.test {
            assertFalse(awaitItem())
            vm.sendUserMessage("Hello")
            assertTrue("typing indicator on", awaitItem())
            advanceUntilIdle()
            assertFalse("typing indicator off", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------------------------
    // Quota and paywall
    // ------------------------------------------------------------------------------------

    @Test
    fun `a spent quota blocks the send and opens the paywall`() = runTest {
        // A cheap local guard: the server enforces the quota regardless, this only saves latency.
        // Arrange
        entitlementFlow.value = trialEntitlement(chatUsed = 2, chatLimit = 2)
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("One more?")
        advanceUntilIdle()

        // Assert
        assertEquals(PremiumRequiredReason.CHAT_QUOTA_EXCEEDED, vm.premiumPrompt.value)
        assertEquals("nothing was added to the transcript", 1, vm.messages.size)
        coVerify(exactly = 0) { coachRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `an expired trial shows the trial copy rather than the quota copy`() = runTest {
        // Arrange
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert
        assertEquals(PremiumRequiredReason.TRIAL_EXPIRED, vm.premiumPrompt.value)
    }

    @Test
    fun `showPremiumPrompt picks the copy that matches why the user is blocked`() = runTest {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()

        // Act & Assert
        entitlementFlow.value = trialEntitlement(chatUsed = 2, chatLimit = 2)
        vm.showPremiumPrompt()
        assertEquals(PremiumRequiredReason.CHAT_QUOTA_EXCEEDED, vm.premiumPrompt.value)

        entitlementFlow.value = expiredEntitlement()
        vm.showPremiumPrompt()
        assertEquals(PremiumRequiredReason.TRIAL_EXPIRED, vm.premiumPrompt.value)
    }

    @Test
    fun `a premium user is never blocked locally`() = runTest {
        // Arrange
        entitlementFlow.value = com.example.hybrid_ai_app.testing.premiumEntitlement()
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns Result.success("Hi")
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert
        assertNull(vm.premiumPrompt.value)
        coVerify(exactly = 1) { coachRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `a server 402 takes the optimistic bubble back out`() = runTest {
        // The message was never delivered, so leaving it in the transcript would show the user a
        // turn the coach never received — and it would then be replayed as history.
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns Result.failure(
            PremiumRequiredException(
                reason = PremiumRequiredReason.CHAT_QUOTA_EXCEEDED,
                message = "You have used today's coach messages.",
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("One more?")
        advanceUntilIdle()

        // Assert
        assertEquals("only the greeting is left", 1, vm.messages.size)
        assertEquals("welcome", vm.messages.single().id)
        assertEquals(PremiumRequiredReason.CHAT_QUOTA_EXCEEDED, vm.premiumPrompt.value)
    }

    @Test
    fun `a server 402 refreshes the entitlement because the cached counter was stale`() = runTest {
        // The local guard let this through, which means our cached count disagreed with the
        // server — the server is authoritative, so re-read it.
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns Result.failure(
            PremiumRequiredException(PremiumRequiredReason.CHAT_QUOTA_EXCEEDED, "spent"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("One more?")
        advanceUntilIdle()

        // Assert
        coVerify(atLeast = 2) { entitlementManager.refresh() }
    }

    @Test
    fun `dismissing the paywall clears the prompt`() = runTest {
        // Arrange
        entitlementFlow.value = expiredEntitlement()
        val vm = viewModel()
        advanceUntilIdle()
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Act
        vm.dismissPremiumPrompt()

        // Assert
        assertNull(vm.premiumPrompt.value)
    }

    @Test
    fun `the prompt starts empty`() = runTest {
        // Arrange & Act
        val vm = viewModel()

        // Assert
        assertNull(vm.premiumPrompt.value)
    }

    // ------------------------------------------------------------------------------------
    // Network failure
    // ------------------------------------------------------------------------------------

    @Test
    fun `a network failure keeps the user's turn and adds an apology`() = runTest {
        // Unlike the 402 case the message may well have been sent, and removing the user's own
        // words would be worse than leaving them with a retry prompt.
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns
            Result.failure(java.io.IOException("offline"))
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert
        assertEquals(3, vm.messages.size)
        assertEquals("Hello", vm.messages[1].text)
        assertEquals(MessageSender.COACH, vm.messages[2].sender)
        assertTrue(
            "expected a connection apology, got: ${vm.messages[2].text}",
            vm.messages[2].text.contains("problema de conexión"),
        )
        assertNull("a connection blip is not a paywall", vm.premiumPrompt.value)
    }

    @Test
    fun `the loading flag is cleared after a failure so the input is usable again`() = runTest {
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns
            Result.failure(java.io.IOException("offline"))
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `a failed send does not consume quota by refreshing twice`() = runTest {
        // Arrange
        coEvery { coachRepository.sendMessage(any(), any(), any()) } returns
            Result.failure(java.io.IOException("offline"))
        val vm = viewModel()
        advanceUntilIdle()

        // Act
        vm.sendUserMessage("Hello")
        advanceUntilIdle()

        // Assert — only the init refresh; a transport error is not a quota event
        coVerify(exactly = 1) { entitlementManager.refresh() }
    }

    // ------------------------------------------------------------------------------------
    // Pass-throughs
    // ------------------------------------------------------------------------------------

    @Test
    fun `the entitlement flow is exposed for the remaining-messages chip`() = runTest {
        // Arrange
        val vm = viewModel()

        // Act
        entitlementFlow.value = trialEntitlement(chatUsed = 1, chatLimit = 2)

        // Assert
        assertEquals(1, vm.entitlement.value.chatMessagesLeft)
    }

    @Test
    fun `the profile picture path is exposed straight from preferences`() = runTest {
        // Arrange
        every { preferencesManager.userProfilePicFlow } returns flowOf("/data/me.png")
        val vm = viewModel()

        // Act & Assert
        vm.localProfilePicPath.test {
            assertEquals("/data/me.png", awaitItem())
            awaitComplete()
        }
    }
}
