package com.example.hybrid_ai_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.ui.theme.HybridTrainingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferencesManager = PreferencesManager(applicationContext)

        setContent {
            val currentLanguage by preferencesManager.languageFlow.collectAsState(initial = "en")
            val isDarkMode by preferencesManager.darkModeFlow.collectAsState(initial = true)

            // 1. Hold the start destination in a state
            var startDestination by remember { mutableStateOf<String?>(null) }

            // 2. Read the token ONLY ONCE when the app starts
            LaunchedEffect(Unit) {
                // Using the synchronous read we created earlier
                val token = preferencesManager.getTokenSync()
                startDestination = if (token.isNullOrEmpty()) "auth" else "home"
            }

            LaunchedEffect(currentLanguage) {
                val localeList = LocaleListCompat.forLanguageTags(currentLanguage)
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            HybridTrainingTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 3. Only render the NavHost when we know where to go
                    if (startDestination == null) {
                        // Opcional: Mostrar una pantalla de carga o logo
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AppNavigation(startDestination = startDestination!!)
                    }
                }
            }
        }
    }
}