package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutPlanDto
import kotlinx.serialization.Serializable

@Serializable
data class GeneratePlanRequest(
    val planDuration: Int,
    val goal: String
)

@Serializable
data class GeneratePlanResponse(
    val success: Boolean,
    val data: WorkoutPlanDto? = null
)