package com.example.hybrid_ai_app.home.data.remote

import com.example.hybrid_ai_app.home.data.remote.dto.ActivePlanResponseDto
import retrofit2.http.GET
import retrofit2.http.Header

interface PlanApiService {
    @GET("api/plans/active")
    suspend fun getActivePlan(
        @Header("Authorization") token: String
    ): ActivePlanResponseDto
}