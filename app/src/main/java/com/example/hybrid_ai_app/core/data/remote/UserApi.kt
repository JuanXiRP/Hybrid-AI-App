package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PATCH

interface UserApi {
    @PATCH("api/users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String, // JWT Token from login
        @Body request: ProfileUpdateRequest
    ): Response<Unit>
}