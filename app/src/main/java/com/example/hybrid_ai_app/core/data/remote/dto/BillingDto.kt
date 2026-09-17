package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class VerifyPurchaseRequest(
    val purchaseToken: String,
    val productId: String? = null,
)

@Serializable
data class EntitlementDto(
    /** "premium" | "trial" | "expired" */
    val status: String = "expired",
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("trial_ends_at") val trialEndsAt: String? = null,
    @SerialName("trial_days_left") val trialDaysLeft: Int = 0,
    val plans: QuotaDto = QuotaDto(),
    val chat: ChatQuotaDto = ChatQuotaDto(),
)

@Serializable
data class QuotaDto(
    val used: Int = 0,
    /** null means unlimited (premium). */
    val limit: Int? = null,
)

@Serializable
data class ChatQuotaDto(
    val used: Int = 0,
    val limit: Int? = null,
    @SerialName("resets_at") val resetsAt: String? = null,
)

@Serializable
data class EntitlementResponse(
    val success: Boolean = false,
    val data: EntitlementDto = EntitlementDto(),
)

/** Body of a 402. `code` is what tells the UI which paywall copy to show. */
@Serializable
data class BillingErrorDto(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
)
