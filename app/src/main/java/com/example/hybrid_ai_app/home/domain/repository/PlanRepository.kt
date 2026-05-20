package com.example.hybrid_ai_app.home.domain.repository

import com.example.hybrid_ai_app.home.domain.model.ActivePlan

// Abstraction boundary following the Dependency Inversion Principle
interface PlanRepository {
    suspend fun getActivePlan(token: String): Result<ActivePlan>
}