package com.example.hybrid_ai_app.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybrid_ai_app.core.data.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager // 🟢 Hilt injects this automatically
) : ViewModel() {

    // Expose state to the UI
    val currentLanguage = preferencesManager.languageFlow
    val isDarkMode = preferencesManager.darkModeFlow

    // Handle UI actions
    fun saveLanguage(language: String) {
        viewModelScope.launch {
            preferencesManager.saveLanguage(language)
        }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            preferencesManager.toggleDarkMode(isDark)
        }
    }
}