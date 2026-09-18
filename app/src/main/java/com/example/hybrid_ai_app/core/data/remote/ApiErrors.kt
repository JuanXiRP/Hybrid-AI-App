package com.example.hybrid_ai_app.core.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * The envelope every backend failure answers with, e.g.
 * `{"success":false,"message":"User already exists with that email"}`.
 */
@Serializable
data class ApiErrorDto(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
)

// Error bodies are only parsed on the failure path, so a small lenient instance here is cheaper
// than plumbing the Retrofit converter's Json through every caller.
private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * The server's own explanation for a failed call, or null when the body carries none.
 *
 * Retrofit puts the payload of a non-2xx response in [Response.errorBody], **not** [Response.body] —
 * reading `body()` on a failure yields null. That is exactly how the backend's messages were being
 * dropped and replaced with a bare HTTP status: the user saw "Authentication failed: 400" while the
 * server was plainly saying "User already exists with that email".
 *
 * Caution: `errorBody().string()` consumes the stream, so call this **once** per response. Do not
 * chain it with another body-reading helper such as `premiumRequiredOrNull()` on the same object —
 * the second call sees an empty string.
 */
fun Response<*>.serverMessageOrNull(): String? = errorBody()?.string()
    ?.let { raw -> runCatching { errorJson.decodeFromString<ApiErrorDto>(raw) }.getOrNull() }
    ?.message
    ?.takeIf { it.isNotBlank() }
