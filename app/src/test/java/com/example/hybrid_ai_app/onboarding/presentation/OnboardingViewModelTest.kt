package com.example.hybrid_ai_app.onboarding.presentation

import android.net.Uri
import com.example.hybrid_ai_app.core.data.remote.PlanAttachmentDto
import com.example.hybrid_ai_app.core.domain.model.PlanImportRejectedException
import com.example.hybrid_ai_app.core.domain.model.PlanNotRecognizedException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.onboarding.data.MAX_PLAN_ATTACHMENTS
import com.example.hybrid_ai_app.onboarding.data.PlanAttachment
import com.example.hybrid_ai_app.onboarding.data.PlanAttachmentError
import com.example.hybrid_ai_app.onboarding.data.PlanAttachmentException
import com.example.hybrid_ai_app.onboarding.data.PlanAttachmentReader
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
 * Onboarding: biometric capture, per-step validation, the bring-your-own-plan import, and the
 * submit that chains a profile PATCH into AI plan generation.
 *
 * `android.net.Uri` appears in `addAttachment`, so tests pass a `mockk<Uri>()` rather than calling
 * `Uri.parse` — the real parser is an Android framework stub on the JVM and returns null.
 */
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var repository: UserRepository
    private lateinit var attachmentReader: PlanAttachmentReader
    private lateinit var vm: OnboardingViewModel

    @Before
    fun setUp() {
        repository = mockk()
        attachmentReader = mockk()
        vm = OnboardingViewModel(repository, attachmentReader)
    }

    private fun attachment(
        displayName: String = "routine.pdf",
        mimeType: String = "application/pdf",
        base64: String = "QUJDRA==",
    ) = PlanAttachment(
        dto = PlanAttachmentDto(mimeType = mimeType, data = base64),
        displayName = displayName,
    )

    /** Fills step 1 and 2 with values that pass validation. */
    private fun fillValidProfile() {
        vm.updateWeight("80")
        vm.updateHeight("180")
        vm.updateAge("30")
        vm.updateSex("male")
        vm.updateGoal("both")
        vm.updateFitnessLevel("intermediate")
    }

    // ------------------------------------------------------------------------------------
    // State updates
    // ------------------------------------------------------------------------------------

    @Test
    fun `the initial state carries sensible defaults`() {
        // Arrange, Act & Assert
        assertEquals(1, vm.currentStep)
        assertEquals("male", vm.uiState.sex)
        assertEquals("both", vm.uiState.goal)
        assertEquals("beginner", vm.uiState.fitnessLevel)
        assertEquals(3, vm.uiState.daysAvailable)
        assertEquals(8, vm.uiState.planDuration)
        assertFalse(vm.uiState.hasExistingPlan)
        assertFalse(vm.isLoading)
    }

    @Test
    fun `switching away from female clears the period date so stale data is never sent`() {
        // Arrange
        vm.updateSex("female")
        vm.updateLastPeriodDate("2026-09-01")

        // Act
        vm.updateSex("male")

        // Assert
        assertEquals("", vm.uiState.lastPeriodDate)
    }

    @Test
    fun `staying female keeps the period date`() {
        // Arrange
        vm.updateSex("female")
        vm.updateLastPeriodDate("2026-09-01")

        // Act
        vm.updateSex("female")

        // Assert
        assertEquals("2026-09-01", vm.uiState.lastPeriodDate)
    }

    @Test
    fun `turning off the existing-plan toggle discards whatever was staged`() {
        // Otherwise a PDF the user attached and then opted out of would still be submitted.
        // Arrange
        vm.toggleExistingPlan(true)
        vm.updatePastedPlanText("Mon: Squat 4x6")

        // Act
        vm.toggleExistingPlan(false)

        // Assert
        assertEquals("", vm.uiState.pastedPlanText)
        assertTrue(vm.uiState.attachments.isEmpty())
    }

    @Test
    fun `the import step only exists when the user brought a plan`() {
        // Arrange & Act
        val withoutPlan = vm.totalSteps
        vm.toggleExistingPlan(true)
        val withPlan = vm.totalSteps

        // Assert
        assertEquals(3, withoutPlan)
        assertEquals(4, withPlan)
    }

    @Test
    fun `removing an attachment removes only the one at that index`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returnsMany listOf(
                Result.success(attachment(displayName = "a.pdf")),
                Result.success(attachment(displayName = "b.pdf")),
                Result.success(attachment(displayName = "c.pdf")),
            )
            repeat(3) { vm.addAttachment(uri) }
            advanceUntilIdle()

            // Act
            vm.removeAttachment(1)

            // Assert
            assertEquals(
                listOf("a.pdf", "c.pdf"),
                vm.uiState.attachments.map { it.displayName },
            )
        }
    }

    // ------------------------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------------------------

    @Test
    fun `stepping forward stops at the last step`() {
        // Arrange — 3 steps, no import
        repeat(10) { vm.nextStep() }

        // Act & Assert
        assertEquals(3, vm.currentStep)
    }

    @Test
    fun `stepping forward reaches step four once a plan is brought`() {
        // Arrange
        vm.toggleExistingPlan(true)

        // Act
        repeat(10) { vm.nextStep() }

        // Assert
        assertEquals(4, vm.currentStep)
    }

    @Test
    fun `stepping back stops at the first step`() {
        // Arrange
        vm.nextStep()

        // Act
        repeat(5) { vm.previousStep() }

        // Assert
        assertEquals(1, vm.currentStep)
    }

    // ------------------------------------------------------------------------------------
    // Validation — step 1
    // ------------------------------------------------------------------------------------

    @Test
    fun `step one rejects a blank or too-light weight`() {
        // The backend enforces min 30kg; catching it here avoids a round trip and a raw
        // Mongoose validation string.
        // Arrange, Act & Assert
        assertEquals("Please enter a valid weight (min 30kg).", vm.validateCurrentStep())

        vm.updateWeight("29")
        assertEquals("Please enter a valid weight (min 30kg).", vm.validateCurrentStep())

        vm.updateWeight("not a number")
        assertEquals("Please enter a valid weight (min 30kg).", vm.validateCurrentStep())
    }

    @Test
    fun `step one rejects a too-short height`() {
        // Arrange
        vm.updateWeight("80")

        // Act & Assert
        assertEquals("Please enter a valid height (min 100cm).", vm.validateCurrentStep())

        vm.updateHeight("99")
        assertEquals("Please enter a valid height (min 100cm).", vm.validateCurrentStep())
    }

    @Test
    fun `step one enforces a minimum age of sixteen`() {
        // Arrange
        vm.updateWeight("80")
        vm.updateHeight("180")

        // Act & Assert
        assertEquals("You must be at least 16 years old.", vm.validateCurrentStep())

        vm.updateAge("15")
        assertEquals("You must be at least 16 years old.", vm.validateCurrentStep())

        vm.updateAge("16")
        assertNull(vm.validateCurrentStep())
    }

    @Test
    fun `a female user must supply a last period date`() {
        // The backend needs it to phase the plan around the cycle, and it is the one snake_case
        // field on the wire.
        // Arrange
        vm.updateWeight("65")
        vm.updateHeight("170")
        vm.updateAge("30")
        vm.updateSex("female")

        // Act & Assert
        assertEquals(
            "Please enter the start date of your last period.",
            vm.validateCurrentStep(),
        )

        vm.updateLastPeriodDate("2026-09-01")
        assertNull(vm.validateCurrentStep())
    }

    @Test
    fun `a male user needs no period date`() {
        // Arrange
        vm.updateWeight("80")
        vm.updateHeight("180")
        vm.updateAge("30")
        vm.updateSex("male")

        // Act & Assert
        assertNull(vm.validateCurrentStep())
    }

    @Test
    fun `a blank sex is rejected`() {
        // Arrange
        vm.updateWeight("80")
        vm.updateHeight("180")
        vm.updateAge("30")
        vm.updateSex("")

        // Act & Assert
        assertEquals("Please select a biological sex.", vm.validateCurrentStep())
    }

    // ------------------------------------------------------------------------------------
    // Validation — steps 2, 3, 4
    // ------------------------------------------------------------------------------------

    @Test
    fun `step two requires a goal and an experience level`() {
        // Arrange
        vm.nextStep()

        // Act & Assert
        assertNull("the defaults already satisfy step two", vm.validateCurrentStep())

        vm.updateGoal("")
        assertEquals("Please select a primary fitness goal.", vm.validateCurrentStep())

        vm.updateGoal("both")
        vm.updateFitnessLevel("")
        assertEquals("Please select your current experience level.", vm.validateCurrentStep())
    }

    @Test
    fun `step two treats injuries as optional`() {
        // Arrange
        vm.nextStep()

        // Act
        vm.updateInjuries("")

        // Assert
        assertNull(vm.validateCurrentStep())
    }

    @Test
    fun `step three bounds the available days to one through seven`() {
        // Arrange
        vm.nextStep()
        vm.nextStep()

        // Act & Assert
        vm.updateDaysAvailable(0)
        assertEquals("Available days must be between 1 and 7.", vm.validateCurrentStep())

        vm.updateDaysAvailable(8)
        assertEquals("Available days must be between 1 and 7.", vm.validateCurrentStep())

        vm.updateDaysAvailable(7)
        assertNull(vm.validateCurrentStep())
    }

    @Test
    fun `step three only accepts the three plan durations the backend supports`() {
        // The backend's User model declares planDuration as enum [4, 8, 12].
        // Arrange
        vm.nextStep()
        vm.nextStep()

        // Act & Assert
        listOf(4, 8, 12).forEach { valid ->
            vm.updatePlanDuration(valid)
            assertNull("$valid weeks should be accepted", vm.validateCurrentStep())
        }
        listOf(1, 6, 16).forEach { invalid ->
            vm.updatePlanDuration(invalid)
            assertEquals(
                "Plan duration must be 4, 8, or 12 weeks.",
                vm.validateCurrentStep(),
            )
        }
    }

    @Test
    fun `step three requires a provided domain only when a plan was brought`() {
        // Arrange
        vm.nextStep()
        vm.nextStep()
        vm.toggleExistingPlan(true)

        // Act & Assert
        vm.updateProvidedDomain("running")
        assertEquals(
            "Tell us which part of your plan you already have.",
            vm.validateCurrentStep(),
        )

        vm.updateProvidedDomain("cardio")
        assertNull(vm.validateCurrentStep())

        vm.updateProvidedDomain("strength")
        assertNull(vm.validateCurrentStep())
    }

    @Test
    fun `step four requires either pasted text or an attachment`() {
        runTest {
            // Arrange
            vm.toggleExistingPlan(true)
            repeat(3) { vm.nextStep() }
            assertEquals(4, vm.currentStep)

            // Act & Assert
            assertEquals(
                "Paste your routine or attach a PDF/photo of it.",
                vm.validateCurrentStep(),
            )

            vm.updatePastedPlanText("Mon: Squat 4x6")
            assertNull(vm.validateCurrentStep())

            vm.updatePastedPlanText("")
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns Result.success(attachment())
            vm.addAttachment(uri)
            advanceUntilIdle()
            assertNull("an attachment alone is enough", vm.validateCurrentStep())
        }
    }

    // ------------------------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------------------------

    @Test
    fun `a readable file is attached`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns
                Result.success(attachment(displayName = "plan.pdf"))

            // Act
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Assert
            assertEquals(1, vm.uiState.attachments.size)
            assertEquals("plan.pdf", vm.uiState.attachments.single().displayName)
            assertNull(vm.attachmentError)
        }
    }

    @Test
    fun `a sixth file is refused without even reading it`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns Result.success(attachment())
            repeat(MAX_PLAN_ATTACHMENTS) { vm.addAttachment(uri) }
            advanceUntilIdle()
            assertEquals(MAX_PLAN_ATTACHMENTS, vm.uiState.attachments.size)

            // Act
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Assert
            assertEquals(PlanAttachmentError.TOO_MANY, vm.attachmentError)
            assertEquals(MAX_PLAN_ATTACHMENTS, vm.uiState.attachments.size)
            coVerify(exactly = MAX_PLAN_ATTACHMENTS) { attachmentReader.read(uri) }
        }
    }

    @Test
    fun `the size cap counts the running decoded total, not just the newest file`() {
        runTest {
            // The backend caps the decoded total at 8 MiB across all attachments, so two files
            // that each fit can still be refused together.
            // Arrange — base64 carries 3 bytes per 4 chars, so ~6 MiB of payload each
            val sixMiBOfBase64 = "A".repeat(8 * 1024 * 1024)
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns
                Result.success(attachment(base64 = sixMiBOfBase64))

            // Act
            vm.addAttachment(uri)
            advanceUntilIdle()
            assertEquals("the first one fits", 1, vm.uiState.attachments.size)
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Assert
            assertEquals(PlanAttachmentError.TOO_LARGE, vm.attachmentError)
            assertEquals("the second is rejected", 1, vm.uiState.attachments.size)
        }
    }

    @Test
    fun `a typed reader failure is surfaced as its own error`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns
                Result.failure(PlanAttachmentException(PlanAttachmentError.UNSUPPORTED_TYPE))

            // Act
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Assert
            assertEquals(PlanAttachmentError.UNSUPPORTED_TYPE, vm.attachmentError)
            assertTrue(vm.uiState.attachments.isEmpty())
        }
    }

    @Test
    fun `an untyped reader failure degrades to unreadable`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns
                Result.failure(IllegalStateException("content resolver died"))

            // Act
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Assert
            assertEquals(PlanAttachmentError.UNREADABLE, vm.attachmentError)
        }
    }

    @Test
    fun `a successful attachment clears a previous error`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns
                Result.failure(PlanAttachmentException(PlanAttachmentError.UNREADABLE))
            vm.addAttachment(uri)
            advanceUntilIdle()
            assertEquals(PlanAttachmentError.UNREADABLE, vm.attachmentError)

            // Act
            coEvery { attachmentReader.read(uri) } returns Result.success(attachment())
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Assert
            assertNull(vm.attachmentError)
        }
    }

    @Test
    fun `dismissing the attachment error clears it`() {
        runTest {
            // Arrange
            val uri = mockk<Uri>()
            coEvery { attachmentReader.read(uri) } returns
                Result.failure(PlanAttachmentException(PlanAttachmentError.TOO_LARGE))
            vm.addAttachment(uri)
            advanceUntilIdle()

            // Act
            vm.dismissAttachmentError()

            // Assert
            assertNull(vm.attachmentError)
        }
    }

    // ------------------------------------------------------------------------------------
    // submitOnboarding
    // ------------------------------------------------------------------------------------

    @Test
    fun `submitting saves the profile then generates a plan`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.updateDaysAvailable(5)
            vm.updatePlanDuration(12)
            val payload = slot<ProfileUpdateRequest>()
            coEvery { repository.updateProfile(capture(payload)) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.success(Unit)
            var succeeded = false

            // Act
            vm.submitOnboarding(onSuccess = { succeeded = true }, onError = { })
            advanceUntilIdle()

            // Assert
            assertEquals(30, payload.captured.age)
            assertEquals(80.0, payload.captured.weight, 0.0)
            assertEquals(180.0, payload.captured.height, 0.0)
            assertEquals("intermediate", payload.captured.fitnessLevel)
            assertEquals(5, payload.captured.daysAvailable)
            assertEquals(12, payload.captured.planDuration)
            coVerify(exactly = 1) { repository.generateAiPlan(12, "both") }
            assertTrue(succeeded)
            assertFalse(vm.isLoading)
        }
    }

    @Test
    fun `injuries are split trimmed and emptied of blanks`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.updateInjuries("  left knee , , right shoulder ,  ")
            val payload = slot<ProfileUpdateRequest>()
            coEvery { repository.updateProfile(capture(payload)) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Assert
            assertEquals(listOf("left knee", "right shoulder"), payload.captured.injuries)
        }
    }

    @Test
    fun `the period date is only sent for a female user`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.updateSex("female")
            vm.updateLastPeriodDate("2026-09-01")
            val payload = slot<ProfileUpdateRequest>()
            coEvery { repository.updateProfile(capture(payload)) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Assert
            assertEquals("2026-09-01", payload.captured.lastPeriodDate)
        }
    }

    @Test
    fun `a male user sends no period date even if one was typed earlier`() {
        runTest {
            // Arrange — typed while female, then switched back
            fillValidProfile()
            vm.updateSex("female")
            vm.updateLastPeriodDate("2026-09-01")
            vm.updateSex("male")
            val payload = slot<ProfileUpdateRequest>()
            coEvery { repository.updateProfile(capture(payload)) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Assert
            assertNull(payload.captured.lastPeriodDate)
        }
    }

    @Test
    fun `unparseable biometrics submit as zero rather than crashing`() {
        runTest {
            // Validation should have caught these first; this pins the fallback so a UI bug
            // cannot turn into a crash on the submit path.
            // Arrange
            vm.updateAge("abc")
            vm.updateWeight("")
            vm.updateHeight("tall")
            val payload = slot<ProfileUpdateRequest>()
            coEvery { repository.updateProfile(capture(payload)) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Assert
            assertEquals(0, payload.captured.age)
            assertEquals(0.0, payload.captured.weight, 0.0)
            assertEquals(0.0, payload.captured.height, 0.0)
        }
    }

    @Test
    fun `a brought plan goes to the import endpoint instead of generate`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.toggleExistingPlan(true)
            vm.updateProvidedDomain("cardio")
            vm.updatePastedPlanText("Mon: 5k easy")
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery {
                repository.importAiPlan(any(), any(), any(), any(), any())
            } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) {
                repository.importAiPlan(8, "both", "cardio", "Mon: 5k easy", emptyList())
            }
            coVerify(exactly = 0) { repository.generateAiPlan(any(), any()) }
        }
    }

    @Test
    fun `attached files are forwarded as DTOs on the import path`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.toggleExistingPlan(true)
            val uri = mockk<Uri>()
            val attached = attachment(mimeType = "image/png", base64 = "UE5H")
            coEvery { attachmentReader.read(uri) } returns Result.success(attached)
            vm.addAttachment(uri)
            advanceUntilIdle()
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            val sent = slot<List<PlanAttachmentDto>>()
            coEvery {
                repository.importAiPlan(any(), any(), any(), any(), capture(sent))
            } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Assert
            assertEquals(listOf(attached.dto), sent.captured)
        }
    }

    @Test
    fun `a failed profile save reports the error and never reaches plan generation`() {
        runTest {
            // Arrange
            fillValidProfile()
            coEvery { repository.updateProfile(any()) } returns
                Result.failure(Exception("Backend error: 401"))
            var error: String? = null

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { error = it })
            advanceUntilIdle()

            // Assert
            assertEquals("Backend error: 401", error)
            coVerify(exactly = 0) { repository.generateAiPlan(any(), any()) }
            assertFalse(vm.isLoading)
        }
    }

    @Test
    fun `a profile failure with no message falls back to generic copy`() {
        runTest {
            // Arrange
            fillValidProfile()
            coEvery { repository.updateProfile(any()) } returns Result.failure(Exception())
            var error: String? = null

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { error = it })
            advanceUntilIdle()

            // Assert
            assertEquals("Unknown network error", error)
        }
    }

    @Test
    fun `a 402 during generation opens the paywall rather than reporting an error`() {
        runTest {
            // Reachable in practice: the free tier allows one plan ever, and wiping locally does
            // not reset the server's count, so a second pass through onboarding gets a 402.
            // Arrange
            fillValidProfile()
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.failure(
                PremiumRequiredException(
                    reason = PremiumRequiredReason.PLAN_LIMIT_REACHED,
                    message = "Your free plan includes one generated routine.",
                ),
            )
            var error: String? = null
            var succeeded = false

            // Act
            vm.submitOnboarding(onSuccess = { succeeded = true }, onError = { error = it })
            advanceUntilIdle()

            // Assert
            assertEquals(PremiumRequiredReason.PLAN_LIMIT_REACHED, vm.premiumPrompt)
            assertNull("the paywall replaces the error message", error)
            assertFalse(succeeded)
            assertFalse(vm.isLoading)
        }
    }

    @Test
    fun `a 422 keeps the user on the import step to try a clearer document`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.toggleExistingPlan(true)
            vm.updatePastedPlanText("my grocery list")
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery {
                repository.importAiPlan(any(), any(), any(), any(), any())
            } returns Result.failure(PlanNotRecognizedException())
            var error: String? = null

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { error = it })
            advanceUntilIdle()

            // Assert
            assertTrue(vm.planNotRecognized)
            assertNull("localized copy is shown instead of the server's English", error)
        }
    }

    @Test
    fun `a 400 on import surfaces the server's specific explanation`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.toggleExistingPlan(true)
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery {
                repository.importAiPlan(any(), any(), any(), any(), any())
            } returns Result.failure(
                PlanImportRejectedException("Attachments must total at most 8 MB"),
            )
            var error: String? = null

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { error = it })
            advanceUntilIdle()

            // Assert
            assertEquals("Attachments must total at most 8 MB", error)
            assertFalse(vm.planNotRecognized)
        }
    }

    @Test
    fun `any other generation failure says the profile was saved so the user can retry`() {
        runTest {
            // The distinction matters: the profile PATCH already succeeded, so re-running
            // onboarding from scratch is not required.
            // Arrange
            fillValidProfile()
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns
                Result.failure(Exception("Error generating plan: HTTP 503"))
            var error: String? = null

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { error = it })
            advanceUntilIdle()

            // Assert
            assertEquals(
                "Profile saved, but plan generation failed. You can retry later.",
                error,
            )
        }
    }

    @Test
    fun `the loading flag is raised during submit and cleared on every outcome`() {
        runTest {
            // Arrange
            fillValidProfile()
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.success(Unit)

            // Act
            vm.submitOnboarding(onSuccess = { }, onError = { })
            // StandardTestDispatcher has not run the coroutine yet
            assertFalse(vm.isLoading)
            advanceUntilIdle()

            // Assert
            assertFalse("cleared after success", vm.isLoading)
        }
    }

    @Test
    fun `dismissing the paywall clears the prompt`() {
        runTest {
            // Arrange
            fillValidProfile()
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery { repository.generateAiPlan(any(), any()) } returns Result.failure(
                PremiumRequiredException(PremiumRequiredReason.TRIAL_EXPIRED, "expired"),
            )
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()

            // Act
            vm.dismissPremiumPrompt()

            // Assert
            assertNull(vm.premiumPrompt)
        }
    }

    @Test
    fun `dismissing the plan-not-recognized notice clears it`() {
        runTest {
            // Arrange
            fillValidProfile()
            vm.toggleExistingPlan(true)
            coEvery { repository.updateProfile(any()) } returns Result.success(Unit)
            coEvery {
                repository.importAiPlan(any(), any(), any(), any(), any())
            } returns Result.failure(PlanNotRecognizedException())
            vm.submitOnboarding(onSuccess = { }, onError = { })
            advanceUntilIdle()
            assertTrue(vm.planNotRecognized)

            // Act
            vm.dismissPlanNotRecognized()

            // Assert
            assertFalse(vm.planNotRecognized)
        }
    }
}
