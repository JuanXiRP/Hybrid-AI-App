package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.GeneratePlanRequest
import com.example.hybrid_ai_app.core.data.remote.GeneratePlanResponse
import com.example.hybrid_ai_app.core.data.remote.ImportPlanRequest
import com.example.hybrid_ai_app.core.data.remote.PlanAttachmentDto
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementDto
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.data.remote.dto.VerifyPurchaseRequest
import com.example.hybrid_ai_app.core.data.remote.planImportErrorOrNull
import com.example.hybrid_ai_app.core.data.remote.premiumRequiredOrNull
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import retrofit2.Response
import javax.inject.Inject

// Implementation of the repository pattern handling API responses safely
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val dao: WorkoutPlanDao
) : UserRepository {

    override suspend fun updateProfile(payload: ProfileUpdateRequest): Result<Unit> {
        return try {
            val response = api.updateProfile(payload)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Backend error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateAiPlan(planDuration: Int, goal: String): Result<Unit> {
        return try {
            val request = GeneratePlanRequest(planDuration = planDuration, goal = goal)
            cachePlanResponse(api.generateAiPlan(request), "Error generating plan")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importAiPlan(
        planDuration: Int,
        goal: String,
        providedDomain: String,
        sourceText: String?,
        attachments: List<PlanAttachmentDto>
    ): Result<Unit> {
        return try {
            val request = ImportPlanRequest(
                planDuration = planDuration,
                goal = goal,
                providedDomain = providedDomain,
                sourceText = sourceText?.takeIf { it.isNotBlank() },
                attachments = attachments
            )
            val response = api.importAiPlan(request)

            // 400/422 are specific to this endpoint (payload refused / no routine found in the
            // material) and are worth their own typed exceptions; everything else falls through
            // to the shared handling, quota 402 included.
            response.planImportErrorOrNull()
                ?.let { Result.failure(it) }
                ?: cachePlanResponse(response, "Error importing plan")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Both plan endpoints return the same envelope and both make the returned plan the active one,
     * so the DTO→entity→Room hop lives here once.
     */
    private suspend fun cachePlanResponse(
        response: Response<GeneratePlanResponse>,
        errorPrefix: String
    ): Result<Unit> {
        if (response.isSuccessful && response.body()?.data != null) {

            // Extraemos el DTO
            val dto = response.body()!!.data!!

            // Mapeamos el DTO de red a la Entidad local de base de datos
            val entity = WorkoutPlanEntity(
                durationWeeks = dto.durationWeeks,
                goal = dto.goal,
                weeks = dto.weeks
            )

            // Guardamos en Room.
            dao.insertPlan(entity)

            return Result.success(Unit)
        }

        // A 402 means the free plan quota is spent — surface it typed so the UI can open
        // the paywall sheet rather than showing a generic failure.
        return Result.failure(
            response.premiumRequiredOrNull()
                // Handles 503/429 errors from backend (Gemini Rate Limit)
                ?: Exception("$errorPrefix: HTTP ${response.code()}")
        )
    }

    override suspend fun getUserProfile(): Result<UserDto> {
        return try {
            val response = api.getUserProfile()
            if (response.isSuccessful && response.body() != null) {
                // Extracts the actual user object from the "data" wrapper
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception("Error fetching profile: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyPurchase(
        purchaseToken: String,
        productId: String?
    ): Result<Entitlement> {
        return try {
            val response = api.verifyPurchase(VerifyPurchaseRequest(purchaseToken, productId))
            val body = response.body()

            if (response.isSuccessful && body != null) {
                Result.success(body.data.toDomain())
            } else {
                Result.failure(Exception(response.verificationErrorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEntitlement(): Result<Entitlement> {
        return try {
            val response = api.getEntitlement()
            val body = response.body()

            if (response.isSuccessful && body != null) {
                Result.success(body.data.toDomain())
            } else {
                Result.failure(Exception("Error fetching entitlement: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // The repository is the error boundary: a Retrofit Response never escapes this layer.
    private fun Response<*>.verificationErrorMessage(): String = when (code()) {
        409 -> "This subscription is already linked to another account."
        400 -> "Google Play reports this subscription is not active."
        503 -> "Purchases are temporarily unavailable. Please try again later."
        else -> "Could not verify the purchase (HTTP ${code()})."
    }

}

private fun EntitlementDto.toDomain(): Entitlement = Entitlement(
    status = EntitlementStatus.fromWire(status),
    trialDaysLeft = trialDaysLeft,
    plansUsed = plans.used,
    plansLimit = plans.limit,
    chatUsed = chat.used,
    chatLimit = chat.limit,
    chatResetsAt = chat.resetsAt,
)
