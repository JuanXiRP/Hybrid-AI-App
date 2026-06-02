package com.example.hybrid_ai_app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.hybrid_ai_app.auth.presentation.AuthScreen
import com.example.hybrid_ai_app.onboarding.presentation.OnboardingScreen
import com.example.hybrid_ai_app.home.presentation.MainScaffold
import com.example.hybrid_ai_app.home.presentation.HomeScreen
import com.example.hybrid_ai_app.home.presentation.WorkoutsScreen
import com.example.hybrid_ai_app.home.presentation.CoachScreen
import com.example.hybrid_ai_app.home.presentation.HistoryScreen
import com.example.hybrid_ai_app.home.presentation.PaywallScreen
import com.example.hybrid_ai_app.home.presentation.WorkoutExecutionScreen
import com.example.hybrid_ai_app.settings.presentation.SettingsScreen

sealed class Screen(val route: String) {
    // Rutas Raíz (Pantalla Completa)
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object MainContainer : Screen("main_container")

    // Rutas Internas (Barra Inferior y Entrenamientos)
    object Home : Screen("home")
    object Workouts : Screen("workouts")
    object Coach : Screen("coach")
    object History : Screen("history")
    object WorkoutExecution : Screen("workout_execution/{weekNumber}/{dayIndex}") {
        fun createRoute(weekNumber: Int, dayIndex: Int): String {
            return "workout_execution/$weekNumber/$dayIndex"
        }
    }
    object Settings : Screen("settings")
    object Paywall : Screen("paywall")
}

//GRAFO RAÍZ
@Composable
fun RootNavGraph(navController: NavHostController,
                 startDestination: String = Screen.MainContainer.route) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = { hasCompletedOnboarding ->
                    val targetRoute = if (hasCompletedOnboarding) {
                        Screen.MainContainer.route
                    } else {
                        Screen.Onboarding.route
                    }

                    navController.navigate(targetRoute) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.MainContainer.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.MainContainer.route) {
            MainScaffold(rootNavController = navController)
        }
        composable(route = Screen.Paywall.route) {
            com.example.hybrid_ai_app.home.presentation.PaywallScreen(
                navController = navController
            )
        }
    }
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    rootNavController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(route = Screen.Workouts.route) {
            WorkoutsScreen(navController = navController)
        }
        composable(route = Screen.Coach.route) {
            CoachScreen(navController = navController)
        }
        composable(route = Screen.History.route) {
            HistoryScreen(navController = navController)
        }

        composable(
            route = Screen.WorkoutExecution.route,
            arguments = listOf(
                navArgument("weekNumber") { type = NavType.IntType },
                navArgument("dayIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val weekNumber = backStackEntry.arguments?.getInt("weekNumber") ?: 1
            val dayIndex = backStackEntry.arguments?.getInt("dayIndex") ?: 0

            WorkoutExecutionScreen(
                weekNumber = weekNumber,
                dayIndex = dayIndex,
                navController = navController
            )
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                rootNavController = rootNavController // 🟢 Passed to Settings
            )
        }
        composable(route = Screen.Paywall.route) {
            PaywallScreen(navController = navController)
        }
    }
}