package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
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