package com.example.hybrid_ai_app.home.data.mapper

import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto
import com.example.hybrid_ai_app.core.data.remote.dto.ExerciseDto
import com.example.hybrid_ai_app.home.domain.model.DEFAULT_REST_SECONDS
import com.example.hybrid_ai_app.home.domain.model.SessionExercise
import com.example.hybrid_ai_app.home.domain.model.SessionSet
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession

/** How many sets an exercise gets when the plan states no usable number, and the cap on any. */
private const val DEFAULT_SET_COUNT = 3
private const val MAX_SET_COUNT = 10

/** The placeholder the backend writes into a plan field the source did not state. */
private const val UNSTATED = "-"

/**
 * A plan day, turned into a session ready to be performed.
 *
 * The plan's numbers are advice, not data: `sets` becomes that many empty rows (3 when it is not a
 * plain number such as "3-4"), and `reps` / `rpe` become read-only targets shown as placeholders.
 * Nothing the plan says is copied into what the athlete actually did.
 */
fun DayDto.toWorkoutSession(
    clientId: String,
    weekNumber: Int,
    dayIndex: Int,
    startedAt: Long,
    newId: () -> String,
): WorkoutSession = WorkoutSession(
    clientId = clientId,
    weekNumber = weekNumber,
    dayIndex = dayIndex,
    title = dayName,
    startedAt = startedAt,
    exercises = exercises.map { it.toSessionExercise(newId) },
)

private fun ExerciseDto.toSessionExercise(newId: () -> String): SessionExercise {
    val setCount = sets.trim().toIntOrNull()?.coerceIn(1, MAX_SET_COUNT) ?: DEFAULT_SET_COUNT
    return SessionExercise(
        id = newId(),
        exerciseId = exerciseId,
        name = name,
        restSeconds = DEFAULT_REST_SECONDS,
        sets = List(setCount) {
            SessionSet(
                id = newId(),
                targetReps = reps.asTarget(),
                targetRpe = rpe.asTarget(),
            )
        },
    )
}

private fun String.asTarget(): String = trim().takeUnless { it == UNSTATED } ?: ""

/**
 * Sets that count as "working" sets. Warm-ups are excluded from every figure derived for the
 * legacy summary fields, because a warm-up at 40 kg says nothing about how the exercise went.
 */
fun List<SessionSet>.workingSets(): List<SessionSet> = filter { it.type != SetType.WARMUP }

/**
 * The finished session, as the log row that is stored and synced.
 *
 * Only sets that were performed are kept: a set is included if it is ticked, or if [completeAll] is
 * set (the athlete chose "complete all" when finishing with unticked sets). An exercise left with no
 * sets is dropped. Every kept set is stored as completed, because a log is by definition what was
 * done.
 *
 * `sets`, `reps`, `weight` and `rpe` on each exercise are the legacy summary, derived from the
 * working sets, so a list of workouts renders without opening every set and old code that reads
 * them keeps working. `rpe` there is the *actual* mean, or blank when none was entered.
 *
 * @param id the Room row to overwrite when editing, 0 for a new log.
 */
fun WorkoutSession.toWorkoutLog(
    finishedAt: Long,
    completeAll: Boolean,
    id: Long = 0,
): WorkoutLogEntity = WorkoutLogEntity(
    id = id,
    weekNumber = weekNumber,
    dayIndex = dayIndex,
    timestamp = finishedAt,
    isCompleted = true,
    loggedExercises = exercises.mapNotNull { it.toLoggedExercise(completeAll) },
    clientId = clientId,
    title = title,
    workoutType = STRENGTH,
    startedAt = startedAt,
    durationSec = ((finishedAt - startedAt) / 1000).coerceAtLeast(0),
    notes = notes.trim().ifBlank { null },
    // A strength log is pushed to the backend, and stays pending until the backend confirms it.
    syncPending = true,
    isExtra = isExtra,
)

private const val STRENGTH = "strength"

private fun SessionExercise.toLoggedExercise(completeAll: Boolean): LoggedExerciseEntity? {
    val kept = sets.filter { it.completed || completeAll }
    if (kept.isEmpty()) return null

    val working = kept.workingSets()
    val heaviest = working.maxByOrNull { parseWeight(it.weight) ?: Double.NEGATIVE_INFINITY }
    val meanRpe = working.mapNotNull { parseRpe(it.actualRpe) }.takeIf { it.isNotEmpty() }?.average()

    return LoggedExerciseEntity(
        name = name,
        sets = working.size.coerceAtLeast(1).toString(),
        reps = working.lastOrNull()?.reps.orEmpty(),
        weight = heaviest?.weight.orEmpty(),
        rpe = meanRpe?.let { Math.round(it).toString() }.orEmpty(),
        exerciseId = exerciseId,
        notes = notes.trim().ifBlank { null },
        setLogs = kept.map { set ->
            LoggedSetEntity(
                type = set.type.wireName,
                weight = set.weight,
                reps = set.reps,
                targetReps = set.targetReps,
                targetRpe = set.targetRpe,
                actualRpe = set.actualRpe,
                completed = true,
            )
        },
    )
}

/**
 * A stored log, reopened as a session so it can be edited with the same screen.
 *
 * The exercises come back with the sets that were logged (all ticked). What the log does not keep
 * — the per-exercise rest time — takes its default again.
 */
fun WorkoutLogEntity.toWorkoutSession(newId: () -> String): WorkoutSession = WorkoutSession(
    clientId = clientId,
    weekNumber = weekNumber,
    dayIndex = dayIndex,
    title = title.orEmpty(),
    startedAt = startedAt ?: timestamp,
    notes = notes.orEmpty(),
    isExtra = isExtra,
    exercises = loggedExercises.map { exercise ->
        SessionExercise(
            id = newId(),
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            notes = exercise.notes.orEmpty(),
            sets = exercise.setLogs.map { set ->
                SessionSet(
                    id = newId(),
                    type = SetType.fromWireName(set.type),
                    targetReps = set.targetReps,
                    targetRpe = set.targetRpe,
                    weight = set.weight,
                    reps = set.reps,
                    actualRpe = set.actualRpe,
                    completed = set.completed,
                )
            },
        )
    },
)

/** Parses what the athlete typed as a weight; a comma decimal is accepted, negatives are not. */
fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

/** Parses reps as a whole number, negatives excluded. */
fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }

private val FIRST_NUMBER = Regex("""\d+(?:[.,]\d+)?""")

/**
 * The first number in an RPE field, rounded, when it is a real RPE (1 to 10).
 *
 * "8" and "7.5" parse, and so does a planned range like "8-10" (its first number), which is what a
 * target looks like. Anything outside 1..10 is dropped rather than sent: the backend rejects the
 * whole log over a single out-of-range value.
 */
fun parseRpe(text: String): Int? = FIRST_NUMBER.find(text)?.value
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.let { Math.round(it).toInt() }
    ?.takeIf { it in 1..10 }
