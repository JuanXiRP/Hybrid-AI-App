package com.example.hybrid_ai_app.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_logs",
    indices = [Index(value = ["clientId"], unique = true)],
)
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekNumber: Int,
    val dayIndex: Int,
    val timestamp: Long,
    val isCompleted: Boolean,
    val loggedExercises: List<LoggedExerciseEntity> = emptyList(),
    // The idempotency key shared with the backend: `PUT /api/workouts/strength/{clientId}` creates
    // or edits exactly one document, so a retried sync or an edit never duplicates the session.
    val clientId: String,
    // What the session was called and what kind it was, stored on the log itself. Without them the
    // history has to look the day up in the active plan, which is wrong (or empty) once the plan
    // has been regenerated. Null on rows written before these columns existed.
    val title: String? = null,
    val workoutType: String? = null,
    val startedAt: Long? = null,
    val durationSec: Long? = null,
    val notes: String? = null,
    // True until the backend has acknowledged this log. A log is saved locally first, so a failed
    // push is remembered here and retried later.
    val syncPending: Boolean = false,
)
