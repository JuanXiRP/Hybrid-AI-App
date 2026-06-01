package com.example.hybrid_ai_app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_logs")
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekNumber: Int,
    val dayIndex: Int,
    val timestamp: Long,
    val isCompleted: Boolean,
    val loggedExercises: List<LoggedExerciseEntity> = emptyList()
)