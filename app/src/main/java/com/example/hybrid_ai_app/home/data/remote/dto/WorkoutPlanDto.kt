package com.example.hybrid_ai_app.home.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutPlanDto(
    @SerialName("_id")
    val id: String = "",          // Mapea el _id de MongoDB
    val startDate: String = "",   // Mapea la fecha de creación de MongoDB
    val active: Boolean = true,   // Mapea el estado del plan
    val durationWeeks: Int,
    val goal: String,
    val weeks: List<WeekDto>
)

// Las clases WeekDto, DayDto y ExerciseDto se quedan igual
@Serializable
data class WeekDto(
    val weekNumber: Int,
    val days: List<DayDto>
)

@Serializable
data class DayDto(
    val dayName: String,
    val exercises: List<ExerciseDto>
)

@Serializable
data class ExerciseDto(
    val name: String,
    val sets: String,
    val reps: String,
    val rpe: String
)