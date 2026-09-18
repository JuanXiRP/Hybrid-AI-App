package com.example.hybrid_ai_app.testing

import com.example.hybrid_ai_app.auth.data.remote.AuthResponse
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto
import com.example.hybrid_ai_app.core.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.core.data.remote.dto.UserDto
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus

/**
 * Builders for the shapes tests need, with sensible defaults and named overrides.
 *
 * The rule: a test states only the fields its assertion depends on. Everything else comes from the
 * default, so a new required field on a DTO breaks one line here instead of thirty across the
 * suite, and a reader can tell at a glance which value the test is actually about.
 *
 * Identifiers default to freshly generated values ([TestIds]) rather than shared literals.
 */

// ---------------------------------------------------------------------------------------------
// Entitlement (domain)
// ---------------------------------------------------------------------------------------------

/**
 * A free-trial entitlement with quota left: one plan allowed and unused, two chat messages with
 * one spent. The common "user can still act" baseline.
 */
fun trialEntitlement(
    status: EntitlementStatus = EntitlementStatus.TRIAL,
    trialDaysLeft: Int = 9,
    plansUsed: Int = 0,
    plansLimit: Int? = 1,
    chatUsed: Int = 1,
    chatLimit: Int? = 2,
    chatResetsAt: String? = "2026-09-19T00:00:00.000Z",
): Entitlement = Entitlement(
    status = status,
    trialDaysLeft = trialDaysLeft,
    plansUsed = plansUsed,
    plansLimit = plansLimit,
    chatUsed = chatUsed,
    chatLimit = chatLimit,
    chatResetsAt = chatResetsAt,
)

/** Premium: `null` limits mean unlimited, which is not the same as a very large limit. */
fun premiumEntitlement(
    plansUsed: Int = 3,
    chatUsed: Int = 12,
): Entitlement = trialEntitlement(
    status = EntitlementStatus.PREMIUM,
    trialDaysLeft = 0,
    plansUsed = plansUsed,
    plansLimit = null,
    chatUsed = chatUsed,
    chatLimit = null,
    chatResetsAt = null,
)

/** Trial ran out and nothing was bought: read-only, regardless of unspent quota. */
fun expiredEntitlement(): Entitlement = trialEntitlement(
    status = EntitlementStatus.EXPIRED,
    trialDaysLeft = 0,
    plansUsed = 0,
    chatUsed = 0,
)

// ---------------------------------------------------------------------------------------------
// Plan shapes (core DTOs — these double as the Room column type)
// ---------------------------------------------------------------------------------------------

fun exerciseDto(
    name: String = "Back Squat",
    sets: String = "4",
    reps: String = "6",
    rpe: String = "8",
): ExerciseDto = ExerciseDto(name = name, sets = sets, reps = reps, rpe = rpe)

fun dayDto(
    dayName: String = "Lower Body",
    workoutType: String = "strength",
    source: String = "generated",
    exercises: List<ExerciseDto> = listOf(exerciseDto()),
): DayDto = DayDto(
    dayName = dayName,
    workoutType = workoutType,
    source = source,
    exercises = exercises,
)

fun weekDto(
    weekNumber: Int = 1,
    days: List<DayDto> = listOf(dayDto()),
): WeekDto = WeekDto(weekNumber = weekNumber, days = days)

/**
 * A full week of seven days, so tests about day rollover and the seven-slot weekly-completion
 * array do not have to build it inline.
 */
fun fullWeekDto(weekNumber: Int = 1): WeekDto = weekDto(
    weekNumber = weekNumber,
    days = (0 until 7).map { index ->
        dayDto(
            dayName = "Day ${index + 1}",
            workoutType = if (index % 3 == 2) "rest" else "strength",
            exercises = if (index % 3 == 2) emptyList() else listOf(exerciseDto()),
        )
    },
)

// ---------------------------------------------------------------------------------------------
// Room entities
// ---------------------------------------------------------------------------------------------

fun workoutPlanEntity(
    id: String = "active_plan",
    durationWeeks: Int = 8,
    goal: String = "both",
    weeks: List<WeekDto> = listOf(weekDto()),
): WorkoutPlanEntity = WorkoutPlanEntity(
    id = id,
    durationWeeks = durationWeeks,
    goal = goal,
    weeks = weeks,
)

fun userProgressEntity(
    userId: String = "active_plan",
    currentWeekNumber: Int = 1,
    currentDayIndex: Int = 0,
): UserProgressEntity = UserProgressEntity(
    userId = userId,
    currentWeekNumber = currentWeekNumber,
    currentDayIndex = currentDayIndex,
)

fun loggedExerciseEntity(
    name: String = "Back Squat",
    sets: String = "4",
    reps: String = "6",
    weight: String = "100",
    rpe: String = "8",
): LoggedExerciseEntity = LoggedExerciseEntity(
    name = name,
    sets = sets,
    reps = reps,
    weight = weight,
    rpe = rpe,
)

fun workoutLogEntity(
    id: Long = 0,
    weekNumber: Int = 1,
    dayIndex: Int = 0,
    timestamp: Long = FIXED_TIMESTAMP,
    isCompleted: Boolean = true,
    loggedExercises: List<LoggedExerciseEntity> = listOf(loggedExerciseEntity()),
): WorkoutLogEntity = WorkoutLogEntity(
    id = id,
    weekNumber = weekNumber,
    dayIndex = dayIndex,
    timestamp = timestamp,
    isCompleted = isCompleted,
    loggedExercises = loggedExercises,
)

/**
 * 2026-09-18T10:00:00Z. A fixed instant, because `HistoryViewModel` formats timestamps for
 * display and a `System.currentTimeMillis()` default would make those assertions drift.
 */
const val FIXED_TIMESTAMP: Long = 1_789_725_600_000L

// ---------------------------------------------------------------------------------------------
// Wire DTOs
// ---------------------------------------------------------------------------------------------

fun userDto(
    id: String? = TestIds.uniqueObjectId(),
    name: String = TestIds.uniqueName(),
    email: String = TestIds.uniqueEmail(),
    age: Int? = 30,
    weight: Double? = 80.0,
    height: Double? = 180.0,
    sex: String? = "male",
    goal: String? = "both",
    fitnessLevel: String? = "intermediate",
    daysAvailable: Int? = 4,
    planDuration: Int? = 8,
    injuries: List<String> = emptyList(),
    isPremium: Boolean = false,
): UserDto = UserDto(
    id = id,
    name = name,
    email = email,
    age = age,
    weight = weight,
    height = height,
    sex = sex,
    goal = goal,
    fitnessLevel = fitnessLevel,
    daysAvailable = daysAvailable,
    planDuration = planDuration,
    injuries = injuries,
    isPremium = isPremium,
)

fun authResponse(
    success: Boolean = true,
    token: String? = TestIds.uniqueToken(),
    message: String? = null,
    hasCompletedOnboarding: Boolean = false,
): AuthResponse = AuthResponse(
    success = success,
    token = token,
    message = message,
    hasCompletedOnboarding = hasCompletedOnboarding,
)
