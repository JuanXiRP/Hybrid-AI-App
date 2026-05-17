package com.example.hybrid_ai_app.onboarding.data.repository

import com.example.hybrid_ai_app.onboarding.data.remote.UserApi
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import com.example.hybrid_ai_app.onboarding.domain.repository.UserRepository
import javax.inject.Inject

// Implementation of the repository pattern handling API responses safely
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi
) : UserRepository {

    override suspend fun updateProfile(token: String, payload: ProfileUpdateRequest): Result<Unit> {
        return try {
            val response = api.updateProfile("Bearer $token", payload)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Backend error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}