package com.example.hybrid_ai_app.auth.presentation

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.core.data.PreferencesManager
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.GoogleAuthRequest
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var api: UserApi
    private lateinit var preferencesManager: PreferencesManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        viewModel = AuthViewModel(api, preferencesManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== STATE ====================
    @Test
    fun `initial state is login mode`() {
        assertTrue(viewModel.isLoginMode)
        assertEquals("", viewModel.email)
        assertEquals("", viewModel.password)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `toggleAuthMode switches between login and register`() {
        assertTrue(viewModel.isLoginMode)
        viewModel.toggleAuthMode()
        assertFalse(viewModel.isLoginMode)
        viewModel.toggleAuthMode()
        assertTrue(viewModel.isLoginMode)
    }

    // ==================== LOGIN ====================
    @Test
    fun `login saves token and calls onSuccess with onboarding flag true`() = runTest {
        coEvery { api.login(any()) } returns Response.success(
            AuthResponse(success = true, token = "real-jwt", hasCompletedOnboarding = true)
        )
        coEvery { preferencesManager.saveToken(any()) } just Runs

        viewModel.updateEmail("test@example.com")
        viewModel.updatePassword("password123")

        var result: Boolean? = null
        viewModel.authenticate(
            onSuccess = { result = it },
            onError = { fail("Should not error") }
        )

        assertEquals(true, result)
        coVerify { preferencesManager.saveToken("real-jwt") }
    }

    @Test
    fun `login returns onboarding false for user without plan`() = runTest {
        coEvery { api.login(any()) } returns Response.success(
            AuthResponse(success = true, token = "jwt-token", hasCompletedOnboarding = false)
        )
        coEvery { preferencesManager.saveToken(any()) } just Runs

        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password123")

        var result: Boolean? = null
        viewModel.authenticate(
            onSuccess = { result = it },
            onError = { fail("Should not error") }
        )

        assertEquals(false, result)
    }

    @Test
    fun `login calls onError on failed response`() = runTest {
        coEvery { api.login(any()) } returns Response.error(
            401, "Unauthorized".toResponseBody()
        )

        viewModel.updateEmail("test@example.com")
        viewModel.updatePassword("wrong")

        var errorMsg: String? = null
        viewModel.authenticate(
            onSuccess = { fail("Should not succeed") },
            onError = { errorMsg = it }
        )

        assertNotNull(errorMsg)
    }

    @Test
    fun `login calls onError when fields are empty`() = runTest {
        var errorMsg: String? = null
        viewModel.authenticate(
            onSuccess = { fail("Should not succeed") },
            onError = { errorMsg = it }
        )

        assertEquals("Please fill in all fields", errorMsg)
    }

    // ==================== REGISTER ====================
    @Test
    fun `register saves token and always returns false for onboarding`() = runTest {
        viewModel.toggleAuthMode()

        coEvery { api.register(any()) } returns Response.success(
            AuthResponse(success = true, token = "new-jwt", hasCompletedOnboarding = false)
        )
        coEvery { preferencesManager.saveToken(any()) } just Runs

        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password123")

        var result: Boolean? = null
        viewModel.authenticate(
            onSuccess = { result = it },
            onError = { fail("Should not error") }
        )

        assertEquals(false, result)
        coVerify { preferencesManager.saveToken("new-jwt") }
    }

    //  GOOGLE SIGN-IN
    @Test
    fun `loginWithGoogle saves token and returns onboarding flag`() = runTest {
        coEvery { api.googleLogin(any()) } returns Response.success(
            AuthResponse(success = true, token = "google-jwt", hasCompletedOnboarding = true)
        )
        coEvery { preferencesManager.saveToken(any()) } just Runs

        var result: Boolean? = null
        viewModel.loginWithGoogle(
            idToken = "fake-google-token",
            onSuccess = { result = it },
            onError = { fail("Should not error") }
        )

        assertEquals(true, result)
        coVerify { preferencesManager.saveToken("google-jwt") }
    }

    @Test
    fun `loginWithGoogle calls onError on failure`() = runTest {
        coEvery { api.googleLogin(any()) } returns Response.error(
            401, "Unauthorized".toResponseBody()
        )

        var errorMsg: String? = null
        viewModel.loginWithGoogle(
            idToken = "bad-token",
            onSuccess = { fail("Should not succeed") },
            onError = { errorMsg = it }
        )

        assertNotNull(errorMsg)
    }
}