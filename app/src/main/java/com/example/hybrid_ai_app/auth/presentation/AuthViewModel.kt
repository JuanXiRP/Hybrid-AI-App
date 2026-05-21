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
    private val preferencesManager: PreferencesManager // 🟢 Hilt inyecta nuestro gestor de DataStore
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

    // Función que llama a tu backend de Node.js
    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                onError("Please fill in all fields")
                return@launch
            }

            isLoading = true
            try {
                // 1. Call the API based on the mode
                val response = if (isLoginMode) {
                    api.login(LoginRequest(email = email, password = password))
                } else {
                    // Extract a dummy name from the email for the register request
                    val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                    api.register(RegisterRequest(name = name, email = email, password = password))
                }

                // 2. Handle the response
                if (response.isSuccessful && response.body()?.success == true) {
                    val realToken = response.body()?.token
                    if (!realToken.isNullOrEmpty()) {
                        // 🟢 Save the token to DataStore!
                        preferencesManager.saveToken(realToken)
                        onSuccess()
                    } else {
                        onError("Server didn't return a token")
                    }
                } else {
                    // Try to parse the backend error message (e.g., "User already exists")
                    val errorBody = response.errorBody()?.string()
                    onError("Authentication failed: ${response.code()}")
                }

            } catch (e: Exception) {
                onError(e.message ?: "Network error")
            } finally {
                isLoading = false
            }
        }
    }
}