package com.example.hybrid_ai_app.coach.data

import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity

/**
 * Turns the user's active workout plan into a compact, human-readable summary that is
 * cheap in tokens and easy for the coach LLM to ground on. Returns null when there is no
 * plan, so the caller can simply omit the context field.
 */
object PlanContextFormatter {

    fun format(plan: WorkoutPlanEntity?): String? {
        if (plan == null) return null

        return buildString {
            append("User's active training plan — Goal: ${plan.goal}, ")
            append("Duration: ${plan.durationWeeks} weeks.\n")

            plan.weeks.forEach { week ->
                append("Week ${week.weekNumber}:\n")
                week.days.forEachIndexed { index, day ->
                    val dayLabel = "Day ${index + 1} - ${day.dayName} [${day.workoutType}]"
                    if (day.exercises.isEmpty()) {
                        append("  $dayLabel: rest\n")
                    } else {
                        val exercises = day.exercises.joinToString("; ") { ex ->
                            "${ex.name} (${ex.sets}x${ex.reps}, RPE ${ex.rpe})"
                        }
                        append("  $dayLabel: $exercises\n")
                    }
                }
            }
        }.trimEnd()
    }
}
