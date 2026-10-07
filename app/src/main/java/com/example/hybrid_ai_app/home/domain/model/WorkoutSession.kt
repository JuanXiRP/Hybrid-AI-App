package com.example.hybrid_ai_app.home.domain.model

import kotlinx.serialization.Serializable

/**
 * What a set is. Normal sets are the numbered ones; the others are labelled W, D and F in the
 * table and are kept out of the "working sets" that the legacy summary fields are derived from.
 */
@Serializable
enum class SetType(val wireName: String) {
    NORMAL("normal"),
    WARMUP("warmup"),
    DROP("drop"),
    FAILURE("failure"),
    ;

    companion object {
        /** Unknown or missing names read as [NORMAL], the backend's own default. */
        fun fromWireName(name: String?): SetType = entries.firstOrNull { it.wireName == name } ?: NORMAL
    }
}

/**
 * A strength workout as it is being performed.
 *
 * It is `@Serializable` because the session in progress is persisted as one JSON document
 * (`ActiveWorkoutEntity.sessionJson`) so it survives process death, and the same document feeds the
 * minimized bar and the foreground service. Everything the athlete types is a string, exactly as
 * typed; parsing happens when the session is finished.
 *
 * Times are epoch milliseconds. [restEndsAt] is absolute rather than a remaining duration, which is
 * what lets a countdown survive the screen being left and the process being killed: whoever reads
 * it subtracts the current time.
 */
@Serializable
data class WorkoutSession(
    val clientId: String,
    // The plan day this session was opened for. It, and not the athlete's current progress, is
    // what the finished log is attributed to.
    val weekNumber: Int,
    val dayIndex: Int,
    val title: String,
    val startedAt: Long,
    val notes: String = "",
    val exercises: List<SessionExercise>,
    val restEndsAt: Long? = null,
    val restTotalSec: Int? = null,
    // A session added on top of the plan rather than opened from one of its days. [weekNumber] and
    // [dayIndex] are then the plan's "today" when it started, and finishing it closes no plan day.
    val isExtra: Boolean = false,
)

@Serializable
data class SessionExercise(
    // Stable identity for list keys and edits; the plan itself has none for an exercise.
    val id: String,
    val exerciseId: String?,
    val name: String,
    val notes: String = "",
    // Rest started after completing a set of this exercise. 0 turns the automatic rest off.
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val sets: List<SessionSet>,
)

@Serializable
data class SessionSet(
    val id: String,
    val type: SetType = SetType.NORMAL,
    // Read-only targets from the plan, shown as placeholders.
    val targetReps: String = "",
    val targetRpe: String = "",
    val weight: String = "",
    val reps: String = "",
    val actualRpe: String = "",
    val completed: Boolean = false,
)

const val DEFAULT_REST_SECONDS = 120
