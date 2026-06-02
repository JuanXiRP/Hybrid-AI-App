package com.example.hybrid_ai_app.home.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivePlanResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: WorkoutPlanDto?
)