package com.example.hybrid_ai_app.core.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class ApiErrorsTest {

    private fun errorResponse(code: Int, body: String): Response<Unit> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))

    @Test
    fun `extracts the backend message from an error body`() {
        val response = errorResponse(
            400,
            """{"success":false,"message":"User already exists with that email"}"""
        )

        assertEquals("User already exists with that email", response.serverMessageOrNull())
    }

    @Test
    fun `reads the Google-account login hint`() {
        val response = errorResponse(
            401,
            """{"success":false,"message":"This account uses Google Sign-In. Continue with Google."}"""
        )

        assertEquals(
            "This account uses Google Sign-In. Continue with Google.",
            response.serverMessageOrNull()
        )
    }

    @Test
    fun `ignores unknown fields such as the billing code and data block`() {
        val response = errorResponse(
            402,
            """{"success":false,"code":"PLAN_LIMIT_REACHED","message":"Your free plan includes one generated routine.","data":{"used":1,"limit":1}}"""
        )

        assertEquals(
            "Your free plan includes one generated routine.",
            response.serverMessageOrNull()
        )
    }

    @Test
    fun `returns null when the body carries no message`() {
        assertNull(errorResponse(500, """{"success":false}""").serverMessageOrNull())
    }

    @Test
    fun `treats a blank message as absent so the caller falls back to the status code`() {
        assertNull(errorResponse(400, """{"success":false,"message":"   "}""").serverMessageOrNull())
    }

    @Test
    fun `returns null rather than throwing on a non-JSON body`() {
        // Render and Cloudflare both answer with HTML on gateway errors.
        assertNull(errorResponse(502, "<html>Bad Gateway</html>").serverMessageOrNull())
    }
}
