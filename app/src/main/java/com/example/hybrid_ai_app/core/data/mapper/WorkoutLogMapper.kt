package com.example.hybrid_ai_app.core.data.mapper

import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.remote.dto.StrengthExerciseDto
import com.example.hybrid_ai_app.core.data.remote.dto.StrengthSetDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutStrengthDto
import com.example.hybrid_ai_app.home.data.mapper.parseReps
import com.example.hybrid_ai_app.home.data.mapper.parseRpe
import com.example.hybrid_ai_app.home.data.mapper.parseWeight
import com.example.hybrid_ai_app.home.domain.model.SetType
import java.time.Instant

// The backend's schema limits. A value over one rejects the whole log with a 400, so the payload is
// clamped here rather than trusting what was typed.
private const val MAX_NOTES = 2000
private const val MAX_EXERCISE_NOTES = 1000

private const val WARMUP = "warmup"
private const val DEFAULT_TARGET_RPE = 8

/**
 * A stored strength log, as the body of `PUT /api/workouts/strength/{clientId}`.
 *
 * The backend still requires the legacy per-exercise summary (`sets`, `reps`, `targetWeight`,
 * `targetRpe`) and the coach reads it, so it is derived here from the per-set detail — the backend
 * needs no change to understand a new-format log. "Working sets" are the non-warm-up ones.
 */
fun WorkoutLogEntity.toStrengthDto(): WorkoutStrengthDto = WorkoutStrengthDto(
    // The name of the planned day, which the coach reads back as the session's routine type.
    routineType = title.orEmpty().ifBlank { if (isExtra) "Extra workout" else "Workout" },
    // The moment the session finished, not the moment it happened to be synced: a retry hours
    // later must not move the log to the wrong day.
    date = Instant.ofEpochMilli(timestamp).toString(),
    clientId = clientId,
    startedAt = startedAt?.let { Instant.ofEpochMilli(it).toString() },
    durationSec = durationSec?.coerceAtLeast(0),
    notes = notes?.take(MAX_NOTES),
    // The day this session closed. The plan id is not sent: the cached plan does not carry the
    // server's _id, and the backend reads a log with none as belonging to the active plan.
    weekNumber = weekNumber,
    dayIndex = dayIndex,
    // Only an extra carries the flag; a planned log keeps the payload it always had.
    isExtra = isExtra.takeIf { it },
    exercises = loggedExercises.map { it.toStrengthDto() },
)

private fun LoggedExerciseEntity.toStrengthDto(): StrengthExerciseDto = if (setLogs.isEmpty()) {
    legacyStrengthDto()
} else {
    perSetStrengthDto()
}

/**
 * A log written before per-set detail existed: one weight per exercise, and `rpe` holding the
 * plan's *target*. That target is sent as the target only; presenting it as the actual RPE (which
 * an earlier version did) told the coach every session landed exactly on plan.
 */
private fun LoggedExerciseEntity.legacyStrengthDto() = StrengthExerciseDto(
    exerciseName = name,
    sets = sets.toIntOrNull() ?: 1,
    reps = reps.toIntOrNull() ?: 1,
    targetWeight = 0.0,
    actualWeight = weight.toDoubleOrNull() ?: 0.0,
    targetRpe = parseRpe(rpe) ?: DEFAULT_TARGET_RPE,
    actualRpe = null,
    exerciseId = exerciseId,
    notes = notes?.take(MAX_EXERCISE_NOTES),
)

private fun LoggedExerciseEntity.perSetStrengthDto(): StrengthExerciseDto {
    val working = setLogs.filter { it.type != WARMUP }
    val actualRpes = working.mapNotNull { parseRpe(it.actualRpe) }

    return StrengthExerciseDto(
        exerciseName = name,
        sets = working.size.coerceAtLeast(1),
        reps = working.lastOrNull()?.let { parseReps(it.reps) } ?: 1,
        targetWeight = 0.0,
        actualWeight = working.mapNotNull { parseWeight(it.weight) }.maxOrNull(),
        targetRpe = working.firstNotNullOfOrNull { parseRpe(it.targetRpe) } ?: DEFAULT_TARGET_RPE,
        actualRpe = actualRpes.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
        exerciseId = exerciseId,
        notes = notes?.take(MAX_EXERCISE_NOTES),
        setLogs = setLogs.map { it.toStrengthDto() },
    )
}

private fun LoggedSetEntity.toStrengthDto() = StrengthSetDto(
    // Normalised, so a name this build does not know cannot make the backend reject the log.
    type = SetType.fromWireName(type).wireName,
    weight = parseWeight(weight),
    reps = parseReps(reps),
    targetReps = targetReps.ifBlank { null },
    targetRpe = parseRpe(targetRpe),
    actualRpe = parseRpe(actualRpe),
)
