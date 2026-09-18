package com.example.hybrid_ai_app.onboarding.presentation

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.domain.model.PlanImportRejectedException
import com.example.hybrid_ai_app.core.domain.model.PlanNotRecognizedException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.onboarding.data.MAX_PLAN_ATTACHMENTS
import com.example.hybrid_ai_app.onboarding.data.MAX_PLAN_ATTACHMENT_BYTES
import com.example.hybrid_ai_app.onboarding.data.PlanAttachment
import com.example.hybrid_ai_app.onboarding.data.PlanAttachmentError
import com.example.hybrid_ai_app.onboarding.data.PlanAttachmentException
import com.example.hybrid_ai_app.onboarding.data.PlanAttachmentReader
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val age: String = "",
    val weight: String = "",
    val height: String = "",
    val sex: String = "male",
    val goal: String = "both",
    val fitnessLevel: String = "beginner",
    val daysAvailable: Int = 3,
    val planDuration: Int = 8,
    val injuriesInput: String = "",
    // ISO yyyy-MM-dd; only relevant when sex == "female", blank otherwise
    val lastPeriodDate: String = "",
    // --- Bring-your-own-plan (optional step 4) ---
    // When true the user already follows one half of the plan and the AI only writes the other.
    val hasExistingPlan: Boolean = false,
    // Which half the user supplies: "strength" (gym) or "cardio" (running).
    val providedDomain: String = "strength",
    val pastedPlanText: String = "",
    val attachments: List<PlanAttachment> = emptyList(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: UserRepository,
    private val attachmentReader: PlanAttachmentReader,
) : ViewModel() {

    var currentStep by mutableIntStateOf(1)
        private set

    var uiState by mutableStateOf(OnboardingState())
        private set

    // Loading State
    var isLoading by mutableStateOf(false)
        private set

    /**
     * Non-null when the backend refused to generate a plan for entitlement reasons.
     *
     * Reachable in practice: the free tier allows one generated plan in total, and
     * `wipeDataAndRegenerate` only clears the local Room cache — the server still counts the
     * WorkoutPlan documents it holds, so a second pass through onboarding gets a 402.
     */
    var premiumPrompt by mutableStateOf<PremiumRequiredReason?>(null)
        private set

    fun dismissPremiumPrompt() {
        premiumPrompt = null
    }

    /**
     * Non-null when the last file the user picked could not be attached. The screen turns it into
     * localized snackbar copy; keeping it typed avoids English strings leaking out of the VM.
     */
    var attachmentError by mutableStateOf<PlanAttachmentError?>(null)
        private set

    fun dismissAttachmentError() {
        attachmentError = null
    }

    /** True once the backend read the material but found no routine in it (HTTP 422). */
    var planNotRecognized by mutableStateOf(false)
        private set

    fun dismissPlanNotRecognized() {
        planNotRecognized = false
    }

    // The import step only exists when the user says they already have half a plan.
    val totalSteps: Int
        get() = if (uiState.hasExistingPlan) 4 else 3

    // State Updates
    fun updateAge(value: String) {
        uiState = uiState.copy(age = value)
    }
    fun updateWeight(value: String) {
        uiState = uiState.copy(weight = value)
    }
    fun updateHeight(value: String) {
        uiState = uiState.copy(height = value)
    }
    fun updateSex(value: String) {
        // Clear the period date if the user is no longer female, so we never send stale data
        uiState = if (value == "female") {
            uiState.copy(sex = value)
        } else {
            uiState.copy(sex = value, lastPeriodDate = "")
        }
    }
    fun updateGoal(value: String) {
        uiState = uiState.copy(goal = value)
    }
    fun updateFitnessLevel(value: String) {
        uiState = uiState.copy(fitnessLevel = value)
    }
    fun updateInjuries(value: String) {
        uiState = uiState.copy(injuriesInput = value)
    }
    fun updateLastPeriodDate(value: String) {
        uiState = uiState.copy(lastPeriodDate = value)
    }
    fun updateDaysAvailable(value: Int) {
        uiState = uiState.copy(daysAvailable = value)
    }
    fun updatePlanDuration(value: Int) {
        uiState = uiState.copy(planDuration = value)
    }

    // --- Bring-your-own-plan ---

    fun toggleExistingPlan(value: Boolean) {
        // Turning it off discards whatever was staged, so a stale PDF can never be submitted.
        uiState = if (value) {
            uiState.copy(hasExistingPlan = true)
        } else {
            uiState.copy(hasExistingPlan = false, pastedPlanText = "", attachments = emptyList())
        }
    }

    fun updateProvidedDomain(value: String) {
        uiState = uiState.copy(providedDomain = value)
    }
    fun updatePastedPlanText(value: String) {
        uiState = uiState.copy(pastedPlanText = value)
    }

    fun addAttachment(uri: Uri) {
        if (uiState.attachments.size >= MAX_PLAN_ATTACHMENTS) {
            attachmentError = PlanAttachmentError.TOO_MANY
            return
        }

        viewModelScope.launch {
            attachmentReader.read(uri)
                .onSuccess { attachment ->
                    // The backend caps the *decoded* total, so check the running sum, not just the
                    // file we just read. Base64 carries 3 bytes per 4 characters.
                    val decodedBytes = (uiState.attachments + attachment)
                        .sumOf { it.dto.data.length.toLong() * 3 / 4 }
                    if (decodedBytes > MAX_PLAN_ATTACHMENT_BYTES) {
                        attachmentError = PlanAttachmentError.TOO_LARGE
                        return@onSuccess
                    }
                    attachmentError = null
                    uiState = uiState.copy(attachments = uiState.attachments + attachment)
                }
                .onFailure { exception ->
                    Log.e("ONBOARDING", "Could not attach $uri", exception)
                    attachmentError = (exception as? PlanAttachmentException)?.error
                        ?: PlanAttachmentError.UNREADABLE
                }
        }
    }

    fun removeAttachment(index: Int) {
        uiState = uiState.copy(
            attachments = uiState.attachments.filterIndexed { i, _ -> i != index },
        )
    }

    // --- Validation Logic ---
    // --- Validation Logic ---
    fun validateCurrentStep(): String? {
        return when (currentStep) {
            1 -> {
                // Validation for Step 1: Biometrics
                val weightNum = uiState.weight.toDoubleOrNull()
                val heightNum = uiState.height.toDoubleOrNull()
                val ageInt = uiState.age.toIntOrNull()

                if (uiState.weight.isBlank() || weightNum == null || weightNum < 30) {
                    return "Please enter a valid weight (min 30kg)."
                }
                if (uiState.height.isBlank() || heightNum == null || heightNum < 100) {
                    return "Please enter a valid height (min 100cm)."
                }
                if (uiState.age.isBlank() || ageInt == null || ageInt < 16) {
                    return "You must be at least 16 years old."
                }
                if (uiState.sex.isBlank()) {
                    return "Please select a biological sex."
                }
                if (uiState.sex == "female" && uiState.lastPeriodDate.isBlank()) {
                    return "Please enter the start date of your last period."
                }
                null // Passed validation for Step 1
            }
            2 -> {
                // Validation for Step 2: Athletic Profile
                if (uiState.goal.isBlank()) {
                    return "Please select a primary fitness goal."
                }
                if (uiState.fitnessLevel.isBlank()) {
                    return "Please select your current experience level."
                }
                // Injuries are optional, so no strict check is needed here
                null // Passed validation for Step 2
            }
            3 -> {
                // Validation for Step 3: Logistics
                if (uiState.daysAvailable !in 1..7) {
                    return "Available days must be between 1 and 7."
                }
                if (uiState.planDuration !in listOf(4, 8, 12)) {
                    return "Plan duration must be 4, 8, or 12 weeks."
                }
                if (uiState.hasExistingPlan && uiState.providedDomain !in listOf("strength", "cardio")) {
                    return "Tell us which part of your plan you already have."
                }
                null // Passed validation for Step 3
            }
            4 -> {
                // Validation for Step 4: the plan the user already follows
                if (uiState.pastedPlanText.isBlank() && uiState.attachments.isEmpty()) {
                    return "Paste your routine or attach a PDF/photo of it."
                }
                null // Passed validation for Step 4
            }
            else -> null // Default fallback
        }
    }

    fun nextStep() {
        if (currentStep < totalSteps) currentStep++
    }

    fun previousStep() {
        if (currentStep > 1) currentStep--
    }

    // --- Real Network Request ---
    fun submitOnboarding(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true

            val parsedInjuries = uiState.injuriesInput
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val payload = ProfileUpdateRequest(
                age = uiState.age.toIntOrNull() ?: 0,
                weight = uiState.weight.toDoubleOrNull() ?: 0.0,
                height = uiState.height.toDoubleOrNull() ?: 0.0,
                sex = uiState.sex,
                goal = uiState.goal,
                fitnessLevel = uiState.fitnessLevel,
                daysAvailable = uiState.daysAvailable,
                planDuration = uiState.planDuration,
                injuries = parsedInjuries,
                lastPeriodDate = uiState.lastPeriodDate
                    .takeIf { uiState.sex == "female" && it.isNotBlank() },
            )

            // 1. Save user profile data
            val profileResult = repository.updateProfile(payload)

            profileResult.onSuccess {
                Log.d("API_SUCCESS", "Profile saved to MongoDB")

                // 2. Automatically trigger the AI workout build. When the user brought half a
                // plan of their own we import it and the backend only writes the missing domain.
                val aiResult = if (uiState.hasExistingPlan) {
                    repository.importAiPlan(
                        planDuration = uiState.planDuration,
                        goal = uiState.goal,
                        providedDomain = uiState.providedDomain,
                        sourceText = uiState.pastedPlanText,
                        attachments = uiState.attachments.map { it.dto },
                    )
                } else {
                    repository.generateAiPlan(
                        planDuration = uiState.planDuration,
                        goal = uiState.goal,
                    )
                }

                aiResult.onSuccess {
                    Log.d("API_SUCCESS", "Gemini successfully generated and saved the workout plan")
                    isLoading = false
                    onSuccess() // Navigate to HomeScreen
                }.onFailure { exception ->
                    isLoading = false
                    Log.e("API_ERROR", "Profile saved, but AI generation failed: ${exception.message}", exception)

                    when (exception) {
                        // Not a failure the user can retry away — they need to subscribe.
                        is PremiumRequiredException -> premiumPrompt = exception.reason
                        // User-fixable: they stay on the import step and try a clearer document.
                        is PlanNotRecognizedException -> planNotRecognized = true
                        is PlanImportRejectedException -> onError(exception.message)
                        else -> onError("Profile saved, but plan generation failed. You can retry later.")
                    }
                }
            }.onFailure { exception ->
                isLoading = false
                Log.e("API_ERROR", "Failed to connect to backend: ${exception.message}", exception)
                onError(exception.message ?: "Unknown network error")
            }
        }
    }
}
