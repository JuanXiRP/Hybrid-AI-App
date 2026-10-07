package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.domain.model.CompletedRun
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutPlanRepository {
    fun getActivePlan(): Flow<WorkoutPlanEntity?>

    fun getUserProgress(): Flow<UserProgressEntity?>

    fun getLogsForWeek(weekNumber: Int): Flow<List<WorkoutLogEntity>>

    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>>

    suspend fun updateProgress(progress: UserProgressEntity)

    suspend fun toggleDayStatus(weekNumber: Int, dayIndex: Int)

    /**
     * Logs a day as completed without any per-set detail (a rest day, or "log as completed" on the
     * dashboard). A strength log is pushed to the backend in the background.
     */
    suspend fun completeWorkout(
        log: WorkoutLogEntity,
        nextProgress: UserProgressEntity,
        workoutType: String,
        dayName: String,
    )

    /**
     * Finishes a session: writes its log, advances progress when it was the current day, and clears
     * the session in progress, all in one transaction. The backend push then runs in the
     * background. The result is the *local* outcome, so a caller can wait for it before navigating.
     *
     * The log is attributed to the session's own week and day, never to the athlete's progress.
     *
     * @param discardIncomplete keep only the sets that were ticked; otherwise every set counts.
     */
    suspend fun completeSession(
        session: WorkoutSession,
        discardIncomplete: Boolean,
        finishedAt: Long,
    ): Result<Unit>

    /**
     * Saves a finished run against its own plan day, advancing progress only if that day is the
     * current one (and never for an extra run), and clears the run in progress in the same
     * transaction. It is then pushed to the backend in the background with its tracked metrics.
     * The result is the *local* outcome, so a caller can wait for it before navigating.
     */
    suspend fun completeRun(run: CompletedRun): Result<Unit>

    suspend fun getWorkoutLog(id: Long): WorkoutLogEntity?

    /** Saves an edited log and pushes it again under the same `clientId`. Progress is untouched. */
    suspend fun updateWorkoutLog(log: WorkoutLogEntity): Result<Unit>

    /**
     * How the athlete last performed an exercise, set by set, for the "previous" column.
     *
     * Matches by catalog id when both sides have one, otherwise by name, and looks only at logs
     * older than [beforeTimestamp] (so editing a past workout compares against what came before it).
     * A log from before per-set detail existed yields one synthesised set from its single weight.
     */
    suspend fun getLastPerformance(
        exerciseId: String?,
        name: String,
        beforeTimestamp: Long = Long.MAX_VALUE,
    ): List<LoggedSetEntity>

    /** Pushes every strength log the backend has not acknowledged yet. Safe to call any time. */
    suspend fun retryPendingSyncs()

    /**
     * Clears the plan, the progress and the session or run in progress, but **keeps the logs**: they carry
     * their own title and type, so regenerating a plan no longer erases the athlete's history.
     */
    suspend fun clearActivePlanAndProgress()
}
