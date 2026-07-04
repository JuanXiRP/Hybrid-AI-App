package com.example.hybrid_ai_app.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hybrid_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        // Non-sensitive user preferences live in DataStore
        val LANGUAGE_KEY = stringPreferencesKey("app_language")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val USER_NAME_KEY = stringPreferencesKey("local_user_name")
        val USER_PROFILE_PIC_KEY = stringPreferencesKey("local_profile_pic_path")

        // The JWT is sensitive and lives in EncryptedSharedPreferences instead
        private const val SECURE_PREFS_NAME = "hybrid_secure_prefs"
        private const val JWT_TOKEN_KEY = "jwt_token"
    }

    /**
     * AES256-GCM encrypted store whose master key is held in the Android Keystore.
     * Reads are synchronous, which suits the OkHttp auth interceptor (no runBlocking).
     */
    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    private fun createSecurePrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore / keyset corruption recovery: wipe the store and recreate it.
            // Worst case the user re-authenticates once.
            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en"
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

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

    suspend fun saveLocalUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[USER_NAME_KEY] = name }
    }

    suspend fun saveLocalProfilePic(path: String) {
        context.dataStore.edit { preferences -> preferences[USER_PROFILE_PIC_KEY] = path }
    }

    // --- JWT (encrypted) ---

    suspend fun saveToken(token: String) {
        securePrefs.edit().putString(JWT_TOKEN_KEY, token).apply()
    }

    /** Synchronous read — safe from the OkHttp interceptor/authenticator threads. */
    fun getTokenSync(): String? = securePrefs.getString(JWT_TOKEN_KEY, null)

    suspend fun getToken(): String? = getTokenSync()

    suspend fun clearToken() = clearTokenSync()

    /** Synchronous clear — used by the OkHttp Authenticator on 401. */
    fun clearTokenSync() {
        securePrefs.edit().remove(JWT_TOKEN_KEY).apply()
    }
}
