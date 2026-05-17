package com.example.hybrid_ai_app.onboarding.data.remote

import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Header

interface UserApi {
    @PATCH("api/users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String, // JWT Token from login
        @Body request: ProfileUpdateRequest
    ): Response<Unit>
}