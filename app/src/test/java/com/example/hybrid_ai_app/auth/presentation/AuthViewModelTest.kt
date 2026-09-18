package com.example.hybrid_ai_app.auth.presentation

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.auth.data.remote.RegisterRequest
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.MainDispatcherRule
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.TestIds
import com.example.hybrid_ai_app.testing.authResponse
import com.example.hybrid_ai_app.testing.errorResponse
import com.example.hybrid_ai_app.testing.successResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection

/**
 * Sign-in and registration.
 *
 * `AuthViewModel` predates the `...UiState` convention: it exposes Compose `mutableStateOf`
 * properties and reports results through `onSuccess`/`onError` callbacks rather than a flow, so
 * these tests read state directly instead of using Turbine. That is a property of the subject,
 * not a shortcut — there is no flow here to await.
 */
class AuthViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var api: UserApi
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        viewModel = AuthViewModel(api, preferencesManager)
    }

    /** Fills the form with a fresh, unique credential pair. */
    private fun enterCredentials(email: String = TestIds.uniqueEmail()): String {
        viewModel.updateEmail(email)
        viewModel.updatePassword("password123")
        return email
    }

    // ==================== STATE ====================

    @Test
    fun `the form opens in login mode and empty`() {
        // Arrange, Act & Assert
        assertTrue(viewModel.isLoginMode)
        assertEquals("", viewModel.email)
        assertEquals("", viewModel.password)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `toggling switches between login and register`() {
        // Arrange
        assertTrue(viewModel.isLoginMode)

        // Act & Assert
        viewModel.toggleAuthMode()
        assertFalse(viewModel.isLoginMode)

        viewModel.toggleAuthMode()
        assertTrue(viewModel.isLoginMode)
    }

    // ==================== LOGIN ====================

    @Test
    fun `a returning user with a plan is reported as onboarded`() = runTest {
        // Arrange
        val token = TestIds.uniqueToken()
        coEvery { api.login(any()) } returns successResponse(
            authResponse(token = token, hasCompletedOnboarding = true),
        )
        enterCredentials()

        // Act
        var result: Boolean? = null
        viewModel.authenticate(
            onSuccess = { result = it },
            onError = { fail("should not error: $it") },
        )
        advanceUntilIdle()

        // Assert
        assertEquals(true, result)
        coVerify(exactly = 1) { preferencesManager.saveToken(token) }
    }

    @Test
    fun `a user without a plan is routed to onboarding`() = runTest {
        // Arrange
        coEvery { api.login(any()) } returns successResponse(
            authResponse(token = TestIds.uniqueToken(), hasCompletedOnboarding = false),
        )
        enterCredentials()

        // Act
        var result: Boolean? = null
        viewModel.authenticate(
            onSuccess = { result = it },
            onError = { fail("should not error: $it") },
        )
        advanceUntilIdle()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun `bad credentials surface the backend's own message`() = runTest {
        // The regression this guards: Retrofit puts a failure body in errorBody(), not body(), so
        // reading body() dropped the server's explanation and showed a bare status code instead.
        // Arrange
        coEvery { api.login(any()) } returns errorResponse(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            BackendResponses.invalidCredentials(),
        )
        enterCredentials()

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(
            onSuccess = { fail("should not succeed") },
            onError = { errorMessage = it },
        )
        advanceUntilIdle()

        // Assert
        assertEquals("Invalid credentials", errorMessage)
        coVerify(exactly = 0) { preferencesManager.saveToken(any()) }
    }

    @Test
    fun `a Google-only account is told to use Google instead`() = runTest {
        // Arrange
        coEvery { api.login(any()) } returns errorResponse(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            BackendResponses.googleAccountHint(),
        )
        enterCredentials()

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(onSuccess = { fail("should not succeed") }, onError = { errorMessage = it })
        advanceUntilIdle()

        // Assert
        assertEquals(
            "This account uses Google Sign-In. Continue with Google.",
            errorMessage,
        )
    }

    @Test
    fun `a failure with no readable body falls back to the status code`() = runTest {
        // Arrange
        coEvery { api.login(any()) } returns errorResponse(
            HttpURLConnection.HTTP_INTERNAL_ERROR,
            BackendResponses.htmlGatewayError(),
        )
        enterCredentials()

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(onSuccess = { fail("should not succeed") }, onError = { errorMessage = it })
        advanceUntilIdle()

        // Assert
        assertEquals("Authentication failed: 500", errorMessage)
    }

    @Test
    fun `empty fields are rejected without a network call`() = runTest {
        // Arrange — nothing entered

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(onSuccess = { fail("should not succeed") }, onError = { errorMessage = it })
        advanceUntilIdle()

        // Assert
        assertEquals("Please fill in all fields", errorMessage)
        coVerify(exactly = 0) { api.login(any()) }
    }

    @Test
    fun `a success response carrying no token is treated as a failure`() = runTest {
        // Without a token every later request would 401, so this must not be reported as success.
        // Arrange
        coEvery { api.login(any()) } returns successResponse(
            AuthResponse(success = true, token = null, hasCompletedOnboarding = true),
        )
        enterCredentials()

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(onSuccess = { fail("should not succeed") }, onError = { errorMessage = it })
        advanceUntilIdle()

        // Assert
        assertNotNull(errorMessage)
        coVerify(exactly = 0) { preferencesManager.saveToken(any()) }
    }

    @Test
    fun `a network exception is reported rather than escaping`() = runTest {
        // Arrange
        coEvery { api.login(any()) } throws java.io.IOException("Unable to resolve host")
        enterCredentials()

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(onSuccess = { fail("should not succeed") }, onError = { errorMessage = it })
        advanceUntilIdle()

        // Assert
        assertEquals("Unable to resolve host", errorMessage)
    }

    @Test
    fun `the loading flag is cleared once the call settles`() = runTest {
        // Arrange
        coEvery { api.login(any()) } returns successResponse(authResponse())
        enterCredentials()

        // Act
        viewModel.authenticate(onSuccess = { }, onError = { })
        advanceUntilIdle()

        // Assert
        assertFalse("a stuck spinner would block the sign-in button", viewModel.isLoading)
    }

    // ==================== REGISTER ====================

    @Test
    fun `a newly registered user always goes to onboarding`() = runTest {
        // The register endpoint sends no has_completed_onboarding key, and a brand-new account
        // cannot have a plan, so the answer is false regardless of what the body says.
        // Arrange
        viewModel.toggleAuthMode()
        val token = TestIds.uniqueToken()
        coEvery { api.register(any()) } returns successResponse(
            authResponse(token = token, hasCompletedOnboarding = true),
        )
        enterCredentials()

        // Act
        var result: Boolean? = null
        viewModel.authenticate(onSuccess = { result = it }, onError = { fail("should not error: $it") })
        advanceUntilIdle()

        // Assert
        assertEquals(false, result)
        coVerify(exactly = 1) { preferencesManager.saveToken(token) }
    }

    @Test
    fun `a duplicate email surfaces the backend's message`() = runTest {
        // Arrange
        viewModel.toggleAuthMode()
        coEvery { api.register(any()) } returns errorResponse(
            HttpURLConnection.HTTP_BAD_REQUEST,
            BackendResponses.userAlreadyExists(),
        )
        enterCredentials()

        // Act
        var errorMessage: String? = null
        viewModel.authenticate(onSuccess = { fail("should not succeed") }, onError = { errorMessage = it })
        advanceUntilIdle()

        // Assert
        assertEquals("User already exists with that email", errorMessage)
    }

    @Test
    fun `the display name is derived from the local part of the email`() = runTest {
        // Arrange
        viewModel.toggleAuthMode()
        // A fixed address rather than a generated one: this test is specifically about deriving
        // the display name from the local part, so the local part has to be known.
        val email = "ada@example.test"
        val expectedName = "Ada"
        val request = slot<RegisterRequest>()
        coEvery { api.register(capture(request)) } returns successResponse(authResponse())
        viewModel.updateEmail(email)
        viewModel.updatePassword("password123")

        // Act
        viewModel.authenticate(onSuccess = { }, onError = { })
        advanceUntilIdle()

        // Assert
        assertEquals(expectedName, request.captured.name)
        assertEquals(email, request.captured.email)
    }

    // ==================== GOOGLE SIGN-IN ====================

    @Test
    fun `Google sign-in stores the exchanged JWT, not the Google token`() = runTest {
        // The Google ID token is exchanged for the backend's own JWT; storing the wrong one would
        // make every subsequent request unauthorised.
        // Arrange
        val backendJwt = TestIds.uniqueToken()
        val googleIdToken = TestIds.uniqueToken()
        coEvery { api.googleLogin(any()) } returns successResponse(
            authResponse(token = backendJwt, hasCompletedOnboarding = true),
        )

        // Act
        var result: Boolean? = null
        viewModel.loginWithGoogle(
            idToken = googleIdToken,
            onSuccess = { result = it },
            onError = { fail("should not error: $it") },
        )
        advanceUntilIdle()

        // Assert
        assertEquals(true, result)
        coVerify(exactly = 1) { preferencesManager.saveToken(backendJwt) }
        coVerify(exactly = 0) { preferencesManager.saveToken(googleIdToken) }
    }

    @Test
    fun `a rejected Google token surfaces the backend's message`() = runTest {
        // Arrange
        coEvery { api.googleLogin(any()) } returns errorResponse(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            BackendResponses.error("Google authentication failed"),
        )

        // Act
        var errorMessage: String? = null
        viewModel.loginWithGoogle(
            idToken = TestIds.uniqueToken(),
            onSuccess = { fail("should not succeed") },
            onError = { errorMessage = it },
        )
        advanceUntilIdle()

        // Assert
        assertEquals("Google authentication failed", errorMessage)
    }

    @Test
    fun `a Google failure with no body falls back to the status code`() = runTest {
        // Arrange
        coEvery { api.googleLogin(any()) } returns errorResponse(
            HttpURLConnection.HTTP_UNAUTHORIZED,
            BackendResponses.htmlGatewayError(),
        )

        // Act
        var errorMessage: String? = null
        viewModel.loginWithGoogle(
            idToken = TestIds.uniqueToken(),
            onSuccess = { fail("should not succeed") },
            onError = { errorMessage = it },
        )
        advanceUntilIdle()

        // Assert
        assertEquals("Google authentication failed: 401", errorMessage)
    }
}
