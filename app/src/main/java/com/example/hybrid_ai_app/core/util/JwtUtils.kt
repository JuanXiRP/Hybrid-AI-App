package com.example.hybrid_ai_app.core.util

import android.util.Base64

/**
 * Lightweight, client-side JWT inspection. This does NOT verify the token signature —
 * that remains the backend's responsibility — it only decodes the payload to detect an
 * already-expired token so the app can avoid firing requests that are guaranteed to 401.
 */
object JwtUtils {

    /**
     * Returns true if the token is malformed or its `exp` claim is in the past.
     * A token without an `exp` claim is treated as non-expiring (returns false).
     */
    fun isExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return true

            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            )

            val exp = Regex("\"exp\"\\s*:\\s*(\\d+)")
                .find(payloadJson)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?: return false // no exp claim -> cannot say it's expired

            val nowSeconds = System.currentTimeMillis() / 1000
            nowSeconds >= exp
        } catch (e: Exception) {
            // Anything unparseable is not a token we should trust
            true
        }
    }
}
