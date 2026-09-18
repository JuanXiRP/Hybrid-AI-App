package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.coach.data.ChatRequest
import com.example.hybrid_ai_app.coach.data.ChatResponse
import com.example.hybrid_ai_app.core.data.remote.dto.BillingErrorDto
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementResponse
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.data.remote.dto.UserProfileResponse
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import com.example.hybrid_ai_app.testing.BackendResponses
import com.example.hybrid_ai_app.testing.TestIds
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.hybrid_ai_app.home.data.remote.dto.ActivePlanResponseDto as HomeActivePlanResponseDto

/**
 * Decodes the JSON the backend really sends, using the **production** [NetworkJson] instance.
 *
 * [SerialNameContractTest] pins the field names structurally; this suite proves the values land in
 * the right properties when parsing bodies transcribed from the backend source, and pins the
 * behaviour of the `Json` configuration itself — the defaults are part of the contract just as
 * much as the names are.
 */
@OptIn(ExperimentalSerializationApi::class)
class WireContractTest {

    // ------------------------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------------------------

    @Test
    fun `the login response's snake_case onboarding flag reaches the camelCase property`() {
        // Arrange
        val token = TestIds.uniqueToken()
        val body = BackendResponses.loginSuccess(token, hasCompletedOnboarding = true)

        // Act
        val response = NetworkJson.decodeFromString<AuthResponse>(body)

        // Assert
        assertTrue(response.success)
        assertEquals(token, response.token)
        assertTrue(response.hasCompletedOnboarding)
    }

    @Test
    fun `a register response without the onboarding key defaults to false rather than failing`() {
        // The register endpoint omits has_completed_onboarding entirely — a new user cannot have
        // a plan yet. The DTO default is what keeps that from being a parse error.
        // Arrange
        val token = TestIds.uniqueToken()
        val body = BackendResponses.registerSuccess(token)

        // Act
        val response = NetworkJson.decodeFromString<AuthResponse>(body)

        // Assert
        assertEquals(token, response.token)
        assertFalse(response.hasCompletedOnboarding)
    }

    // ------------------------------------------------------------------------------------
    // User profile
    // ------------------------------------------------------------------------------------

    @Test
    fun `the full user document parses despite the many fields the DTO does not declare`() {
        // The backend sends the whole Mongoose document: subscription, trialEndsAt,
        // hasCompletedOnboarding, createdAt, updatedAt, __v — none of which UserDto declares.
        // Arrange
        val userId = TestIds.uniqueObjectId()
        val email = TestIds.uniqueEmail()
        val body = BackendResponses.userProfile(userId = userId, email = email)

        // Act
        val response = NetworkJson.decodeFromString<UserProfileResponse>(body)

        // Assert
        assertTrue(response.success)
        assertEquals(userId, response.data.id)
        assertEquals(email, response.data.email)
        assertEquals("intermediate", response.data.fitnessLevel)
        assertEquals(4, response.data.daysAvailable)
        assertEquals(8, response.data.planDuration)
    }

    @Test
    fun `last_period_date is written by the client but never read back`() {
        // Asserted as a deliberate fact: ProfileUpdateRequest SENDS last_period_date, and the
        // backend stores and returns it, but UserDto does not declare it — so the value cannot
        // round-trip into the app. Anything that needs to display it must add the field first.
        // Arrange
        val body = BackendResponses.userProfile(lastPeriodDate = "2026-07-01")

        // Act
        val response = NetworkJson.decodeFromString<UserProfileResponse>(body)
        // Inspect UserDto itself, not the wrapper: the wrapper only ever declares
        // success/data, so asking it would pass no matter what UserDto models.
        val userFields = UserDto.serializer().descriptor.let { descriptor ->
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        }

        // Assert
        assertTrue("the response itself parses fine", response.success)
        assertFalse(
            "UserDto still does not model last_period_date",
            userFields.contains("last_period_date"),
        )
    }

    // ------------------------------------------------------------------------------------
    // Entitlement — the snake_case billing block
    // ------------------------------------------------------------------------------------

    @Test
    fun `a trial entitlement maps every snake_case key onto its property`() {
        // Arrange
        val body = BackendResponses.entitlement(
            status = "trial",
            isPremium = false,
            trialEndsAt = "2026-10-02T12:00:00.000Z",
            trialDaysLeft = 14,
            plansUsed = 0,
            plansLimit = 1,
            chatUsed = 1,
            chatLimit = 2,
            chatResetsAt = "2026-09-19T00:00:00.000Z",
        )

        // Act
        val dto = NetworkJson.decodeFromString<EntitlementResponse>(body).data

        // Assert
        assertEquals("trial", dto.status)
        assertFalse(dto.isPremium)
        assertEquals("2026-10-02T12:00:00.000Z", dto.trialEndsAt)
        assertEquals(14, dto.trialDaysLeft)
        assertEquals(0, dto.plans.used)
        assertEquals(1, dto.plans.limit)
        assertEquals(1, dto.chat.used)
        assertEquals(2, dto.chat.limit)
        assertEquals("2026-09-19T00:00:00.000Z", dto.chat.resetsAt)
    }

    @Test
    fun `premium sends null limits and they stay null rather than becoming zero`() {
        // `null` means unlimited. Coercing it to 0 would read as "no quota left" and lock a
        // paying user out of the feature they just paid for — the exact opposite of the intent.
        // Arrange
        val body = BackendResponses.premiumEntitlementBody()

        // Act
        val dto = NetworkJson.decodeFromString<EntitlementResponse>(body).data

        // Assert
        assertEquals("premium", dto.status)
        assertTrue(dto.isPremium)
        assertNull("plans.limit must stay null for premium", dto.plans.limit)
        assertNull("chat.limit must stay null for premium", dto.chat.limit)
    }

    // ------------------------------------------------------------------------------------
    // Plans
    // ------------------------------------------------------------------------------------

    @Test
    fun `the generated plan parses through the per-subdocument _id fields Mongoose adds`() {
        // Every week, day and exercise carries its own _id, because no Mongoose subschema sets
        // `_id: false`. None of the client DTOs declare them.
        // Arrange
        val body = BackendResponses.workoutPlan(durationWeeks = 8, goal = "both")

        // Act
        val plan = NetworkJson.decodeFromString<GeneratePlanResponse>(body).data!!

        // Assert
        assertEquals(8, plan.durationWeeks)
        assertEquals("both", plan.goal)
        assertEquals(1, plan.weeks.size)
        assertEquals(1, plan.weeks.first().weekNumber)
        val day = plan.weeks.first().days.first()
        assertEquals("Lower Body", day.dayName)
        assertEquals("strength", day.workoutType)
        assertEquals("generated", day.source)
    }

    @Test
    fun `sets reps and rpe arrive as strings not numbers`() {
        // The Gemini responseSchema declares all three as STRING, and the import path writes "-"
        // when the source document states no value. Typing them as Int on the client would fail
        // to parse every imported plan.
        // Arrange
        val body = BackendResponses.workoutPlan()

        // Act
        val exercise = NetworkJson.decodeFromString<GeneratePlanResponse>(body)
            .data!!.weeks.first().days.first().exercises.first()

        // Assert
        assertEquals("4", exercise.sets)
        assertEquals("6", exercise.reps)
        assertEquals("8", exercise.rpe)
    }

    @Test
    fun `an imported plan's provenance survives the wire`() {
        // Arrange
        val body = BackendResponses.workoutPlan(origin = "imported", source = "imported")

        // Act
        val plan = NetworkJson.decodeFromString<GeneratePlanResponse>(body).data!!

        // Assert
        assertEquals("imported", plan.weeks.first().days.first().source)
    }

    @Test
    fun `the same plan body also parses through the separate home DTO family`() {
        // Arrange
        val planId = TestIds.uniqueObjectId()
        val body = BackendResponses.workoutPlan(planId = planId)

        // Act
        val response = NetworkJson.decodeFromString<HomeActivePlanResponseDto>(body)

        // Assert
        assertTrue(response.success)
        assertEquals(planId, response.data!!.id)
        assertEquals("2026-09-18T10:00:00.000Z", response.data!!.startDate)
        assertTrue(response.data!!.active)
    }

    @Test
    fun `the home plan DTO fails loudly when durationWeeks is missing`() {
        // Unlike the core DTO, the home one gives durationWeeks no default, so an incomplete
        // response throws instead of silently reading as a zero-week plan. Pinned because the
        // difference between the two families is easy to "tidy up" by accident.
        // Arrange
        val body = BackendResponses.envelope(
            """{"_id":"abc","startDate":"","active":true,"goal":"both","weeks":[]}""",
        )

        // Act & Assert
        val failure = runCatching {
            NetworkJson.decodeFromString<HomeActivePlanResponseDto>(body)
        }.exceptionOrNull()
        assertTrue(
            "expected a SerializationException, got ${failure?.javaClass?.name}",
            failure is SerializationException,
        )
    }

    // ------------------------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------------------------

    @Test
    fun `the chat response exposes the reply and maps the error key onto errorMessage`() {
        // Arrange
        val successBody = BackendResponses.chatReply(reply = "Hold RPE 8 today.")
        val errorBody = BackendResponses.error("Message is required")

        // Act
        val success = NetworkJson.decodeFromString<ChatResponse>(successBody)
        val failure = NetworkJson.decodeFromString<ChatResponse>(errorBody)

        // Assert
        assertEquals("Hold RPE 8 today.", success.data!!.reply)
        assertNull(success.errorMessage)
        assertNull("no data block on the failure body", failure.data)
        assertEquals("Message is required", failure.errorMessage)
    }

    // ------------------------------------------------------------------------------------
    // The 402 family
    // ------------------------------------------------------------------------------------

    @Test
    fun `each 402 code decodes while its unmodelled data block is ignored`() {
        // Arrange
        val cases = mapOf(
            "PLAN_LIMIT_REACHED" to BackendResponses.planLimitReached(),
            "CHAT_QUOTA_EXCEEDED" to BackendResponses.chatQuotaExceeded(),
            "TRIAL_EXPIRED" to BackendResponses.trialExpired(),
        )

        // Act & Assert
        cases.forEach { (expectedCode, body) ->
            val dto = NetworkJson.decodeFromString<BillingErrorDto>(body)
            assertFalse(dto.success)
            assertEquals(expectedCode, dto.code)
            assertTrue("every 402 carries a message", !dto.message.isNullOrBlank())
        }
    }

    @Test
    fun `a generic error body has no code at all rather than a null one`() {
        // The backend only sends `code` from the billing controller and the 402 guards; every
        // other failure is message-only. The DTO default is what covers the absence.
        // Arrange
        val body = BackendResponses.unauthorized()

        // Act
        val dto = NetworkJson.decodeFromString<BillingErrorDto>(body)

        // Assert
        assertFalse(body.contains("\"code\""))
        assertNull(dto.code)
        assertEquals("Not authorized, no token", dto.message)
    }

    // ------------------------------------------------------------------------------------
    // The Json configuration itself
    // ------------------------------------------------------------------------------------

    @Test
    fun `a field the client has never seen is ignored instead of breaking the response`() {
        // This is what lets the backend ship a new field without a coordinated client release.
        // Arrange — a plausible future addition alongside the keys we do know
        val body = BackendResponses.envelope(
            """{"status":"trial","is_premium":false,""" +
                """"trial_ends_at":"2026-10-02T12:00:00.000Z","trial_days_left":14,""" +
                """"plans":{"used":0,"limit":1,"grace_period_days":3},""" +
                """"chat":{"used":1,"limit":2,"resets_at":"2026-09-19T00:00:00.000Z"},""" +
                """"referral_credits":5,""" +
                """"experiment_bucket":{"name":"paywall_v3","variant":"b"}}""",
        )

        // Act
        val dto = NetworkJson.decodeFromString<EntitlementResponse>(body).data

        // Assert
        assertEquals("trial", dto.status)
        assertEquals(1, dto.plans.limit)
        assertEquals(2, dto.chat.limit)
    }

    @Test
    fun `a defaulted property is omitted from the request body entirely`() {
        // `encodeDefaults = false` is how ProfileUpdateRequest keeps last_period_date out of the
        // payload for non-female users, rather than sending an explicit null the backend would
        // have to special-case.
        // Arrange
        val request = ProfileUpdateRequest(
            age = 30,
            weight = 80.0,
            height = 180.0,
            sex = "male",
            goal = "both",
            fitnessLevel = "intermediate",
            daysAvailable = 4,
            planDuration = 8,
            injuries = emptyList(),
            lastPeriodDate = null,
        )

        // Act
        val json = NetworkJson.encodeToString(request)

        // Assert
        assertFalse(
            "a null lastPeriodDate must not appear on the wire: $json",
            json.contains("last_period_date"),
        )
    }

    @Test
    fun `a non-default value is sent under its snake_case name`() {
        // Arrange
        val request = ProfileUpdateRequest(
            age = 30,
            weight = 65.0,
            height = 170.0,
            sex = "female",
            goal = "endurance",
            fitnessLevel = "beginner",
            daysAvailable = 3,
            planDuration = 8,
            injuries = listOf("left knee"),
            lastPeriodDate = "2026-09-01",
        )

        // Act
        val json = NetworkJson.encodeToString(request)

        // Assert
        assertTrue(json.contains("\"last_period_date\":\"2026-09-01\""))
        assertTrue(json.contains("\"fitnessLevel\":\"beginner\""))
        assertTrue(json.contains("\"daysAvailable\":3"))
    }

    @Test
    fun `an omitted optional chat field is left out of the request`() {
        // Arrange
        val request = ChatRequest(message = "How heavy should I squat?")

        // Act
        val json = NetworkJson.encodeToString(request)

        // Assert
        assertEquals("""{"message":"How heavy should I squat?"}""", json)
    }

    @Test
    fun `an explicit null for a non-nullable field throws instead of using the default`() {
        // `coerceInputValues` is left at false. A server bug that sends `"status": null` should
        // surface as a parse failure the repository turns into an error, not silently become
        // "expired" and put the user into read-only mode.
        // Arrange
        val body = BackendResponses.envelope(
            """{"status":null,"is_premium":false,"trial_days_left":0,""" +
                """"plans":{"used":0,"limit":1},"chat":{"used":0,"limit":2}}""",
        )

        // Act
        val failure = runCatching {
            NetworkJson.decodeFromString<EntitlementResponse>(body)
        }.exceptionOrNull()

        // Assert
        assertTrue(
            "expected a SerializationException, got ${failure?.javaClass?.name}",
            failure is SerializationException,
        )
    }

    @Test
    fun `the production Json instance is the one under test here`() {
        // Guards against the subtle failure where tests build their own permissive Json, pass,
        // and leave production parsing with different settings. If NetworkJson is ever
        // reconfigured, the behavioural tests above move with it.
        // Arrange
        val strict = Json { ignoreUnknownKeys = false }
        val bodyWithUnknownField = BackendResponses.envelope(
            """{"status":"trial","is_premium":false,"trial_days_left":1,""" +
                """"plans":{"used":0,"limit":1},"chat":{"used":0,"limit":2},""" +
                """"brand_new_field":true}""",
        )

        // Act
        val productionResult = runCatching {
            NetworkJson.decodeFromString<EntitlementResponse>(bodyWithUnknownField)
        }
        val strictResult = runCatching {
            strict.decodeFromString<EntitlementResponse>(bodyWithUnknownField)
        }

        // Assert
        assertTrue("production must tolerate unknown keys", productionResult.isSuccess)
        assertTrue("a strict instance would not", strictResult.isFailure)
    }
}
