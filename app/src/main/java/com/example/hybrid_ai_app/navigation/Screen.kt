package com.example.hybrid_ai_app.navigation

// Defines all the navigation routes for the application
sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Workouts : Screen("workouts")
    object AIChat : Screen("ai_chat")
    object History : Screen("history")
    object LogActivity : Screen("log_activity")
    object Settings : Screen("settings")
    object Premium : Screen("premium")
}