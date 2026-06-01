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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Extension property to ensure a single instance of DataStore per context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hybrid_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("app_language")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")

        // 🟢 Added keys for local user overrides
        val USER_NAME_KEY = stringPreferencesKey("local_user_name")
        val USER_PROFILE_PIC_KEY = stringPreferencesKey("local_profile_pic_path")
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en"
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: true
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[JWT_TOKEN_KEY]
    }

    // 🟢 Reactive flows for UI observation
    val userNameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY]
    }

    val userProfilePicFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_PROFILE_PIC_KEY]
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences -> preferences[LANGUAGE_KEY] = language }
    }

    suspend fun toggleDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences -> preferences[DARK_MODE_KEY] = isDark }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences -> preferences[JWT_TOKEN_KEY] = token }
    }

    suspend fun saveLocalUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[USER_NAME_KEY] = name }
    }

    suspend fun saveLocalProfilePic(path: String) {
        context.dataStore.edit { preferences -> preferences[USER_PROFILE_PIC_KEY] = path }
    }

    fun getTokenSync(): String? {
        return runBlocking { context.dataStore.data.first()[JWT_TOKEN_KEY] }
    }

    suspend fun getToken(): String? {
        return tokenFlow.first()
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences -> preferences.remove(JWT_TOKEN_KEY) }
    }
}