package com.example.hybrid_ai_app.core.data.local.converter

import androidx.room.TypeConverter
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutLogConverters {

    // Lenient on purpose. The logged exercises are an on-disk schema with no version, so a row
    // written by a build with more fields than this one knows must not turn its exercises into an
    // empty list. (Historically this used the strict default `Json`, which made any unknown key a
    // silent data loss; missing keys are covered by the defaults on the entity.)
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromLoggedExerciseList(value: List<LoggedExerciseEntity>): String = json.encodeToString(value)

    @TypeConverter
    fun toLoggedExerciseList(value: String): List<LoggedExerciseEntity> = try {
        json.decodeFromString(value)
    } catch (e: Exception) {
        emptyList()
    }
}
