package com.example.hybrid_ai_app.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {
    // Estado para alternar entre Login (true) y Registro (false)
    var isLoginMode by mutableStateOf(true)
        private set

    fun toggleAuthMode() {
        isLoginMode = !isLoginMode
    }

    // Aquí añadiremos más adelante la lógica de validación y llamada al Backend
}