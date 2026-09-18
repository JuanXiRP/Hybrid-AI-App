package com.example.hybrid_ai_app.coach.data

import com.example.hybrid_ai_app.testing.dayDto
import com.example.hybrid_ai_app.testing.exerciseDto
import com.example.hybrid_ai_app.testing.weekDto
import com.example.hybrid_ai_app.testing.workoutPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt context sent to the coach LLM.
 *
 * This is the only consumer of the core `DayDto.workoutType` and `.source` fields, so these tests
 * are what stops a DTO change from silently emptying the coach's grounding.
 */
class PlanContextFormatterTest {

    @Test
    fun `no plan produces no context so the caller can omit the field`() {
        // Arrange
        val plan = null

        // Act
        val context = PlanContextFormatter.format(plan)

        // Assert
        assertNull(context)
    }

    @Test
    fun `the header carries the goal and duration the coach needs to ground on`() {
        // Arrange
        val plan = workoutPlanEntity(goal = "endurance", durationWeeks = 12)

        // Act
        val context = PlanContextFormatter.format(plan)

        // Assert
        assertTrue(
            "expected the goal and duration in the header, got: $context",
            context!!.startsWith(
                "User's active training plan — Goal: endurance, Duration: 12 weeks.",
            ),
        )
    }

    @Test
    fun `a training day lists each exercise with its sets reps and RPE`() {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(
                    days = listOf(
                        dayDto(
                            dayName = "Lower Body",
                            workoutType = "strength",
                            source = "generated",
                            exercises = listOf(
                                exerciseDto(name = "Back Squat", sets = "4", reps = "6", rpe = "8"),
                                exerciseDto(name = "Leg Press", sets = "3", reps = "10", rpe = "7"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        // Act
        val context = PlanContextFormatter.format(plan)

        // Assert
        assertTrue(
            "expected both exercises joined with '; ', got: $context",
            context!!.contains(
                "Day 1 - Lower Body [strength/generated]: " +
                    "Back Squat (4x6, RPE 8); Leg Press (3x10, RPE 7)",
            ),
        )
    }

    @Test
    fun `a day with no exercises is described as rest rather than an empty list`() {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(
                    days = listOf(
                        dayDto(dayName = "Recovery", workoutType = "rest", exercises = emptyList()),
                    ),
                ),
            ),
        )

        // Act
        val context = PlanContextFormatter.format(plan)

        // Assert
        assertTrue(
            "expected the rest marker, got: $context",
            context!!.contains("Day 1 - Recovery [rest/generated]: rest"),
        )
    }

    @Test
    fun `provenance is preserved so the coach does not restructure a plan the user brought in`() {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(days = listOf(dayDto(dayName = "Push", source = "imported"))),
            ),
        )

        // Act
        val context = PlanContextFormatter.format(plan)

        // Assert
        assertTrue(
            "expected source 'imported' in the day label, got: $context",
            context!!.contains("[strength/imported]"),
        )
    }

    @Test
    fun `days are numbered from one within each week and every week is labelled`() {
        // Arrange
        val plan = workoutPlanEntity(
            weeks = listOf(
                weekDto(
                    weekNumber = 1,
                    days = listOf(dayDto(dayName = "A"), dayDto(dayName = "B")),
                ),
                weekDto(weekNumber = 2, days = listOf(dayDto(dayName = "C"))),
            ),
        )

        // Act
        val context = PlanContextFormatter.format(plan)!!

        // Assert
        assertTrue("missing week 1 label", context.contains("Week 1:"))
        assertTrue("missing week 2 label", context.contains("Week 2:"))
        assertTrue("day numbering should restart per week", context.contains("Day 1 - A"))
        assertTrue(context.contains("Day 2 - B"))
        assertTrue("week 2's only day should be Day 1", context.contains("Day 1 - C"))
    }

    @Test
    fun `a plan with no weeks yields just the trimmed header`() {
        // Arrange
        val plan = workoutPlanEntity(goal = "strength", durationWeeks = 4, weeks = emptyList())

        // Act
        val context = PlanContextFormatter.format(plan)

        // Assert
        assertEquals(
            "User's active training plan — Goal: strength, Duration: 4 weeks.",
            context,
        )
    }

    @Test
    fun `the context never ends in whitespace that would waste prompt tokens`() {
        // Arrange
        val plan = workoutPlanEntity(weeks = listOf(weekDto()))

        // Act
        val context = PlanContextFormatter.format(plan)!!

        // Assert
        assertEquals(context.trimEnd(), context)
    }
}
