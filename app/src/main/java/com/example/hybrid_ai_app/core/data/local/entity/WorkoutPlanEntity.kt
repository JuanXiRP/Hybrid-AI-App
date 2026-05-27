package com.example.hybrid_ai_app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey
    val id: String = "active_plan",
    val durationWeeks: Int,
    val goal: String,
    val weeks: List<WeekDto> // Room requires a TypeConverter to store this complex list
)