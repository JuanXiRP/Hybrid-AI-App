package com.example.hybrid_ai_app.home.presentation

import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity

// Immutable UI state representation for Jetpack Compose
data class WorkoutPlanUiState(
    val isLoading: Boolean = true,
    val plan: WorkoutPlanEntity? = null,
    val errorMessage: String? = null
)