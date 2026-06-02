package com.example.hybrid_ai_app.core.data.local.converter

import androidx.room.TypeConverter
import com.example.hybrid_ai_app.core.data.remote.dto.WeekDto // Adjust to your WeekEntity if mapped
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutPlanConverters {

    // Configures the JSON
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
            emptyList()
        }
    }

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