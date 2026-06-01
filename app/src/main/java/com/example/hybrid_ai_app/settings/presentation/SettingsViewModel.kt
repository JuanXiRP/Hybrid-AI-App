package com.example.hybrid_ai_app.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

sealed interface ProfileState {
    object Loading : ProfileState
    data class Success(val user: UserDto) : ProfileState
    data class Error(val message: String) : ProfileState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val userRepository: UserRepository,
    private val workoutPlanRepository: WorkoutPlanRepository
) : ViewModel() {

    val currentLanguage = preferencesManager.languageFlow
    val isDarkMode = preferencesManager.darkModeFlow

    val localUserName = preferencesManager.userNameFlow
    val localProfilePicPath = preferencesManager.userProfilePicFlow

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val result = userRepository.getUserProfile()

                result.onSuccess { user ->
                    // 🟢 FIX 1: The repository already unwraps the response. 'user' is the UserDto.
                    _profileState.value = ProfileState.Success(user)
                }.onFailure { exception ->
                    _profileState.value = ProfileState.Error(exception.message ?: "Failed to load profile")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error(e.localizedMessage ?: "Unknown network error")
            }
        }
    }

    fun updateProfileMetrics(weight: Double?, goal: String, daysAvailable: Int?) {
        viewModelScope.launch {
            // Retrieve current state to access existing user data
            val currentState = _profileState.value
            if (currentState !is ProfileState.Success) return@launch

            val currentUser = currentState.user

            _isUpdating.value = true
            try {
                // 🟢 FIX 2: Construct the strict ProfileUpdateRequest by blending the
                // new UI inputs with the existing data from the database.
                val request = ProfileUpdateRequest(
                    age = currentUser.age ?: 0,
                    weight = weight ?: currentUser.weight ?: 0.0,
                    height = currentUser.height ?: 0.0,
                    sex = currentUser.sex ?: "",
                    goal = goal.ifBlank { currentUser.goal ?: "" },
                    fitnessLevel = currentUser.fitnessLevel ?: "",
                    daysAvailable = daysAvailable ?: currentUser.daysAvailable ?: 0,
                    planDuration = currentUser.planDuration ?: 4,
                    injuries = currentUser.injuries
                )

                val response = userRepository.updateProfile(request)
                if (response.isSuccess) {
                    fetchUserProfile() // Refresh UI state with updated Mongo data
                }
            } catch (e: Exception) {
                // Failsafe catch
            } finally {
                _isUpdating.value = false
            }
        }
    }

    fun saveLanguage(language: String) {
        viewModelScope.launch { preferencesManager.saveLanguage(language) }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch { preferencesManager.toggleDarkMode(isDark) }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            preferencesManager.clearToken()
            onLogoutComplete()
        }
    }
    fun wipeDataAndRegenerate(onCleared: () -> Unit) {
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                workoutPlanRepository.clearActivePlanAndProgress()
                onCleared() // Avisa a la UI para que navegue
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error("Failed to clear plan: ${e.message}")
            } finally {
                _isUpdating.value = false
            }
        }
    }
    fun updateLocalName(newName: String) {
        viewModelScope.launch {
            preferencesManager.saveLocalUserName(newName)
        }
    }

    fun updateProfilePicture(imagePath: String) {
        viewModelScope.launch {
            preferencesManager.saveLocalProfilePic(imagePath)
        }
    }
}