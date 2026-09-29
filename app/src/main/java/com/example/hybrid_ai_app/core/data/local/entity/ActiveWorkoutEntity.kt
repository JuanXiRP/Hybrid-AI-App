package com.example.hybrid_ai_app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The workout session in progress, if any. There is at most one row, so the id is a constant.
 *
 * The session itself is stored as JSON ([sessionJson]) because it is a nested, frequently changing
 * document that nothing queries into; `weekNumber` and `dayIndex` are lifted out only so the
 * minimized bar and the resume/conflict check can read them without decoding it.
 */
@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(
    @PrimaryKey val id: String = ACTIVE_WORKOUT_ID,
    val weekNumber: Int,
    val dayIndex: Int,
    val sessionJson: String,
) {
    companion object {
        const val ACTIVE_WORKOUT_ID = "active"
    }
}
