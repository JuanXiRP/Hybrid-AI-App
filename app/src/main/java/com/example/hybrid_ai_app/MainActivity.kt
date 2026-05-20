package com.example.hybrid_ai_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.home.presentation.MainScaffold
import com.example.hybrid_ai_app.ui.theme.HybridTrainingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the preferences manager singleton
        val preferencesManager = PreferencesManager(applicationContext)

        setContent {
            // Reactively collect the language state
            val currentLanguage by preferencesManager.languageFlow.collectAsState(initial = "en")
            val isDarkMode by preferencesManager.darkModeFlow.collectAsState(initial = true)

            // Trigger system locale update when DataStore value changes
            LaunchedEffect(currentLanguage) {
                val localeList = LocaleListCompat.forLanguageTags(currentLanguage)
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            HybridTrainingTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScaffold()
                }
            }
        }
    }
}