package com.example.hybrid_ai_app.home.domain.model

import java.util.Date

// Clean representation of the training plan for the UI
data class ActivePlan(
    val id: String,
    val startDate: Date,
    val durationWeeks: Int,
    val goal: String,
    val weeks: List<PlanWeek>,
    val isActive: Boolean
)

data class PlanWeek(
    val weekNumber: Int,
    val days: List<PlanDay>
)

data class PlanDay(
    val dayName: String,
    val exercises: List<Exercise>
)

data class Exercise(
    val name: String,
    val sets: String,
    val reps: String,
    val rpe: String
)