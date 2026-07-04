package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.data.PreferencesManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reacts to a 401 from the backend. There is no refresh-token endpoint yet, so a 401
 * means the stored JWT is expired or invalid: we clear it (forcing the user back to the
 * auth screen on next launch) and return null so OkHttp stops retrying — avoiding an
 * infinite auth loop.
 *
 * When the backend gains a refresh endpoint, this is where the token would be rotated
 * and the failed request retried with the new credential.
 */
class TokenAuthenticator(
    private val preferencesManager: PreferencesManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up (and clear the session) once we've already seen a 401 for this request.
        if (responseCount(response) >= 2) {
            preferencesManager.clearTokenSync()
            return null
        }
        preferencesManager.clearTokenSync()
        return null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
