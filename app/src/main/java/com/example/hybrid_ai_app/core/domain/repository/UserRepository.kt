package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest

// The contract for user-related data operations
interface UserRepository {
    suspend fun updateProfile(token: String, payload: ProfileUpdateRequest): Result<Unit>
}