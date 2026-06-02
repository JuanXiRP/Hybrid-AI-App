package com.example.hybrid_ai_app.home.domain.usecase

import com.example.hybrid_ai_app.home.domain.model.ActivePlan
import com.example.hybrid_ai_app.home.domain.repository.PlanRepository
import javax.inject.Inject

class GetActivePlanUseCase @Inject constructor(
    private val repository: PlanRepository
) {
    suspend operator fun invoke(token: String): Result<ActivePlan> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("Authentication token is missing"))
        }
        return repository.getActivePlan(token)
    }
}