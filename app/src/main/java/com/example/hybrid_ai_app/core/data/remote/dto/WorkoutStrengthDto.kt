package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutStrengthDto(
    @SerialName("_id") val id: String? = null,
    val userId: String? = null,
    val date: String? = null,
    val routineType: String,
    val exercises: List<StrengthExerciseDto> = emptyList(),
    // Plan markers: which planned session this log closed. Optional on the wire (both are
    // omitted when null, since NetworkJson leaves encodeDefaults off), because the backend
    // accepts logs from clients that predate them. They are what lets the AI coach say which
    // sessions are left this week instead of inferring it from how many logs exist.
    val weekNumber: Int? = null,
    val dayIndex: Int? = null,
)

@Serializable
data class StrengthExerciseDto(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val targetWeight: Double,
    val actualWeight: Double? = null,
    val targetRpe: Int,
    val actualRpe: Int? = null,
)
