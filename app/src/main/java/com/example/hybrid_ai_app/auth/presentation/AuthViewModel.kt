package com.example.hybrid_ai_app.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.auth.data.remote.LoginRequest
import com.example.hybrid_ai_app.auth.data.remote.RegisterRequest
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.remote.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: UserApi,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    var isLoginMode by mutableStateOf(true)
        private set

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun toggleAuthMode() {
        isLoginMode = !isLoginMode
    }

    fun updateEmail(newEmail: String) { email = newEmail }
    fun updatePassword(newPassword: String) { password = newPassword }

    fun authenticate(onSuccess: (hasCompletedOnboarding: Boolean) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                onError("Please fill in all fields")
                return@launch
            }

            isLoading = true
            try {
                val response = if (isLoginMode) {
                    api.login(LoginRequest(email = email, password = password))
                } else {
                    val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                    api.register(RegisterRequest(name = name, email = email, password = password))
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val realToken = response.body()?.token

                    // Extract onboarding status from backend response payload if available
                    val hasCompletedOnboarding = response.body()?.hasCompletedOnboarding ?: false

                    if (!realToken.isNullOrEmpty()) {
                        preferencesManager.saveToken(realToken)
                        // New registrations always go to onboarding, logins depend on backend profile status
                        onSuccess(if (isLoginMode) hasCompletedOnboarding else false)
                    } else {
                        onError("Server didn't return a token")
                    }
                } else {
                    onError("Authentication failed: ${response.code()}")
                }

            } catch (e: Exception) {
                onError(e.message ?: "Network error")
            } finally {
                isLoading = false
            }
        }
    }

    fun bypassGoogleAuthForTesting(isExistingUser: Boolean, onSuccess: (hasCompletedOnboarding: Boolean) -> Unit) {
        viewModelScope.launch {
            preferencesManager.saveToken("mock_jwt_token_for_testing")
            onSuccess(isExistingUser)
        }
    }
}