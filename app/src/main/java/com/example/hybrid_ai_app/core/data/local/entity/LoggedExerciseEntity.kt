package com.example.hybrid_ai_app.core.data.local.entity

import kotlinx.serialization.Serializable

/**
 * One exercise of a logged session, stored inside the log row as JSON.
 *
 * Every property has a default, and [WorkoutLogConverters][com.example.hybrid_ai_app.core.data.local.converter.WorkoutLogConverters]
 * decodes leniently, because this shape is an on-disk schema with no version attached: a row
 * written by an older build must still decode, and a row written by a newer one must not lose its
 * exercises to a key this build has never heard of.
 *
 * `sets`, `reps`, `weight` and `rpe` are the legacy summary (what the first version of the app
 * logged, one weight per exercise). New logs also carry the real per-set [setLogs]; the summary
 * fields stay so a list of past workouts can render without opening every set.
 */
@Serializable
data class LoggedExerciseEntity(
    val name: String,
    val sets: String = "",
    val reps: String = "",
    val weight: String = "",
    val rpe: String = "",
    // Id in the bundled exercise catalog, when the plan exercise had one.
    val exerciseId: String? = null,
    val notes: String? = null,
    val setLogs: List<LoggedSetEntity> = emptyList(),
)

/**
 * A set as it was performed. Values are strings because that is what the athlete typed: they are
 * parsed (and range-checked) only when the log is pushed to the backend.
 */
@Serializable
data class LoggedSetEntity(
    // Lowercase wire name: "normal", "warmup", "drop" or "failure".
    val type: String = "normal",
    val weight: String = "",
    val reps: String = "",
    val targetReps: String = "",
    val targetRpe: String = "",
    val actualRpe: String = "",
    val completed: Boolean = false,
)
