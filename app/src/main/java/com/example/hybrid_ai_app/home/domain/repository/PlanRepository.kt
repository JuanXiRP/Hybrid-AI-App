package com.example.hybrid_ai_app.home.domain.repository

import com.example.hybrid_ai_app.home.domain.model.ActivePlan

interface PlanRepository {
    suspend fun getActivePlan(token: String): Result<ActivePlan>
}