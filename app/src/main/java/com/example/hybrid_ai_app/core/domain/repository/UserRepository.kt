package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest

interface UserRepository {
    suspend fun updateProfile(payload: ProfileUpdateRequest): Result<Unit>

    // Triggers the Gemini AI generation flow on the backend
    suspend fun generateAiPlan(planDuration: Int, goal: String): Result<Unit>

    suspend fun getUserProfile(): Result<UserDto>

    /**
     * Sends a Play purchase token to the backend, which validates it against the Play Developer
     * API before granting anything. Idempotent — also used by "Restore Purchases".
     *
     * @return the caller's entitlement as the server now sees it.
     */
    suspend fun verifyPurchase(purchaseToken: String, productId: String?): Result<Entitlement>

    /** Current quotas and trial state, for rendering counters and gating CTAs. */
    suspend fun getEntitlement(): Result<Entitlement>
}
