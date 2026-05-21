package com.example.hybrid_ai_app.onboarding.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

data class OnboardingState(
    val age: String = "",
    val weight: String = "",
    val height: String = "",
    val sex: String = "male",
    val goal: String = "both",
    val fitnessLevel: String = "beginner",
    val daysAvailable: Int = 3,
    val planDuration: Int = 8,
    val injuriesInput: String = ""
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    var currentStep by mutableIntStateOf(1)
        private set

    var uiState by mutableStateOf(OnboardingState())
        private set

    // Loading State
    var isLoading by mutableStateOf(false)
        private set

    val totalSteps = 3

    // State Updates
    fun updateAge(value: String) { uiState = uiState.copy(age = value) }
    fun updateWeight(value: String) { uiState = uiState.copy(weight = value) }
    fun updateHeight(value: String) { uiState = uiState.copy(height = value) }
    fun updateSex(value: String) { uiState = uiState.copy(sex = value) }
    fun updateGoal(value: String) { uiState = uiState.copy(goal = value) }
    fun updateFitnessLevel(value: String) { uiState = uiState.copy(fitnessLevel = value) }
    fun updateInjuries(value: String) { uiState = uiState.copy(injuriesInput = value) }
    fun updateDaysAvailable(value: Int) { uiState = uiState.copy(daysAvailable = value) }
    fun updatePlanDuration(value: Int) { uiState = uiState.copy(planDuration = value) }

    // --- Validation Logic ---
    fun validateCurrentStep(): String? {
        return when (currentStep) {
            1 -> {
                val ageInt = uiState.age.toIntOrNull()
                val weightNum = uiState.weight.toDoubleOrNull()
                val heightNum = uiState.height.toDoubleOrNull()

                if (weightNum == null || weightNum < 30) return "Please enter a valid weight (min 30kg)."
                if (heightNum == null || heightNum < 100) return "Please enter a valid height (min 100cm)."
                if (ageInt == null || ageInt < 16) return "You must be at least 16 years old."

                null // Passed validation
            }
            else -> null
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
                injuries = parsedInjuries
            )

            // 1. Save user profile data
            val profileResult = repository.updateProfile(payload)

            profileResult.onSuccess {
                Log.d("API_SUCCESS", "Profile saved to MongoDB")

                // 2. Automatically trigger AI Workout Generation
                val aiResult = repository.generateAiPlan(
                    planDuration = uiState.planDuration,
                    goal = uiState.goal
                )

                aiResult.onSuccess {
                    Log.d("API_SUCCESS", "Gemini successfully generated and saved the workout plan")
                    isLoading = false
                    onSuccess() // Navigate to HomeScreen
                }.onFailure { exception ->
                    isLoading = false
                    Log.e("API_ERROR", "Profile saved, but AI generation failed: ${exception.message}", exception)
                    onError("Profile saved, but plan generation failed. You can retry later.")
                }

            }.onFailure { exception ->
                isLoading = false
                Log.e("API_ERROR", "Failed to connect to backend: ${exception.message}", exception)
                onError(exception.message ?: "Unknown network error")
            }
        }
    }
}