package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.domain.model.RunPoint
import kotlinx.coroutines.flow.Flow

/**
 * The run in progress, kept in Room so it outlives the screen, the activity and the process.
 *
 * The run screen writes it (start, pause, resume, discard) and the tracking service follows it:
 * the service observes [observe] and starts, pauses or stops GPS and the clock to match, and it is
 * the one that appends the tracked [RunPoint]s.
 */
interface ActiveRunRepository {
    /** The run in progress, or null; emits again on every change to it (not on new points). */
    fun observe(): Flow<ActiveRun?>

    suspend fun get(): ActiveRun?

    /** Starts [run], replacing any run in progress and dropping its points. */
    suspend fun start(run: ActiveRun)

    /** Every point tracked so far, oldest first. */
    suspend fun points(): List<RunPoint>

    suspend fun addPoint(point: RunPoint)

    /** Banks [accumulatedMs] and stops the clock. */
    suspend fun pause(accumulatedMs: Long)

    /** Restarts the clock from [atMillis]; what was banked is kept. */
    suspend fun resume(atMillis: Long)

    /** Drops the run and its points. */
    suspend fun clear()
}
