package com.example.hybrid_ai_app.testing

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Retrofit [Response] builders for testing the error-mapping extensions in isolation.
 *
 * These are for the *pure* extension functions (`serverMessageOrNull`, `premiumRequiredOrNull`,
 * `planImportErrorOrNull`), where the input genuinely is a `Response` and no network is involved.
 *
 * Repository tests deliberately do **not** use these — they go through [MockWebServerRule] and a
 * real Retrofit instead, because a hand-built `Response.error(...)` wraps an in-memory buffer and
 * so hides the fact that `errorBody().string()` is a one-shot stream in production.
 */

private const val JSON = "application/json"

/**
 * A non-2xx response whose body is the given JSON (or HTML, for the gateway-error case).
 *
 * Generic in the body type so it can stand in for any `...Api` return type — the body is never
 * deserialised on the failure path, but the compiler still needs the types to line up.
 */
fun <T> errorResponse(code: Int, body: String): Response<T> = Response.error(code, body.toResponseBody(JSON.toMediaType()))

/** A non-2xx response with a completely empty body, which the backend does send on some paths. */
fun <T> emptyErrorResponse(code: Int): Response<T> = Response.error(code, "".toResponseBody(JSON.toMediaType()))

/** A 2xx response carrying an already-deserialised body. */
fun <T> successResponse(body: T): Response<T> = Response.success(body)
