package com.example.hybrid_ai_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.navigation.SetupNavGraph
import com.example.hybrid_ai_app.ui.theme.HybridTrainingTheme
import dagger.hilt.android.AndroidEntryPoint

// Marks this activity as a setup point for dependency injection
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HybridTrainingTheme {
                val navController = rememberNavController()
                SetupNavGraph(navController = navController)
            }
        }
    }
}