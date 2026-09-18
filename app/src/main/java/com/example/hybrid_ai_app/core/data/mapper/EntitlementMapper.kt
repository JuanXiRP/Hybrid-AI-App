package com.example.hybrid_ai_app.core.data.mapper

import com.example.hybrid_ai_app.core.data.remote.dto.ChatQuotaDto
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementDto
import com.example.hybrid_ai_app.core.data.remote.dto.QuotaDto
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus

/**
 * The single translation between the billing wire DTO and the domain [Entitlement].
 *
 * This used to exist as two byte-identical `private fun EntitlementDto.toDomain()` copies, one in
 * `EntitlementManager` and one in `UserRepositoryImpl`. Two copies of a mapper on the money path
 * means a fix to one is a bug in the other, and a test can only ever pin whichever copy it
 * happens to reach.
 *
 * The conversion is deliberately **not** symmetric, and the asymmetries are load-bearing:
 *
 *  - `trial_ends_at` has no domain field, so [toDomain] drops it and [toDto] writes `null`. The
 *    UI counts down from `trialDaysLeft`, which the backend already computes, so the instant
 *    itself is never needed on the client.
 *  - `is_premium` is never read from the wire. The domain derives premium from
 *    `status == PREMIUM`, so a response claiming `status: "trial"` with `is_premium: true` is
 *    resolved in favour of `status`. One source of truth beats two that can disagree.
 *  - [EntitlementStatus.fromWire] folds `null` and any unrecognised status into `TRIAL`, which is
 *    the permissive choice: an unknown status must not lock a paying user out.
 */
fun EntitlementDto.toDomain(): Entitlement = Entitlement(
    status = EntitlementStatus.fromWire(status),
    trialDaysLeft = trialDaysLeft,
    plansUsed = plans.used,
    plansLimit = plans.limit,
    chatUsed = chat.used,
    chatLimit = chat.limit,
    chatResetsAt = chat.resetsAt,
)

/**
 * The reverse, used only to write the DataStore cache.
 *
 * `trialEndsAt` is written as `null` because the domain never carried it — see the note above.
 * That makes a cache write lossy with respect to the server response it came from, which is
 * acceptable precisely because the cache is only a cold-start placeholder and the backend is
 * re-consulted on every launch.
 */
fun Entitlement.toDto(): EntitlementDto = EntitlementDto(
    status = when (status) {
        EntitlementStatus.PREMIUM -> "premium"
        EntitlementStatus.TRIAL -> "trial"
        EntitlementStatus.EXPIRED -> "expired"
    },
    isPremium = isPremium,
    trialEndsAt = null,
    trialDaysLeft = trialDaysLeft,
    plans = QuotaDto(used = plansUsed, limit = plansLimit),
    chat = ChatQuotaDto(used = chatUsed, limit = chatLimit, resetsAt = chatResetsAt),
)
