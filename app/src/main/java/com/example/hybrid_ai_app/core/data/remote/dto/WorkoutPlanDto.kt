package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.Serializable
// Represents the root JSON structure returned by the AI
@Serializable
data class WorkoutPlanDto(
    val durationWeeks: Int = 0,
    val goal: String,
    val weeks: List<WeekDto>
)

// Represents a single week within the macrocycle
@Serializable
data class WeekDto(
    val weekNumber: Int,
    val days: List<DayDto>
)

// Represents a specific training day
@Serializable
data class DayDto(
    val dayName: String = "Training Day",
    val workoutType: String = "rest", // "strength", "cardio", or "rest"
    val exercises: List<ExerciseDto> = emptyList()
)

// Represents the lowest level component: the exercise parameters
@Serializable
data class ExerciseDto(
    val name: String = "Unknown Exercise",
    val sets: String = "-",
    val reps: String = "-",
    val rpe: String = "-"
)