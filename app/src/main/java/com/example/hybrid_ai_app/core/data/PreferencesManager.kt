package com.example.hybrid_ai_app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to ensure a single instance of DataStore per context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hybrid_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("app_language")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    }

    // Reactive flow for language preference (defaulting to English)
    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en"
    }

    // Reactive flow for theme preference
    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: true
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun toggleDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = isDark
        }
    }
}