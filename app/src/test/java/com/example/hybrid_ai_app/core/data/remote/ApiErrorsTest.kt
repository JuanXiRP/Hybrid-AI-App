package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.emptyErrorResponse
import com.example.hybrid_ai_app.testing.errorResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpURLConnection

/**
 * Reading the server's own explanation out of a failed response.
 *
 * The regression behind this: Retrofit puts a non-2xx payload in `errorBody()`, not `body()`, so
 * reading `body()` on a failure yields null. That is how the backend's messages were being
 * dropped — the user saw "Authentication failed: 400" while the server was plainly saying
 * "User already exists with that email".
 */
class ApiErrorsTest {

    @Test
    fun `the backend message is extracted from an error body`() {
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_BAD_REQUEST,
            BackendResponses.userAlreadyExists(),
        )

        // Act
        val message = response.serverMessageOrNull()

        // Assert
        assertEquals("User already exists with that email", message)
    }

    @Test
    fun `the Google-account hint is read`() {
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            BackendResponses.googleAccountHint(),
        )

        // Act
        val message = response.serverMessageOrNull()

        // Assert
        assertEquals("This account uses Google Sign-In. Continue with Google.", message)
    }

    @Test
    fun `fields the DTO does not model are ignored rather than failing the parse`() {
        // A 402 carries both `code` and a `data` block; ApiErrorDto models neither fully.
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_PAYMENT_REQUIRED,
            BackendResponses.planLimitReached(),
        )

        // Act
        val message = response.serverMessageOrNull()

        // Assert
        assertEquals("Your free plan includes one generated routine.", message)
    }

    @Test
    fun `a body with no message yields null so the caller can use the status code`() {
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_INTERNAL_ERROR,
            BackendResponses.failureWithNoMessage(),
        )

        // Act & Assert
        assertNull(response.serverMessageOrNull())
    }

    @Test
    fun `a blank message counts as absent`() {
        // Showing whitespace as an error would leave the user staring at an empty dialog.
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_BAD_REQUEST,
            BackendResponses.error("   "),
        )

        // Act & Assert
        assertNull(response.serverMessageOrNull())
    }

    @Test
    fun `a non-JSON body yields null rather than throwing`() {
        // Render and Cloudflare both answer with HTML on gateway errors, and the backend's
        // entitlement guards fall through to Express's default HTML handler when they throw.
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_BAD_GATEWAY,
            BackendResponses.htmlGatewayError(),
        )

        // Act & Assert
        assertNull(response.serverMessageOrNull())
    }

    @Test
    fun `an empty body yields null rather than throwing`() {
        // Arrange
        val response = emptyErrorResponse<Unit>(HttpURLConnection.HTTP_UNAUTHORIZED)

        // Act & Assert
        assertNull(response.serverMessageOrNull())
    }

    @Test
    fun `the error body is a one-shot stream, so a second read sees nothing`() {
        // Documented in ApiErrors.kt and load-bearing: `serverMessageOrNull()` and
        // `premiumRequiredOrNull()` must never both run on the same response, because the second
        // caller gets an empty string and its typed reason silently degrades to UNKNOWN.
        // Arrange
        val response = errorResponse<Unit>(
            HttpURLConnection.HTTP_BAD_REQUEST,
            BackendResponses.userAlreadyExists(),
        )

        // Act
        val firstRead = response.serverMessageOrNull()
        val secondRead = response.serverMessageOrNull()

        // Assert
        assertEquals("User already exists with that email", firstRead)
        assertNull("the stream is consumed by the first read", secondRead)
    }
}
