package com.example.hybrid_ai_app.navigation

import androidx.compose.runtime.Composable
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
import com.example.hybrid_ai_app.home.presentation.WorkoutExecutionScreen

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
    object WorkoutExecution : Screen("workout_execution/{workoutId}/{type}") {
        fun createRoute(workoutId: String, type: String) = "workout_execution/$workoutId/$type"
    }
    object Settings : Screen("settings")
}

// 1. GRAFO RAÍZ (Controla la entrada a la app)
@Composable
fun RootNavGraph(navController: NavHostController,
                 startDestination: String = Screen.MainContainer.route) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    // Tras el onboarding, vamos al contenedor principal (MainScaffold)
                    navController.navigate(Screen.MainContainer.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.MainContainer.route) {
            // Aquí cargamos la estructura con la barra inferior
            MainScaffold()
        }

        composable(route = Screen.Settings.route) {
            com.example.hybrid_ai_app.settings.presentation.SettingsScreen(navController = navController)
        }
    }
}

// 2. GRAFO PRINCIPAL (Controla las pestañas y la navegación profunda)
@Composable
fun MainNavGraph(navController: NavHostController) {
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

        // Ruta dinámica para iniciar un entrenamiento
        composable(
            route = Screen.WorkoutExecution.route,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getString("workoutId") ?: ""
            val type = backStackEntry.arguments?.getString("type") ?: ""
            WorkoutExecutionScreen(workoutId = workoutId, workoutType = type, navController = navController)
        }
        composable(route = Screen.Settings.route) {
            // Usamos el import completo para evitar conflictos si tienes otro SettingsScreen
            com.example.hybrid_ai_app.settings.presentation.SettingsScreen(navController = navController)
        }
    }
}