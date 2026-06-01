package com.example.hybrid_ai_app.core.data.local.converter

import androidx.room.TypeConverter
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutLogConverters {

    @TypeConverter
    fun fromLoggedExerciseList(value: List<LoggedExerciseEntity>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toLoggedExerciseList(value: String): List<LoggedExerciseEntity> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}