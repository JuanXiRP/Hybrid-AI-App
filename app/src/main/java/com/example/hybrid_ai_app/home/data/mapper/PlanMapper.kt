package com.example.hybrid_ai_app.home.data.mapper

import com.example.hybrid_ai_app.home.data.remote.dto.DayDto
import com.example.hybrid_ai_app.home.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.home.data.remote.dto.WeekDto
import com.example.hybrid_ai_app.home.data.remote.dto.WorkoutPlanDto
import com.example.hybrid_ai_app.home.domain.model.ActivePlan
import com.example.hybrid_ai_app.home.domain.model.Exercise
import com.example.hybrid_ai_app.home.domain.model.PlanDay
import com.example.hybrid_ai_app.home.domain.model.PlanWeek
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun WorkoutPlanDto.toDomain(): ActivePlan {

    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

    val parsedDate = try {
        if (this.startDate.isNotBlank()) {
            format.parse(this.startDate) ?: Date()
        } else {
            Date()
        }
    } catch (e: Exception) {
        Date()
    }

    return ActivePlan(
        id = this.id ?: "",
        startDate = parsedDate,
        durationWeeks = this.durationWeeks,
        goal = this.goal,
        // Functional programming mapping for nested lists
        weeks = this.weeks.map { it.toDomain() },
        isActive = this.active
    )
}

fun WeekDto.toDomain(): PlanWeek = PlanWeek(
    weekNumber = this.weekNumber,
    days = this.days.map { it.toDomain() }
)

fun DayDto.toDomain(): PlanDay = PlanDay(
    dayName = this.dayName,
    exercises = this.exercises.map { it.toDomain() }
)

fun ExerciseDto.toDomain(): Exercise = Exercise(
    name = this.name,
    sets = this.sets,
    reps = this.reps,
    rpe = this.rpe
)