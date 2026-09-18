package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.testing.MockCleanupRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection

/**
 * What happens when the backend rejects the stored JWT.
 *
 * OkHttp only invokes an `Authenticator` on a 401, and there is no refresh endpoint yet, so the
 * only correct behaviour is: clear the session and stop. Returning a retry request here without a
 * new credential would loop until OkHttp's own cap, hammering the backend with a token it has
 * already rejected.
 */
class TokenAuthenticatorTest {

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private val preferencesManager: PreferencesManager = mockk(relaxed = true)
    private val authenticator = TokenAuthenticator(preferencesManager)

    private fun response(
        code: Int = HttpURLConnection.HTTP_UNAUTHORIZED,
        prior: Response? = null,
    ): Response = Response.Builder()
        .request(Request.Builder().url("https://example.test/api/users/profile").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Unauthorized")
        .apply { prior?.let { priorResponse(it) } }
        .build()

    @Test
    fun `a 401 clears the stored token so the next launch returns to the auth screen`() {
        // Arrange
        val unauthorized = response()

        // Act
        val retry = authenticator.authenticate(route = null, response = unauthorized)

        // Assert
        verify(exactly = 1) { preferencesManager.clearTokenSync() }
        assertNull("no retry is attempted without a fresh credential", retry)
    }

    @Test
    fun `a repeated 401 on the same request chain still gives up rather than looping`() {
        // Arrange — a response that already has a prior 401 behind it
        val chained = response(prior = response())

        // Act
        val retry = authenticator.authenticate(route = null, response = chained)

        // Assert
        verify(exactly = 1) { preferencesManager.clearTokenSync() }
        assertNull(retry)
    }

    @Test
    fun `a long prior-response chain is handled without recursing off the end`() {
        // Arrange — three levels deep, exercising the responseCount walk
        val deep = response(prior = response(prior = response()))

        // Act
        val retry = authenticator.authenticate(route = null, response = deep)

        // Assert
        assertNull(retry)
        verify(exactly = 1) { preferencesManager.clearTokenSync() }
    }

    @Test
    fun `the token is cleared synchronously because an interceptor cannot suspend`() {
        // clearTokenSync rather than the suspend clearToken: OkHttp's Authenticator is a blocking
        // callback, so the only option is the synchronous accessor. Pinned so a well-meaning
        // change to the suspend variant does not compile-and-deadlock here.
        // Arrange
        every { preferencesManager.clearTokenSync() } returns Unit

        // Act
        authenticator.authenticate(route = null, response = response())

        // Assert
        verify { preferencesManager.clearTokenSync() }
    }
}
