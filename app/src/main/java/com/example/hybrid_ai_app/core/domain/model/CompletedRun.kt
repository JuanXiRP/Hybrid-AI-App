package com.example.hybrid_ai_app.core.domain.model

import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity

/** One GPS fix of a tracked run. Kept free of the Maps `LatLng` so it is plain JVM data. */
data class RunPoint(val lat: Double, val lng: Double)

/**
 * A tracked run the athlete has just finished, attributed to the plan day it was opened from
 * (the `workout_execution/{weekNumber}/{dayIndex}` route), never to the progress pointer.
 */
data class CompletedRun(
    /** The log's identity, minted when the run started. */
    val clientId: String,
    val weekNumber: Int,
    val dayIndex: Int,
    val title: String,
    /** The day's planned run, recorded as the log's exercises (a run has no weight to log). */
    val instruction: List<LoggedExerciseEntity>,
    val finishedAt: Long,
    val durationSec: Long,
    val distanceKm: Double,
    val path: List<RunPoint>,
    /** A run added on top of the plan: it is logged but advances no progress and closes no day. */
    val isExtra: Boolean = false,
)
