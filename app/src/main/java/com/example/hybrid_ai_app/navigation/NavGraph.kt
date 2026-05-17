package com.example.hybrid_ai_app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hybrid_ai_app.auth.presentation.AuthScreen
import com.example.hybrid_ai_app.onboarding.presentation.OnboardingScreen

@Composable
fun SetupNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Auth.route
    ) {
        composable(route = Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    // Clears the backstack so the user cannot navigate back to login
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )

        }

        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }

            )
        }

        // TODO: Bind the rest of the routes
    }
}