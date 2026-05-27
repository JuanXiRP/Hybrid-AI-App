package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutStrengthDto(
    @SerialName("_id") val id: String? = null,
    val userId: String? = null,
    val date: String? = null,
    val routineType: String,
    val exercises: List<StrengthExerciseDto> = emptyList()
)

@Serializable
data class StrengthExerciseDto(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val targetWeight: Double,
    val actualWeight: Double? = null,
    val targetRpe: Int,
    val actualRpe: Int? = null
)