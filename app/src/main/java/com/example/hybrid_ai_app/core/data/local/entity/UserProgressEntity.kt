package com.example.hybrid_ai_app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val userId: String,
    val currentWeekNumber: Int = 1,
    val currentDayIndex: Int = 0
)