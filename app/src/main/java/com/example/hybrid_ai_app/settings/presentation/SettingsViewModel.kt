package com.example.hybrid_ai_app.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Sealed interface for strict state handling
sealed interface ProfileState {
    object Loading : ProfileState
    data class Success(val user: UserDto) : ProfileState // Replace UserDto with your domain model if mapped
    data class Error(val message: String) : ProfileState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val userRepository: UserRepository // 🟢 Injected Repository
) : ViewModel() {

    val currentLanguage = preferencesManager.languageFlow
    val isDarkMode = preferencesManager.darkModeFlow

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            // 🟢 Assumes you have a getUserProfile() function in your repository
            val result = userRepository.getUserProfile()
            result.onSuccess { user ->
                _profileState.value = ProfileState.Success(user)
            }.onFailure { exception ->
                _profileState.value = ProfileState.Error(exception.message ?: "Failed to load profile")
            }
        }
    }

    fun saveLanguage(language: String) {
        viewModelScope.launch { preferencesManager.saveLanguage(language) }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch { preferencesManager.toggleDarkMode(isDark) }
    }

    // 🟢 Executes Logout by clearing the token
    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            preferencesManager.clearToken()
            // Optional: clear Room database tables here if you want a complete data wipe on logout
            onLogoutComplete()
        }
    }
}