package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest

interface UserRepository {
    // Clean signature: Auth token is handled implicitly by the network interceptor
    suspend fun updateProfile(payload: ProfileUpdateRequest): Result<Unit>

    // Triggers the Gemini AI generation flow on the backend
    suspend fun generateAiPlan(planDuration: Int, goal: String): Result<Unit>
}