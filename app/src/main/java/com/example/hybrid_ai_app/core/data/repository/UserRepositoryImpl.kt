package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.GeneratePlanRequest
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import javax.inject.Inject

// Implementation of the repository pattern handling API responses safely
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val dao: WorkoutPlanDao
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

            if (response.isSuccessful && response.body()?.data != null) {

                // Extraemos el DTO
                val dto = response.body()!!.data!!

                // Mapeamos el DTO de red a la Entidad local de base de datos
                val entity = WorkoutPlanEntity(
                    durationWeeks = dto.durationWeeks,
                    goal = dto.goal,
                    weeks = dto.weeks
                )

                // Guardamos en Room.
                dao.insertPlan(entity)

                Result.success(Unit)
            } else {
                // Handles 503/429 errors from backend (Gemini Rate Limit)
                Result.failure(Exception("Error generating plan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(): Result<UserDto> {
        return try {
            val response = api.getUserProfile()
            if (response.isSuccessful && response.body() != null) {
                // Extracts the actual user object from the "data" wrapper
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception("Error fetching profile: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upgradeToPremium(purchaseToken: String): Result<Boolean> {
        return try {
            Result.success(true)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}