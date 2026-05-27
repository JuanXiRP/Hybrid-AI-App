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
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.navigation.RootNavGraph
import com.example.hybrid_ai_app.navigation.Screen
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

            // Creates the global NavController for the Root graph
            val navController = rememberNavController()

            var startDestination by remember { mutableStateOf<String?>(null) }

            // Safe async read: Doesn't block the UI thread
            LaunchedEffect(Unit) {
                val token = preferencesManager.getToken()
                // 🟢 Uses your strictly typed Sealed Classes from NavGraph.kt
                startDestination = if (token.isNullOrEmpty()) {
                    Screen.Auth.route
                } else {
                    Screen.MainContainer.route
                }
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
                    if (startDestination == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // 🟢 Connects to your modern Navigation Graph
                        RootNavGraph(
                            navController = navController,
                            startDestination = startDestination!!
                        )
                    }
                }
            }
        }
    }
}