package com.example.hybrid_ai_app.core.domain.model

/**
 * What the current user is allowed to do. Mirrors `GET /api/billing/entitlement`.
 *
 * The backend is authoritative — this is a cache for rendering counters and disabling CTAs.
 * The server re-checks every quota on every write, so a tampered client gains nothing.
 */
data class Entitlement(
    val status: EntitlementStatus,
    val trialDaysLeft: Int,
    val plansUsed: Int,
    val plansLimit: Int?,
    val chatUsed: Int,
    val chatLimit: Int?,
    /** ISO-8601 instant at which the daily chat quota resets (UTC midnight). */
    val chatResetsAt: String?,
) {
    val isPremium: Boolean get() = status == EntitlementStatus.PREMIUM

    /** Read-only mode: the trial ran out and the user has not subscribed. */
    val isReadOnly: Boolean get() = status == EntitlementStatus.EXPIRED

    val canGeneratePlan: Boolean
        get() = !isReadOnly && (plansLimit == null || plansUsed < plansLimit)

    val canSendChatMessage: Boolean
        get() = !isReadOnly && (chatLimit == null || chatUsed < chatLimit)

    val chatMessagesLeft: Int?
        get() = chatLimit?.let { (it - chatUsed).coerceAtLeast(0) }

    companion object {
        /**
         * Assumed state before the first successful fetch. Deliberately permissive: the server
         * enforces the real limits, and pessimistically locking a paying user out of their app
         * because the network blipped is far worse than briefly showing an enabled button.
         */
        val Unknown = Entitlement(
            status = EntitlementStatus.TRIAL,
            trialDaysLeft = 0,
            plansUsed = 0,
            plansLimit = null,
            chatUsed = 0,
            chatLimit = null,
            chatResetsAt = null,
        )
    }
}

enum class EntitlementStatus {
    PREMIUM,
    TRIAL,
    EXPIRED;

    companion object {
        fun fromWire(value: String?): EntitlementStatus = when (value) {
            "premium" -> PREMIUM
            "expired" -> EXPIRED
            else -> TRIAL
        }
    }
}

/**
 * The `code` field of a 402 response. Tells the paywall sheet which copy to show, instead of
 * the UI having to parse a human-readable message.
 */
enum class PremiumRequiredReason {
    TRIAL_EXPIRED,
    PLAN_LIMIT_REACHED,
    CHAT_QUOTA_EXCEEDED,
    UNKNOWN;

    companion object {
        fun fromCode(code: String?): PremiumRequiredReason = when (code) {
            "TRIAL_EXPIRED" -> TRIAL_EXPIRED
            "PLAN_LIMIT_REACHED" -> PLAN_LIMIT_REACHED
            "CHAT_QUOTA_EXCEEDED" -> CHAT_QUOTA_EXCEEDED
            else -> UNKNOWN
        }
    }
}

/**
 * Raised by the repository layer when the backend answers 402. Repositories are the error
 * boundary: a Retrofit `Response` must never reach a ViewModel.
 */
class PremiumRequiredException(
    val reason: PremiumRequiredReason,
    override val message: String,
) : Exception(message)
