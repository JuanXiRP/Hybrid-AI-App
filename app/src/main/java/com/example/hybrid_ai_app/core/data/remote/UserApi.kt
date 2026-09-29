package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.auth.data.remote.LoginRequest
import com.example.hybrid_ai_app.auth.data.remote.RegisterRequest
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementResponse
import com.example.hybrid_ai_app.core.data.remote.dto.GoogleAuthRequest
import com.example.hybrid_ai_app.core.data.remote.dto.UserProfileResponse
import com.example.hybrid_ai_app.core.data.remote.dto.VerifyPurchaseRequest
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutRunDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutStrengthDto
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @PATCH("api/users/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): Response<Unit>

    @POST("api/ai/generate-plan")
    suspend fun generateAiPlan(@Body request: GeneratePlanRequest): Response<GeneratePlanResponse>

    /**
     * Parses a plan the user already follows and asks the AI to author only the missing domain.
     * Returns the same envelope as [generateAiPlan], so callers treat both paths identically.
     */
    @POST("api/ai/import-plan")
    suspend fun importAiPlan(@Body request: ImportPlanRequest): Response<GeneratePlanResponse>

    @GET("api/users/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    @POST("api/workouts/strength")
    suspend fun syncStrengthWorkout(@Body payload: WorkoutStrengthDto): Response<Unit>

    /**
     * Creates or edits a strength log, keyed by the client-generated [clientId]. Idempotent: the
     * same id always lands on the same document, which is what makes a retried sync and an edit of
     * a past workout safe.
     */
    @PUT("api/workouts/strength/{clientId}")
    suspend fun upsertStrengthWorkout(
        @Path("clientId") clientId: String,
        @Body body: WorkoutStrengthDto,
    ): Response<Unit>

    @POST("api/workouts/run")
    suspend fun syncRunWorkout(@Body payload: WorkoutRunDto): Response<Unit>

    @POST("api/auth/google")
    suspend fun googleLogin(@Body request: GoogleAuthRequest): Response<AuthResponse>

    /**
     * Validates a Play purchase token server-side and grants premium.
     *
     * Idempotent, so "Restore Purchases" simply replays every token from
     * [BillingManager.queryPurchases] through here. Returns the caller's fresh entitlement.
     */
    @POST("api/billing/verify")
    suspend fun verifyPurchase(@Body request: VerifyPurchaseRequest): Response<EntitlementResponse>

    @GET("api/billing/entitlement")
    suspend fun getEntitlement(): Response<EntitlementResponse>
}
