package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutPlanDto
import kotlinx.serialization.Serializable

// 1. Lo que ENVIAMOS al backend (Request)
@Serializable
data class GeneratePlanRequest(
    val planDuration: Int,
    val goal: String
)

// 2. Lo que RECIBIMOS del backend (Response Wrapper)
@Serializable
data class GeneratePlanResponse(
    val success: Boolean,
    val data: WorkoutPlanDto? = null
)