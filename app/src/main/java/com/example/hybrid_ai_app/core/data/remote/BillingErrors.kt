package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.data.remote.dto.BillingErrorDto
import com.example.hybrid_ai_app.core.domain.model.PlanImportRejectedException
import com.example.hybrid_ai_app.core.domain.model.PlanNotRecognizedException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import kotlinx.serialization.json.Json
import retrofit2.Response

const val HTTP_PAYMENT_REQUIRED = 402
const val HTTP_BAD_REQUEST = 400
const val HTTP_UNPROCESSABLE_ENTITY = 422

// Error bodies are only parsed on the failure path, so a small lenient instance here is cheaper
// than plumbing the Retrofit converter's Json into every repository.
private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Turns a 402 into a typed domain exception carrying the backend's machine-readable `code`.
 *
 * Repositories are the error boundary: a Retrofit [Response] must never reach a ViewModel, and a
 * ViewModel must never have to read an English message to know which paywall copy to show.
 *
 * Returns null for any other status, so callers keep their existing error handling.
 */
fun Response<*>.premiumRequiredOrNull(): PremiumRequiredException? {
    if (code() != HTTP_PAYMENT_REQUIRED) return null

    val error = errorBody()?.string()?.let { raw ->
        runCatching { errorJson.decodeFromString<BillingErrorDto>(raw) }.getOrNull()
    }

    return PremiumRequiredException(
        reason = PremiumRequiredReason.fromCode(error?.code),
        message = error?.message ?: "Premium required.",
    )
}

/**
 * Maps the plan-import specific failures to typed domain exceptions, same boundary rule as
 * [premiumRequiredOrNull]: 422 means "we read it but there was no routine in there" (user-fixable,
 * localized copy in the UI), 400 means the payload itself was refused and the server's message is
 * specific enough to surface.
 *
 * Returns null for any other status so the caller keeps its existing handling.
 */
fun Response<*>.planImportErrorOrNull(): Exception? = when (code()) {
    HTTP_UNPROCESSABLE_ENTITY -> PlanNotRecognizedException()
    HTTP_BAD_REQUEST -> {
        val error = errorBody()?.string()?.let { raw ->
            runCatching { errorJson.decodeFromString<BillingErrorDto>(raw) }.getOrNull()
        }
        PlanImportRejectedException(error?.message ?: "The plan we received could not be read.")
    }
    else -> null
}
