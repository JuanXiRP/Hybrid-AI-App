package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.auth.data.remote.LoginRequest
import com.example.hybrid_ai_app.auth.data.remote.RegisterRequest
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH // 🟢 IMPORTANTE: Importa PATCH
import retrofit2.http.POST

interface UserApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    // 🟢 DEBE SER @PATCH EXACTAMENTE COMO EN NODE.JS
    @PATCH("api/users/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): Response<Unit>

    @POST("api/ai/generate-plan")
    suspend fun generateAiPlan(@Body request: GeneratePlanRequest): Response<Unit>
}