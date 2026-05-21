package com.example.hybrid_ai_app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.auth.presentation.AuthScreen
import com.example.hybrid_ai_app.home.presentation.MainScaffold
import com.example.hybrid_ai_app.onboarding.presentation.OnboardingScreen

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication Flow
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    // Navigate to onboarding and remove auth from the backstack preventing back-navigation
                    navController.navigate("onboarding") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Flow
        composable("onboarding") {
            OnboardingScreen(
                onFinishOnboarding = {
                    // Navigate to dashboard and remove onboarding from the backstack
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        // Main Dashboard Flow
        composable("home") {
            // Your existing scaffold that contains the BottomNavigation and internal screens
            MainScaffold()
        }
    }
}