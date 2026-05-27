package com.example.hybrid_ai_app.core.data.local.converter

import androidx.room.TypeConverter
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto // Adjust to your WeekEntity if mapped
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Room TypeConverters using kotlinx.serialization for optimal performance
class WorkoutPlanConverters {

    // Configures the JSON engine to ignore unknown keys, preventing crashes on schema migrations
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromWeekList(value: List<WeekDto>?): String {
        if (value == null) return "[]"
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toWeekList(value: String): List<WeekDto> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList() // Graceful degradation if local database is corrupted
        }
    }

    // Add similar converters for List<String> (e.g., for injuries) or any other complex type
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}