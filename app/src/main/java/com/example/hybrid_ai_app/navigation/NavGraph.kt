package com.example.hybrid_ai_app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.hybrid_ai_app.auth.presentation.AuthScreen
import com.example.hybrid_ai_app.home.presentation.CoachScreen
import com.example.hybrid_ai_app.home.presentation.ExtraRunScreen
import com.example.hybrid_ai_app.home.presentation.HistoryScreen
import com.example.hybrid_ai_app.home.presentation.HomeScreen
import com.example.hybrid_ai_app.home.presentation.MainScaffold
import com.example.hybrid_ai_app.home.presentation.PaywallScreen
import com.example.hybrid_ai_app.home.presentation.WorkoutExecutionScreen
import com.example.hybrid_ai_app.home.presentation.WorkoutsScreen
import com.example.hybrid_ai_app.home.presentation.workout.WorkoutSessionScreen
import com.example.hybrid_ai_app.home.presentation.workout.WorkoutSessionViewModel
import com.example.hybrid_ai_app.onboarding.presentation.OnboardingScreen
import com.example.hybrid_ai_app.settings.presentation.SettingsScreen

sealed class Screen(val route: String) {
    // Root routes (full screen)
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object MainContainer : Screen("main_container")

    // Inner routes (bottom bar and workouts)
    object Home : Screen("home")
    object Workouts : Screen("workouts")
    object Coach : Screen("coach")
    object History : Screen("history")
    object WorkoutExecution : Screen("workout_execution/{weekNumber}/{dayIndex}") {
        fun createRoute(weekNumber: Int, dayIndex: Int): String = "workout_execution/$weekNumber/$dayIndex"
    }

    // A workout added on top of the plan. `kind` is "strength" or "run".
    object ExtraWorkout : Screen("extra_workout/{kind}") {
        fun createRoute(kind: String): String = "extra_workout/$kind"
    }

    // A past workout reopened for editing, by the id of its stored log.
    object WorkoutEdit : Screen("workout_edit/{logId}") {
        fun createRoute(logId: Long): String = "workout_edit/$logId"
    }
    object Settings : Screen("settings")
    object Paywall : Screen("paywall")
}

// ROOT GRAPH
@Composable
fun RootNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.MainContainer.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
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
                },
            )
        }

        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.MainContainer.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onUpgradeRequired = { navController.navigate(Screen.Paywall.route) },
            )
        }

        composable(route = Screen.MainContainer.route) {
            MainScaffold(rootNavController = navController)
        }
        composable(route = Screen.Paywall.route) {
            com.example.hybrid_ai_app.home.presentation.PaywallScreen(
                navController = navController,
            )
        }
    }
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    rootNavController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController, rootNavController = rootNavController)
        }
        composable(route = Screen.Workouts.route) {
            WorkoutsScreen(navController = navController, rootNavController = rootNavController)
        }
        composable(route = Screen.Coach.route) {
            // rootNavController so the paywall opens full-screen, without the bottom bar.
            CoachScreen(navController = navController, rootNavController = rootNavController)
        }
        composable(route = Screen.History.route) {
            HistoryScreen(navController = navController)
        }

        composable(
            route = Screen.WorkoutExecution.route,
            arguments = listOf(
                navArgument("weekNumber") { type = NavType.IntType },
                navArgument("dayIndex") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val weekNumber = backStackEntry.arguments?.getInt("weekNumber") ?: 1
            val dayIndex = backStackEntry.arguments?.getInt("dayIndex") ?: 0

            WorkoutExecutionScreen(
                weekNumber = weekNumber,
                dayIndex = dayIndex,
                navController = navController,
                rootNavController = rootNavController,
            )
        }

        composable(
            route = Screen.ExtraWorkout.route,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { backStackEntry ->
            // The ViewModels read `kind` from the route themselves.
            if (backStackEntry.arguments?.getString("kind") == WorkoutSessionViewModel.KIND_RUN) {
                ExtraRunScreen(navController = navController, rootNavController = rootNavController)
            } else {
                WorkoutSessionScreen(navController = navController, rootNavController = rootNavController)
            }
        }

        composable(
            route = Screen.WorkoutEdit.route,
            arguments = listOf(navArgument("logId") { type = NavType.LongType }),
        ) {
            // The ViewModel reads `logId` from the route itself, which is what puts the screen in
            // edit mode.
            WorkoutSessionScreen(navController = navController, rootNavController = rootNavController)
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                rootNavController = rootNavController, // Passed to Settings
            )
        }
        // Paywall is deliberately NOT registered here: it lives in RootNavGraph so it covers the
        // bottom bar. Every entry point navigates to it via rootNavController.
    }
}
