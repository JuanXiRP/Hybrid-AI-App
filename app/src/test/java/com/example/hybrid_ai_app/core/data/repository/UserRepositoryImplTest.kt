package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.PlanAttachmentDto
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus
import com.example.hybrid_ai_app.core.domain.model.PlanImportRejectedException
import com.example.hybrid_ai_app.core.domain.model.PlanNotRecognizedException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredException
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.MockCleanupRule
import com.example.hybrid_ai_app.testing.MockWebServerRule
import com.example.hybrid_ai_app.testing.TestIds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection

/**
 * The main repository, driven through a real Retrofit against MockWebServer with a mocked Room DAO.
 *
 * A mocked `UserApi` would prove nothing about the eleven endpoint paths, the request bodies or
 * the error envelopes. This suite covers, per method: the success path, an HTTP error carrying the
 * backend's real body, a 401, and a dropped connection — plus the typed 402/422/400 translations,
 * which are the ones the UI branches on.
 */
class UserRepositoryImplTest {

    @get:Rule
    val server = MockWebServerRule()

    @get:Rule
    val mockCleanup = MockCleanupRule()

    private lateinit var dao: WorkoutPlanDao
    private lateinit var repository: UserRepositoryImpl

    @Before
    fun setUp() {
        // Re-stubbed per test: MockCleanupRule wipes MockK answers as well as recorded calls.
        dao = mockk(relaxed = true)
        repository = UserRepositoryImpl(server.api(UserApi::class.java), dao)
    }

    private fun profilePayload(lastPeriodDate: String? = null) = ProfileUpdateRequest(
        age = 30,
        weight = 80.0,
        height = 180.0,
        sex = if (lastPeriodDate == null) "male" else "female",
        goal = "both",
        fitnessLevel = "intermediate",
        daysAvailable = 4,
        planDuration = 8,
        injuries = emptyList(),
        lastPeriodDate = lastPeriodDate,
    )

    // ------------------------------------------------------------------------------------
    // updateProfile
    // ------------------------------------------------------------------------------------

    @Test
    fun `updateProfile PATCHes the profile endpoint`() {
        runTest {
            // Arrange
            server.enqueueEmpty(HttpURLConnection.HTTP_OK)

            // Act
            val result = repository.updateProfile(profilePayload())
            val request = server.takeRequest()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("PATCH", request.method)
            assertEquals(BackendResponses.Routes.USER_PROFILE, request.path)
        }
    }

    @Test
    fun `updateProfile omits last_period_date for a user who has none`() {
        runTest {
            // Arrange
            server.enqueueEmpty(HttpURLConnection.HTTP_OK)

            // Act
            repository.updateProfile(profilePayload(lastPeriodDate = null))
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertFalse("no null should be sent: $body", body.contains("last_period_date"))
        }
    }

    @Test
    fun `updateProfile sends last_period_date under its snake_case name when present`() {
        runTest {
            // Arrange
            server.enqueueEmpty(HttpURLConnection.HTTP_OK)

            // Act
            repository.updateProfile(profilePayload(lastPeriodDate = "2026-09-01"))
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertTrue(body.contains("\"last_period_date\":\"2026-09-01\""))
        }
    }

    @Test
    fun `updateProfile reports the status code on a validation failure`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.error("Validation failed: last_period_date cannot be in the future."),
                code = HttpURLConnection.HTTP_BAD_REQUEST,
            )

            // Act
            val result = repository.updateProfile(profilePayload())

            // Assert
            assertEquals("Backend error: 400", result.exceptionOrNull()!!.message)
        }
    }

    @Test
    fun `updateProfile reports a 401 rather than throwing`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.unauthorized(),
                code = HttpURLConnection.HTTP_UNAUTHORIZED,
            )

            // Act
            val result = repository.updateProfile(profilePayload())

            // Assert
            assertEquals("Backend error: 401", result.exceptionOrNull()!!.message)
        }
    }

    @Test
    fun `updateProfile turns a dropped connection into a failed Result`() {
        runTest {
            // Arrange
            server.enqueueConnectionFailure()

            // Act
            val result = repository.updateProfile(profilePayload())

            // Assert
            assertTrue(result.exceptionOrNull() is IOException)
        }
    }

    // ------------------------------------------------------------------------------------
    // generateAiPlan
    // ------------------------------------------------------------------------------------

    @Test
    fun `generateAiPlan caches the returned plan into Room`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.workoutPlan(durationWeeks = 12, goal = "endurance"),
                code = HttpURLConnection.HTTP_CREATED,
            )
            val saved = slot<WorkoutPlanEntity>()
            coEvery { dao.insertPlan(capture(saved)) } returns Unit

            // Act
            val result = repository.generateAiPlan(planDuration = 12, goal = "endurance")

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { dao.insertPlan(any()) }
            assertEquals(12, saved.captured.durationWeeks)
            assertEquals("endurance", saved.captured.goal)
            assertEquals("Lower Body", saved.captured.weeks.single().days.single().dayName)
        }
    }

    @Test
    fun `the cached plan always lands on the single active_plan primary key`() {
        runTest {
            // WorkoutPlanEntity defaults its id to "active_plan" and the repository never sets it,
            // so the table holds exactly one plan by construction — generating a new one replaces
            // the old rather than accumulating.
            // Arrange
            server.enqueueJson(
                BackendResponses.workoutPlan(),
                code = HttpURLConnection.HTTP_CREATED,
            )
            val saved = slot<WorkoutPlanEntity>()
            coEvery { dao.insertPlan(capture(saved)) } returns Unit

            // Act
            repository.generateAiPlan(planDuration = 8, goal = "both")

            // Assert
            assertEquals("active_plan", saved.captured.id)
        }
    }

    @Test
    fun `generateAiPlan posts the duration and goal to the generate endpoint`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.workoutPlan(),
                code = HttpURLConnection.HTTP_CREATED,
            )

            // Act
            repository.generateAiPlan(planDuration = 4, goal = "strength")
            val request = server.takeRequest()

            // Assert
            assertEquals("POST", request.method)
            assertEquals(BackendResponses.Routes.GENERATE_PLAN, request.path)
            assertEquals(
                """{"planDuration":4,"goal":"strength"}""",
                request.body.readUtf8(),
            )
        }
    }

    @Test
    fun `a 402 on generate becomes a typed paywall failure and caches nothing`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.planLimitReached(),
                code = HttpURLConnection.HTTP_PAYMENT_REQUIRED,
            )

            // Act
            val result = repository.generateAiPlan(planDuration = 8, goal = "both")

            // Assert
            val error = result.exceptionOrNull()
            assertTrue(error is PremiumRequiredException)
            assertEquals(
                PremiumRequiredReason.PLAN_LIMIT_REACHED,
                (error as PremiumRequiredException).reason,
            )
            coVerify(exactly = 0) { dao.insertPlan(any()) }
        }
    }

    @Test
    fun `a Gemini rate-limit 503 falls back to the status-code message`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.error("Error communicating with Coach AI"),
                code = HttpURLConnection.HTTP_UNAVAILABLE,
            )

            // Act
            val result = repository.generateAiPlan(planDuration = 8, goal = "both")

            // Assert
            assertEquals("Error generating plan: HTTP 503", result.exceptionOrNull()!!.message)
            coVerify(exactly = 0) { dao.insertPlan(any()) }
        }
    }

    @Test
    fun `a 200 with no data block caches nothing and fails`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.successWithNoData())

            // Act
            val result = repository.generateAiPlan(planDuration = 8, goal = "both")

            // Assert
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { dao.insertPlan(any()) }
        }
    }

    @Test
    fun `generateAiPlan turns a dropped connection into a failed Result`() {
        runTest {
            // Arrange
            server.enqueueConnectionFailure()

            // Act
            val result = repository.generateAiPlan(planDuration = 8, goal = "both")

            // Assert
            assertTrue(result.exceptionOrNull() is IOException)
            coVerify(exactly = 0) { dao.insertPlan(any()) }
        }
    }

    // ------------------------------------------------------------------------------------
    // importAiPlan
    // ------------------------------------------------------------------------------------

    @Test
    fun `importAiPlan posts the provided domain and source text`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.workoutPlan(origin = "imported", source = "imported"),
                code = HttpURLConnection.HTTP_CREATED,
            )

            // Act
            repository.importAiPlan(
                planDuration = 8,
                goal = "both",
                providedDomain = "strength",
                sourceText = "Mon: Squat 4x6",
                attachments = emptyList(),
            )
            val request = server.takeRequest()

            // Assert
            assertEquals(BackendResponses.Routes.IMPORT_PLAN, request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"providedDomain\":\"strength\""))
            assertTrue(body.contains("\"sourceText\":\"Mon: Squat 4x6\""))
        }
    }

    @Test
    fun `a blank source text is dropped so the backend sees only the attachments`() {
        runTest {
            // The backend answers 400 unless at least one of sourceText/attachments has content;
            // sending an empty string would read as content that is not there.
            // Arrange
            server.enqueueJson(
                BackendResponses.workoutPlan(),
                code = HttpURLConnection.HTTP_CREATED,
            )

            // Act
            repository.importAiPlan(
                planDuration = 8,
                goal = "both",
                providedDomain = "cardio",
                sourceText = "   ",
                attachments = listOf(PlanAttachmentDto(mimeType = "application/pdf", data = "QQ==")),
            )
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertFalse("blank sourceText must not be sent: $body", body.contains("sourceText"))
            assertTrue(body.contains("\"mimeType\":\"application/pdf\""))
        }
    }

    @Test
    fun `an imported plan is cached with its provenance intact`() {
        runTest {
            // source = "imported" is read only by PlanContextFormatter, so if it were lost here
            // the coach would treat a plan the user brought in as one it wrote itself.
            // Arrange
            server.enqueueJson(
                BackendResponses.workoutPlan(origin = "imported", source = "imported"),
                code = HttpURLConnection.HTTP_CREATED,
            )
            val saved = slot<WorkoutPlanEntity>()
            coEvery { dao.insertPlan(capture(saved)) } returns Unit

            // Act
            val result = repository.importAiPlan(
                planDuration = 8,
                goal = "both",
                providedDomain = "strength",
                sourceText = "Mon: Squat",
                attachments = emptyList(),
            )

            // Assert
            assertTrue(result.isSuccess)
            assertEquals("imported", saved.captured.weeks.single().days.single().source)
        }
    }

    @Test
    fun `a 422 means no plan was readable and is surfaced as its own type`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.planNotRecognized(),
                code = 422,
            )

            // Act
            val result = repository.importAiPlan(
                planDuration = 8,
                goal = "both",
                providedDomain = "strength",
                sourceText = "grocery list",
                attachments = emptyList(),
            )

            // Assert
            assertTrue(result.exceptionOrNull() is PlanNotRecognizedException)
            coVerify(exactly = 0) { dao.insertPlan(any()) }
        }
    }

    @Test
    fun `a 400 on import surfaces the server's specific rejection message`() {
        runTest {
            // Arrange
            val message = "Attachments must total at most 8 MB"
            server.enqueueJson(
                BackendResponses.importRejected(message),
                code = HttpURLConnection.HTTP_BAD_REQUEST,
            )

            // Act
            val result = repository.importAiPlan(
                planDuration = 8,
                goal = "both",
                providedDomain = "strength",
                sourceText = null,
                attachments = emptyList(),
            )

            // Assert
            val error = result.exceptionOrNull()
            assertTrue(error is PlanImportRejectedException)
            assertEquals(message, error!!.message)
        }
    }

    @Test
    fun `a 402 on import still reaches the paywall rather than the import error path`() {
        runTest {
            // planImportErrorOrNull returns null for 402 without reading the body, which is what
            // leaves the stream intact for premiumRequiredOrNull to read. If both read it, the
            // second would see an empty string and the reason would degrade to UNKNOWN.
            // Arrange
            server.enqueueJson(
                BackendResponses.trialExpired(),
                code = HttpURLConnection.HTTP_PAYMENT_REQUIRED,
            )

            // Act
            val result = repository.importAiPlan(
                planDuration = 8,
                goal = "both",
                providedDomain = "strength",
                sourceText = "Mon: Squat",
                attachments = emptyList(),
            )

            // Assert
            val error = result.exceptionOrNull()
            assertTrue(error is PremiumRequiredException)
            assertEquals(
                PremiumRequiredReason.TRIAL_EXPIRED,
                (error as PremiumRequiredException).reason,
            )
        }
    }

    // ------------------------------------------------------------------------------------
    // getUserProfile
    // ------------------------------------------------------------------------------------

    @Test
    fun `getUserProfile unwraps the user from the data envelope`() {
        runTest {
            // Arrange
            val userId = TestIds.uniqueObjectId()
            val email = TestIds.uniqueEmail()
            server.enqueueJson(BackendResponses.userProfile(userId = userId, email = email))

            // Act
            val result = repository.getUserProfile()
            val request = server.takeRequest()

            // Assert
            assertEquals("GET", request.method)
            assertEquals(BackendResponses.Routes.USER_PROFILE, request.path)
            assertEquals(userId, result.getOrNull()!!.id)
            assertEquals(email, result.getOrNull()!!.email)
        }
    }

    @Test
    fun `getUserProfile reports a 401 with the status code`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.unauthorized(),
                code = HttpURLConnection.HTTP_UNAUTHORIZED,
            )

            // Act
            val result = repository.getUserProfile()

            // Assert
            assertEquals("Error fetching profile: HTTP 401", result.exceptionOrNull()!!.message)
        }
    }

    @Test
    fun `getUserProfile turns a dropped connection into a failed Result`() {
        runTest {
            // Arrange
            server.enqueueConnectionFailure()

            // Act
            val result = repository.getUserProfile()

            // Assert
            assertTrue(result.exceptionOrNull() is IOException)
        }
    }

    // ------------------------------------------------------------------------------------
    // getEntitlement
    // ------------------------------------------------------------------------------------

    @Test
    fun `getEntitlement maps the snake_case billing block into the domain model`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.entitlement(
                    status = "trial",
                    trialDaysLeft = 9,
                    plansUsed = 1,
                    plansLimit = 1,
                    chatUsed = 1,
                    chatLimit = 2,
                ),
            )

            // Act
            val result = repository.getEntitlement()
            val request = server.takeRequest()

            // Assert
            assertEquals(BackendResponses.Routes.BILLING_ENTITLEMENT, request.path)
            val entitlement = result.getOrNull()!!
            assertEquals(EntitlementStatus.TRIAL, entitlement.status)
            assertEquals(9, entitlement.trialDaysLeft)
            assertFalse("the one plan is spent", entitlement.canGeneratePlan)
            assertTrue("a chat message is left", entitlement.canSendChatMessage)
            assertEquals(1, entitlement.chatMessagesLeft)
        }
    }

    @Test
    fun `a premium entitlement arrives with unlimited quotas`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.premiumEntitlementBody())

            // Act
            val entitlement = repository.getEntitlement().getOrNull()!!

            // Assert
            assertTrue(entitlement.isPremium)
            assertNull(entitlement.plansLimit)
            assertTrue(entitlement.canGeneratePlan)
            assertNull(entitlement.chatMessagesLeft)
        }
    }

    @Test
    fun `getEntitlement reports the status code on failure`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.unauthorized(),
                code = HttpURLConnection.HTTP_UNAUTHORIZED,
            )

            // Act
            val result = repository.getEntitlement()

            // Assert
            assertEquals("Error fetching entitlement: HTTP 401", result.exceptionOrNull()!!.message)
        }
    }

    @Test
    fun `getEntitlement turns a dropped connection into a failed Result`() {
        runTest {
            // Arrange
            server.enqueueConnectionFailure()

            // Act
            val result = repository.getEntitlement()

            // Assert
            assertTrue(result.exceptionOrNull() is IOException)
        }
    }

    // ------------------------------------------------------------------------------------
    // verifyPurchase — the money path, so every status gets its own copy
    // ------------------------------------------------------------------------------------

    @Test
    fun `verifyPurchase posts the token and returns the granted entitlement`() {
        runTest {
            // Arrange
            val purchaseToken = TestIds.uniquePurchaseToken()
            server.enqueueJson(BackendResponses.premiumEntitlementBody())

            // Act
            val result = repository.verifyPurchase(purchaseToken, "hybrid_ai_pro_monthly")
            val request = server.takeRequest()

            // Assert
            assertEquals("POST", request.method)
            assertEquals(BackendResponses.Routes.BILLING_VERIFY, request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"purchaseToken\":\"$purchaseToken\""))
            assertTrue(body.contains("\"productId\":\"hybrid_ai_pro_monthly\""))
            assertTrue(result.getOrNull()!!.isPremium)
        }
    }

    @Test
    fun `a missing productId is omitted from the verify body`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.premiumEntitlementBody())

            // Act
            repository.verifyPurchase(TestIds.uniquePurchaseToken(), productId = null)
            val body = server.takeRequest().body.readUtf8()

            // Assert
            assertFalse(body.contains("productId"))
        }
    }

    @Test
    fun `a 409 explains that the subscription belongs to another account`() {
        runTest {
            // The one failure a user can actually act on — they signed in with the wrong account.
            // Arrange
            server.enqueueJson(BackendResponses.tokenAlreadyClaimed(), code = 409)

            // Act
            val result = repository.verifyPurchase(TestIds.uniquePurchaseToken(), null)

            // Assert
            assertEquals(
                "This subscription is already linked to another account.",
                result.exceptionOrNull()!!.message,
            )
        }
    }

    @Test
    fun `a 400 explains that Play reports the subscription inactive`() {
        runTest {
            // Arrange
            server.enqueueJson(
                BackendResponses.error("Subscription is not active"),
                code = HttpURLConnection.HTTP_BAD_REQUEST,
            )

            // Act
            val result = repository.verifyPurchase(TestIds.uniquePurchaseToken(), null)

            // Assert
            assertEquals(
                "Google Play reports this subscription is not active.",
                result.exceptionOrNull()!!.message,
            )
        }
    }

    @Test
    fun `a 503 asks the user to retry rather than implying they were not charged`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.error("Billing disabled"), code = 503)

            // Act
            val result = repository.verifyPurchase(TestIds.uniquePurchaseToken(), null)

            // Assert
            assertEquals(
                "Purchases are temporarily unavailable. Please try again later.",
                result.exceptionOrNull()!!.message,
            )
        }
    }

    @Test
    fun `any other status falls back to a message naming the code`() {
        runTest {
            // Arrange
            server.enqueueJson(BackendResponses.error("Play verification failed"), code = 502)

            // Act
            val result = repository.verifyPurchase(TestIds.uniquePurchaseToken(), null)

            // Assert
            assertEquals(
                "Could not verify the purchase (HTTP 502).",
                result.exceptionOrNull()!!.message,
            )
        }
    }

    @Test
    fun `verifyPurchase turns a dropped connection into a failed Result`() {
        runTest {
            // Arrange
            server.enqueueConnectionFailure()

            // Act
            val result = repository.verifyPurchase(TestIds.uniquePurchaseToken(), null)

            // Assert
            assertTrue(result.exceptionOrNull() is IOException)
        }
    }
}
