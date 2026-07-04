package com.example.hybrid_ai_app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.util.JwtUtils
import com.example.hybrid_ai_app.navigation.RootNavGraph
import com.example.hybrid_ai_app.navigation.Screen
import com.example.hybrid_ai_app.ui.theme.HybridTrainingTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val currentLanguage by preferencesManager.languageFlow.collectAsState(initial = "en")
            val isDarkMode by preferencesManager.darkModeFlow.collectAsState(initial = false)

            val navController = rememberNavController()
            var startDestination by remember { mutableStateOf<String?>(null) }

            // 🟢 EL FIX: Actualizamos los recursos de la Actividad original
            val context = LocalContext.current
            val updatedConfiguration = remember(currentLanguage) {
                val locale = Locale(currentLanguage)
                Locale.setDefault(locale)

                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)

                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                config
            }

            LaunchedEffect(Unit) {
                val token = preferencesManager.getToken()
                startDestination = if (token.isNullOrEmpty() || JwtUtils.isExpired(token)) {
                    // Drop an expired/invalid token so we don't fire doomed requests
                    if (!token.isNullOrEmpty()) preferencesManager.clearToken()
                    Screen.Auth.route
                } else {
                    Screen.MainContainer.route
                }
            }


            CompositionLocalProvider(LocalConfiguration provides updatedConfiguration) {
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
}