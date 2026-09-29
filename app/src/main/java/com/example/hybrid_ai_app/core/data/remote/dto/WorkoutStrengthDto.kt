package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutStrengthDto(
    @SerialName("_id") val id: String? = null,
    val userId: String? = null,
    val date: String? = null,
    val routineType: String,
    val exercises: List<StrengthExerciseDto> = emptyList(),
    // Plan markers: which planned session this log closed. Optional on the wire (both are
    // omitted when null, since NetworkJson leaves encodeDefaults off), because the backend
    // accepts logs from clients that predate them. They are what lets the AI coach say which
    // sessions are left this week instead of inferring it from how many logs exist.
    val weekNumber: Int? = null,
    val dayIndex: Int? = null,
    // The idempotency key. It travels in the URL of `PUT /api/workouts/strength/{clientId}`, and
    // the backend takes it from there, not from the body; it is declared here so a response that
    // echoes the document still decodes into it.
    val clientId: String? = null,
    // ISO-8601 instant the session started, and how long it lasted.
    val startedAt: String? = null,
    val durationSec: Long? = null,
    val notes: String? = null,
)

@Serializable
data class StrengthExerciseDto(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val targetWeight: Double,
    val actualWeight: Double? = null,
    val targetRpe: Int,
    val actualRpe: Int? = null,
    // Catalog id and a per-exercise note. The set-by-set detail below is optional so a client that
    // predates it, and the legacy summary fields above, keep working.
    val exerciseId: String? = null,
    val notes: String? = null,
    val setLogs: List<StrengthSetDto>? = null,
)

@Serializable
data class StrengthSetDto(
    // "normal" | "warmup" | "drop" | "failure". No default, so it is always sent (a default would
    // be dropped by `encodeDefaults = false`), and the backend's own default never has to apply.
    val type: String,
    val weight: Double? = null,
    val reps: Int? = null,
    val targetReps: String? = null,
    val targetRpe: Int? = null,
    val actualRpe: Int? = null,
)
