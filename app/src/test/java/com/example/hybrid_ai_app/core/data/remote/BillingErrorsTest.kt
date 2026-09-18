package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.core.domain.model.PlanImportRejectedException
import com.example.hybrid_ai_app.core.domain.model.PlanNotRecognizedException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.emptyErrorResponse
import com.example.hybrid_ai_app.testing.errorResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

/**
 * The repository-layer error boundary: a Retrofit `Response` must never reach a ViewModel, and a
 * ViewModel must never read an English message to decide which paywall copy to show.
 *
 * These extensions were entirely untested. They are the only thing translating the backend's
 * machine-readable `code` into a typed reason, so a typo in one of the three code strings would
 * silently degrade every paywall prompt to the generic `UNKNOWN` copy.
 */
class BillingErrorsTest {

    // -------------------------------------------------------------------------------------
    // premiumRequiredOrNull — the 402 family
    // -------------------------------------------------------------------------------------

    @Test
    fun `PLAN_LIMIT_REACHED maps to the plan-limit reason with the server's message`() {
        // Arrange
        val response = errorResponse<Unit>(HTTP_PAYMENT_REQUIRED, BackendResponses.planLimitReached())

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.PLAN_LIMIT_REACHED, exception!!.reason)
        assertEquals("Your free plan includes one generated routine.", exception.message)
    }

    @Test
    fun `CHAT_QUOTA_EXCEEDED maps to the chat-quota reason`() {
        // Arrange
        val response = errorResponse<Unit>(HTTP_PAYMENT_REQUIRED, BackendResponses.chatQuotaExceeded())

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.CHAT_QUOTA_EXCEEDED, exception!!.reason)
        assertEquals("You have used today's coach messages.", exception.message)
    }

    @Test
    fun `TRIAL_EXPIRED maps to the trial-expired reason`() {
        // Arrange
        val response = errorResponse<Unit>(HTTP_PAYMENT_REQUIRED, BackendResponses.trialExpired())

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.TRIAL_EXPIRED, exception!!.reason)
        assertEquals(
            "Your free trial has ended. Subscribe to keep training.",
            exception.message,
        )
    }

    @Test
    fun `the unmodelled data block is tolerated rather than failing the whole parse`() {
        // The client's BillingErrorDto declares only success, code and message — the backend's
        // `data` payload (used/limit/resets_at) is an unknown block. Losing the reason because of
        // a field we chose not to model would turn a precise paywall prompt into generic copy.
        // Arrange
        val response = errorResponse<Unit>(
            HTTP_PAYMENT_REQUIRED,
            BackendResponses.chatQuotaExceeded(used = 2, limit = 2),
        )

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.CHAT_QUOTA_EXCEEDED, exception!!.reason)
    }

    @Test
    fun `an unrecognised code degrades to UNKNOWN instead of throwing`() {
        // Arrange — a code this client version has never heard of, e.g. a newer backend
        val response = errorResponse<Unit>(
            HTTP_PAYMENT_REQUIRED,
            """{"success":false,"code":"SEAT_LIMIT_REACHED","message":"Too many devices."}""",
        )

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.UNKNOWN, exception!!.reason)
        assertEquals("Too many devices.", exception.message)
    }

    @Test
    fun `a 402 with no code at all still produces a typed exception with fallback copy`() {
        // Arrange
        val response = errorResponse<Unit>(HTTP_PAYMENT_REQUIRED, BackendResponses.failureWithNoMessage())

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.UNKNOWN, exception!!.reason)
        assertEquals("Premium required.", exception.message)
    }

    @Test
    fun `a 402 whose body is HTML falls back rather than throwing`() {
        // Render and Cloudflare answer with HTML on gateway errors, and the backend's entitlement
        // guards fall through to Express's default HTML handler when they throw.
        // Arrange
        val response = errorResponse<Unit>(
            HTTP_PAYMENT_REQUIRED,
            BackendResponses.htmlGatewayError(),
        )

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.UNKNOWN, exception!!.reason)
        assertEquals("Premium required.", exception.message)
    }

    @Test
    fun `a 402 with an empty body falls back rather than throwing`() {
        // Arrange
        val response = emptyErrorResponse<Unit>(HTTP_PAYMENT_REQUIRED)

        // Act
        val exception = response.premiumRequiredOrNull()

        // Assert
        assertEquals(PremiumRequiredReason.UNKNOWN, exception!!.reason)
    }

    @Test
    fun `any status other than 402 is left to the caller's own error handling`() {
        // The contract is "returns null for anything else" — repositories rely on that to keep
        // their existing HTTP handling, so a 401 must not be swallowed as a paywall prompt.
        // Arrange
        val statuses = listOf(
            HttpURLConnection.HTTP_BAD_REQUEST,
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN,
            HttpURLConnection.HTTP_NOT_FOUND,
            HTTP_UNPROCESSABLE_ENTITY,
            HttpURLConnection.HTTP_INTERNAL_ERROR,
        )

        // Act & Assert
        statuses.forEach { status ->
            val response = errorResponse<Unit>(status, BackendResponses.planLimitReached())
            assertNull(
                "HTTP $status must not be treated as a paywall response",
                response.premiumRequiredOrNull(),
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // planImportErrorOrNull — the import failures
    // -------------------------------------------------------------------------------------

    @Test
    fun `422 means the material held no readable plan and keeps the user on the import step`() {
        // Arrange
        val response = errorResponse<Unit>(
            HTTP_UNPROCESSABLE_ENTITY,
            BackendResponses.planNotRecognized(),
        )

        // Act
        val exception = response.planImportErrorOrNull()

        // Assert
        assertTrue(
            "expected PlanNotRecognizedException, got ${exception?.javaClass?.simpleName}",
            exception is PlanNotRecognizedException,
        )
        assertEquals("No training plan could be read from the material", exception!!.message)
    }

    @Test
    fun `422 uses fixed local copy and ignores the server's English message`() {
        // The 422 path is user-fixable and shown as localized copy, so the server's wording is
        // deliberately not surfaced — unlike the 400 path below.
        // Arrange
        val response = errorResponse<Unit>(
            HTTP_UNPROCESSABLE_ENTITY,
            BackendResponses.error("some other server wording entirely"),
        )

        // Act
        val exception = response.planImportErrorOrNull()

        // Assert
        assertEquals("No training plan could be read from the material", exception!!.message)
    }

    @Test
    fun `400 surfaces the server's message because it names the specific rejection`() {
        // Arrange
        val serverMessage = "Attachments must total at most 8 MB"
        val response = errorResponse<Unit>(
            HTTP_BAD_REQUEST,
            BackendResponses.importRejected(serverMessage),
        )

        // Act
        val exception = response.planImportErrorOrNull()

        // Assert
        assertTrue(
            "expected PlanImportRejectedException, got ${exception?.javaClass?.simpleName}",
            exception is PlanImportRejectedException,
        )
        assertEquals(serverMessage, exception!!.message)
    }

    @Test
    fun `a 400 carrying no message falls back to generic copy`() {
        // Arrange
        val response = errorResponse<Unit>(HTTP_BAD_REQUEST, BackendResponses.failureWithNoMessage())

        // Act
        val exception = response.planImportErrorOrNull()

        // Assert
        assertTrue(exception is PlanImportRejectedException)
        assertEquals("The plan we received could not be read.", exception!!.message)
    }

    @Test
    fun `a 400 with a non-JSON body falls back rather than throwing`() {
        // Arrange
        val response = errorResponse<Unit>(HTTP_BAD_REQUEST, BackendResponses.htmlGatewayError())

        // Act
        val exception = response.planImportErrorOrNull()

        // Assert
        assertEquals("The plan we received could not be read.", exception!!.message)
    }

    @Test
    fun `statuses other than 400 and 422 are not import errors`() {
        // Arrange
        val statuses = listOf(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HTTP_PAYMENT_REQUIRED,
            HttpURLConnection.HTTP_NOT_FOUND,
            HttpURLConnection.HTTP_INTERNAL_ERROR,
        )

        // Act & Assert
        statuses.forEach { status ->
            val response = errorResponse<Unit>(status, BackendResponses.error("nope"))
            assertNull(
                "HTTP $status must not be treated as an import error",
                response.planImportErrorOrNull(),
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // The status constants themselves
    // -------------------------------------------------------------------------------------

    @Test
    fun `the status constants match the codes the backend actually sends`() {
        // These are compared against the backend's own middleware, which answers 402 from the
        // entitlement guards, 400 from import validation and 422 when Gemini returned no weeks.
        // Arrange, Act & Assert
        assertEquals(402, HTTP_PAYMENT_REQUIRED)
        assertEquals(400, HTTP_BAD_REQUEST)
        assertEquals(422, HTTP_UNPROCESSABLE_ENTITY)
    }
}
