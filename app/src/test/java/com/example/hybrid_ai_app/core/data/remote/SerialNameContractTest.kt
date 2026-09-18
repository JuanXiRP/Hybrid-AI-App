package com.example.hybrid_ai_app.core.data.remote

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.auth.data.remote.LoginRequest
import com.example.hybrid_ai_app.auth.data.remote.RegisterRequest
import com.example.hybrid_ai_app.coach.data.ChatData
import com.example.hybrid_ai_app.coach.data.ChatMessageDto
import com.example.hybrid_ai_app.coach.data.ChatRequest
import com.example.hybrid_ai_app.coach.data.ChatResponse
import com.example.hybrid_ai_app.core.data.remote.dto.BillingErrorDto
import com.example.hybrid_ai_app.core.data.remote.dto.ChatQuotaDto
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementDto
import com.example.hybrid_ai_app.core.data.remote.dto.EntitlementResponse
import com.example.hybrid_ai_app.core.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.core.data.remote.dto.QuotaDto
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.data.remote.dto.UserProfileResponse
import com.example.hybrid_ai_app.core.data.remote.dto.VerifyPurchaseRequest
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutPlanDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutRunDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutStrengthDto
import com.example.hybrid_ai_app.onboarding.data.remote.dto.ProfileUpdateRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.hybrid_ai_app.home.data.remote.dto.ActivePlanResponseDto as HomeActivePlanResponseDto
import com.example.hybrid_ai_app.home.data.remote.dto.DayDto as HomeDayDto
import com.example.hybrid_ai_app.home.data.remote.dto.WeekDto as HomeWeekDto
import com.example.hybrid_ai_app.home.data.remote.dto.WorkoutPlanDto as HomeWorkoutPlanDto

/**
 * Pins the **wire name of every property on every DTO**, read straight off the generated
 * serializer descriptors.
 *
 * Why this exists rather than only round-tripping sample JSON: the contract with
 * `hybrid-ai-backend` is hand-mirrored, with no codegen and no shared schema. A Kotlin-side rename
 * of a property that has no `@SerialName` changes the wire name silently — the app still compiles,
 * the DTO still parses (`ignoreUnknownKeys = true` swallows the now-unrecognised server field),
 * and the value simply arrives as `null` or its default. Nothing fails; data just disappears.
 *
 * Roughly 35 properties across these DTOs are camelCase with **no** `@SerialName`, so they ride on
 * their Kotlin name. Those are listed here explicitly. If someone renames one, or adds a
 * `JsonNamingStrategy` to [NetworkJson], this test fails and names the field.
 *
 * The expected lists are also declaration-ordered, which is what kotlinx uses when it *writes*
 * JSON, so a reordering that changes request-body key order is visible here too.
 */
@OptIn(ExperimentalSerializationApi::class)
class SerialNameContractTest {

    private fun wireNames(serializer: KSerializer<*>): List<String> =
        serializer.descriptor.elementNamesInOrder()

    private fun SerialDescriptor.elementNamesInOrder(): List<String> =
        (0 until elementsCount).map { getElementName(it) }

    private fun assertWireNames(
        serializer: KSerializer<*>,
        vararg expected: String,
    ) {
        assertEquals(
            "wire contract changed for ${serializer.descriptor.serialName}",
            expected.toList(),
            wireNames(serializer),
        )
    }

    // ------------------------------------------------------------------------------------
    // The snake_case keys. These are the deliberate exceptions to an otherwise camelCase API,
    // and each one is load-bearing on the backend side.
    // ------------------------------------------------------------------------------------

    @Test
    fun `AuthResponse keeps has_completed_onboarding snake_case`() {
        // The backend sends this as a SIBLING of `data`, derived from whether a WorkoutPlan
        // exists — not from the user document's own camelCase `hasCompletedOnboarding`.
        // Arrange, Act & Assert
        assertWireNames(
            AuthResponse.serializer(),
            "success",
            "token",
            "message",
            "has_completed_onboarding",
        )
    }

    @Test
    fun `ProfileUpdateRequest keeps last_period_date snake_case`() {
        // The only snake_case field in the backend's User model, stored verbatim there
        // specifically to preserve this hand-mirrored contract.
        // Arrange, Act & Assert
        assertWireNames(
            ProfileUpdateRequest.serializer(),
            "age",
            "weight",
            "height",
            "sex",
            "goal",
            "fitnessLevel",
            "daysAvailable",
            "planDuration",
            "injuries",
            "last_period_date",
        )
    }

    @Test
    fun `ChatRequest keeps plan_context snake_case`() {
        // The backend destructures `plan_context` directly out of req.body; a rename here means
        // the coach silently loses all grounding in the user's plan and answers generically.
        // Arrange, Act & Assert
        assertWireNames(ChatRequest.serializer(), "message", "plan_context", "history")
    }

    @Test
    fun `EntitlementDto keeps the billing block's snake_case keys`() {
        // This block is the one place the backend has a real camelCase-to-snake_case translation
        // layer (billingController.toWire), written to match these @SerialNames.
        // Arrange, Act & Assert
        assertWireNames(
            EntitlementDto.serializer(),
            "status",
            "is_premium",
            "trial_ends_at",
            "trial_days_left",
            "plans",
            "chat",
        )
    }

    @Test
    fun `ChatQuotaDto keeps resets_at snake_case`() {
        // Arrange, Act & Assert
        assertWireNames(ChatQuotaDto.serializer(), "used", "limit", "resets_at")
    }

    @Test
    fun `the DTOs that read Mongo's _id keep the underscore`() {
        // Mongoose emits `_id`, never `id`. The one exception is the trimmed auth user object,
        // which the controller hand-builds with `id` — and which no DTO here models.
        // Arrange, Act & Assert
        assertEquals("_id", wireNames(UserDto.serializer()).first())
        assertEquals("_id", wireNames(WorkoutStrengthDto.serializer()).first())
        assertEquals("_id", wireNames(HomeWorkoutPlanDto.serializer()).first())
    }

    @Test
    fun `ChatResponse maps errorMessage onto the wire's message key`() {
        // The single deliberate Kotlin-name-to-wire-name divergence in the project: the property
        // is `errorMessage` for readability at the call site, but the backend sends `message`.
        // Arrange, Act & Assert
        assertWireNames(ChatResponse.serializer(), "success", "data", "message")
    }

    // ------------------------------------------------------------------------------------
    // The camelCase majority. No @SerialName, so the Kotlin name IS the contract.
    // ------------------------------------------------------------------------------------

    @Test
    fun `the plan DTOs that double as the Room column type keep their names`() {
        // These are simultaneously a wire contract AND an on-disk schema: WorkoutPlanEntity
        // stores List<WeekDto> as JSON through WorkoutPlanConverters. A rename therefore fails
        // to decode every existing row, and the converter catches the exception and returns
        // emptyList() — so the user's plan silently empties instead of crashing.
        // Arrange, Act & Assert
        assertWireNames(WorkoutPlanDto.serializer(), "durationWeeks", "goal", "weeks")
        assertWireNames(WeekDto.serializer(), "weekNumber", "days")
        assertWireNames(DayDto.serializer(), "dayName", "workoutType", "source", "exercises")
        assertWireNames(ExerciseDto.serializer(), "name", "sets", "reps", "rpe")
    }

    @Test
    fun `UserDto keeps its camelCase profile fields`() {
        // Arrange, Act & Assert
        assertWireNames(
            UserDto.serializer(),
            "_id",
            "name",
            "email",
            "age",
            "weight",
            "height",
            "sex",
            "goal",
            "fitnessLevel",
            "daysAvailable",
            "planDuration",
            "injuries",
            "isPremium",
        )
    }

    @Test
    fun `the plan generation and import request bodies keep their camelCase names`() {
        // Arrange, Act & Assert
        assertWireNames(GeneratePlanRequest.serializer(), "planDuration", "goal")
        assertWireNames(
            ImportPlanRequest.serializer(),
            "planDuration",
            "goal",
            "providedDomain",
            "sourceText",
            "attachments",
        )
        assertWireNames(PlanAttachmentDto.serializer(), "mimeType", "data")
        assertWireNames(GeneratePlanResponse.serializer(), "success", "data")
    }

    @Test
    fun `the workout sync payloads keep their camelCase names`() {
        // Arrange, Act & Assert
        assertWireNames(
            WorkoutStrengthDto.serializer(),
            "_id",
            "userId",
            "date",
            "routineType",
            "exercises",
        )
        assertWireNames(
            WorkoutRunDto.serializer(),
            "userId",
            "distance",
            "duration",
            "targetPace",
            "actualPace",
            "elevationGain",
            "rpe",
            "gpsPath",
        )
    }

    @Test
    fun `the billing request and error bodies keep their names`() {
        // Arrange, Act & Assert
        assertWireNames(VerifyPurchaseRequest.serializer(), "purchaseToken", "productId")
        assertWireNames(BillingErrorDto.serializer(), "success", "code", "message")
        assertWireNames(ApiErrorDto.serializer(), "success", "code", "message")
        assertWireNames(EntitlementResponse.serializer(), "success", "data")
    }

    @Test
    fun `the auth request bodies keep their names`() {
        // Arrange, Act & Assert
        assertWireNames(LoginRequest.serializer(), "email", "password")
        assertWireNames(
            RegisterRequest.serializer(),
            "name",
            "email",
            "password",
            "age",
            "weight",
            "height",
            "sex",
            "goal",
            "fitnessLevel",
            "daysAvailable",
            "planDuration",
        )
    }

    @Test
    fun `the chat payloads keep their names`() {
        // Arrange, Act & Assert
        assertWireNames(ChatMessageDto.serializer(), "role", "content")
        assertWireNames(ChatData.serializer(), "reply", "timestamp")
    }

    @Test
    fun `UserProfileResponse wraps the user under data`() {
        // Arrange, Act & Assert
        assertWireNames(UserProfileResponse.serializer(), "success", "data")
    }

    // ------------------------------------------------------------------------------------
    // The second, separate plan DTO family under home/
    // ------------------------------------------------------------------------------------

    @Test
    fun `the home plan DTOs are a distinct shape from the core ones`() {
        // Two WorkoutPlanDto types exist deliberately and are NOT interchangeable. The home
        // variant adds _id/startDate/active and, notably, its DayDto drops `workoutType` and
        // `source` — so a plan read through this path cannot tell a rest day from a training day
        // nor an imported plan from a generated one.
        // Arrange, Act & Assert
        assertWireNames(
            HomeWorkoutPlanDto.serializer(),
            "_id",
            "startDate",
            "active",
            "durationWeeks",
            "goal",
            "weeks",
        )
        assertWireNames(HomeWeekDto.serializer(), "weekNumber", "days")
        assertWireNames(HomeDayDto.serializer(), "dayName", "exercises")
        assertWireNames(HomeActivePlanResponseDto.serializer(), "success", "data")
    }

    @Test
    fun `the home DayDto really does lack the provenance fields the coach relies on`() {
        // Asserted as an explicit fact rather than left implicit: this is a known asymmetry, and
        // if someone adds the fields to close it, this test should fail and be updated rather
        // than the difference being rediscovered from a bug report.
        // Arrange
        val coreNames = wireNames(DayDto.serializer())
        val homeNames = wireNames(HomeDayDto.serializer())

        // Act
        val missingFromHome = coreNames - homeNames.toSet()

        // Assert
        assertEquals(listOf("workoutType", "source"), missingFromHome)
    }
}
