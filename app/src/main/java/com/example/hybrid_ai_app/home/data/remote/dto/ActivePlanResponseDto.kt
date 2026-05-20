package com.example.hybrid_ai_app.home.data.remote.dto

import com.google.gson.annotations.SerializedName

// Matches the exact JSON envelope from your Node.js backend { success: true, data: { ... } }
data class ActivePlanResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: WorkoutPlanDto?
)

data class WorkoutPlanDto(
    @SerializedName("_id") val id: String,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("durationWeeks") val durationWeeks: Int,
    @SerializedName("goal") val goal: String,
    @SerializedName("weeks") val weeks: List<WeekDto>,
    @SerializedName("active") val active: Boolean
)

data class WeekDto(
    @SerializedName("weekNumber") val weekNumber: Int,
    @SerializedName("days") val days: List<DayDto>
)

data class DayDto(
    @SerializedName("dayName") val dayName: String,
    @SerializedName("exercises") val exercises: List<ExerciseDto>
)

data class ExerciseDto(
    @SerializedName("name") val name: String,
    @SerializedName("sets") val sets: String,
    @SerializedName("reps") val reps: String,
    @SerializedName("rpe") val rpe: String
)