package com.example.hybrid_ai_app.coach.data

import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.MockWebServerRule
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection

/**
 * The coach chat repository, driven through a real Retrofit against MockWebServer.
 *
 * Going through the real stack rather than a mocked [CoachApi] is what makes these tests worth
 * having: they assert the `@POST("api/ai/chat")` path, that `plan_context` actually appears in the
 * serialised body under its snake_case name, and that `errorBody()` — a one-shot stream in
 * production — is read exactly once on the failure path.
 */
class CoachRepositoryTest {

    @get:Rule
    val server = MockWebServerRule()

    private fun repository() = CoachRepository(server.api(CoachApi::class.java))

    // ------------------------------------------------------------------------------------
    // Success
    // ------------------------------------------------------------------------------------

    @Test
    fun `a successful reply is unwrapped from the data envelope`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.chatReply(reply = "Squat at RPE 8 today."))

            // Act
            val result = repository().sendMessage("How heavy?", planContext = null, history = emptyList())

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("Squat at RPE 8 today.", result.getOrNull())
        }
    }

    @Test
    fun `the request goes to the chat endpoint as a POST`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.chatReply())

            // Act
            repository().sendMessage("Hello", planContext = null, history = emptyList())
            val request = server.takeRequest()

            // Assert
            assertEquals("POST", request.method)
            assertEquals(BackendResponses.Routes.CHAT, request.path)
        }
    }

    @Test
    fun `the plan context is sent under its snake_case wire name`() {
        runTest {
            // The backend destructures `plan_context` out of req.body. If this key were camelCase
            // the request would still succeed, and the coach would silently answer without ever
            // seeing the user's plan.
            // Arrange
            server.enqueueJson(BackendResponses.chatReply())
            val context = "User's active training plan — Goal: both, Duration: 8 weeks."

            // Act
            repository().sendMessage("What is today?", planContext = context, history = emptyList())
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertTrue("expected plan_context in: $body", body.contains("\"plan_context\":"))
            assertTrue(body.contains("Duration: 8 weeks"))
        }
    }

    @Test
    fun `a null plan context is omitted from the body rather than sent as null`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.chatReply())

            // Act
            repository().sendMessage("Hi", planContext = null, history = emptyList())
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertEquals("""{"message":"Hi"}""", body)
        }
    }

    @Test
    fun `conversation history is forwarded so the coach can answer follow-ups`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.chatReply())
            val history = listOf(
                ChatMessageDto(role = "user", content = "Is squatting twice a week fine?"),
                ChatMessageDto(role = "model", content = "Yes, with a lighter second session."),
            )

            // Act
            repository().sendMessage("And deadlifts?", planContext = null, history = history)
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertTrue(body.contains("\"history\":["))
            assertTrue(body.contains("\"role\":\"user\""))
            assertTrue(body.contains("\"role\":\"model\""))
            assertTrue(body.contains("Is squatting twice a week fine?"))
        }
    }

    // ------------------------------------------------------------------------------------
    // Failure
    // ------------------------------------------------------------------------------------

    @Test
    fun `a 402 becomes a typed premium-required failure carrying the backend's code`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.chatQuotaExceeded(),
                code = HttpURLConnection.HTTP_PAYMENT_REQUIRED,
            )

            // Act
            val result = repository().sendMessage("One more?", planContext = null, history = emptyList())

            // Assert
            val error = result.exceptionOrNull()
            assertTrue(
                "expected PremiumRequiredException, got ${error?.javaClass?.simpleName}",
                error is PremiumRequiredException,
            )
            assertEquals(
                PremiumRequiredReason.CHAT_QUOTA_EXCEEDED,
                (error as PremiumRequiredException).reason,
            )
            assertEquals("You have used today's coach messages.", error.message)
        }
    }

    @Test
    fun `an expired trial surfaces as the trial-expired reason, not a quota one`() {
        runTest {
            // The backend stacks requireActiveAccess before requireChatQuota, so an expired user
            // always gets TRIAL_EXPIRED — the paywall copy differs from the quota case.
            // Arrange
            server.enqueueJson(
                BackendResponses.trialExpired(),
                code = HttpURLConnection.HTTP_PAYMENT_REQUIRED,
            )

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertEquals(
                PremiumRequiredReason.TRIAL_EXPIRED,
                (result.exceptionOrNull() as PremiumRequiredException).reason,
            )
        }
    }

    @Test
    fun `a 401 fails with the status code rather than a paywall prompt`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.unauthorized(),
                code = HttpURLConnection.HTTP_UNAUTHORIZED,
            )

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            val error = result.exceptionOrNull()!!
            assertTrue(
                "a 401 must not be reported as a paywall",
                error !is PremiumRequiredException,
            )
            assertEquals("Coach error: HTTP 401", error.message)
        }
    }

    @Test
    fun `a 500 fails with the status code`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.error("Error communicating with Coach AI"),
                code = HttpURLConnection.HTTP_INTERNAL_ERROR,
            )

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertEquals("Coach error: HTTP 500", result.exceptionOrNull()!!.message)
        }
    }

    @Test
    fun `a 200 with a blank reply is treated as a failure, not an empty coach message`() {
        runTest {
            // An empty bubble in the chat would look like the coach ignored the user.
            // Arrange
            server.enqueueJson("""{"success":true,"data":{"reply":"   "}}""")

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `a 200 with no data block is treated as a failure`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.successWithNoData())

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `a dropped connection becomes a failed Result instead of an escaping exception`() {
        runTest {
            // The repository is the error boundary: no IOException may reach the ViewModel.
            // Arrange
            server.enqueueConnectionFailure()

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertTrue(result.isFailure)
            assertTrue(
                "expected an IOException inside the Result, got " +
                    "${result.exceptionOrNull()?.javaClass?.name}",
                result.exceptionOrNull() is IOException,
            )
        }
    }

    @Test
    fun `a read timeout becomes a failed Result`() {
        runTest {
            // Arrange — a body the server never finishes sending
            server.server.enqueue(
                MockResponse()
                    .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE),
            )

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `a non-JSON gateway error does not crash the parse`() {
        runTest {
            // Render answers with HTML on gateway errors.
            // Arrange
            server.server.enqueue(
                MockResponse()
                    .setResponseCode(HttpURLConnection.HTTP_BAD_GATEWAY)
                    .setHeader("Content-Type", "text/html")
                    .setBody(BackendResponses.htmlGatewayError()),
            )

            // Act
            val result = repository().sendMessage("Hello", planContext = null, history = emptyList())

            // Assert
            assertTrue(result.isFailure)
            assertEquals("Coach error: HTTP 502", result.exceptionOrNull()!!.message)
        }
    }
}
