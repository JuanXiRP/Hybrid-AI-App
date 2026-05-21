package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.remote.GeneratePlanRequest
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import javax.inject.Inject

// Implementation of the repository pattern handling API responses safely
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi
) : UserRepository {

    override suspend fun updateProfile(payload: ProfileUpdateRequest): Result<Unit> {
        return try {
            val response = api.updateProfile(payload)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Backend error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateAiPlan(planDuration: Int, goal: String): Result<Unit> {
        return try {
            val request = GeneratePlanRequest(planDuration = planDuration, goal = goal)
            val response = api.generateAiPlan(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                // Handles 503/429 errors from backend (Gemini Rate Limit)
                Result.failure(Exception("Error generating plan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}