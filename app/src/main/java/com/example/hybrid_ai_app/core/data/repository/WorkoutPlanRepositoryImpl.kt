// src/main/java/com/example/hybrid_ai_app/core/data/repository/WorkoutPlanRepositoryImpl.kt
package com.example.hybrid_ai_app.core.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.hybrid_ai_app.core.data.local.AppDatabase
import com.example.hybrid_ai_app.core.data.local.dao.ActiveWorkoutDao
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.LoggedExerciseEntity
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.mapper.toStrengthDto
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutRunDto
import com.example.hybrid_ai_app.core.di.ApplicationScope
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.home.data.mapper.toWorkoutLog
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

private const val TAG = "WorkoutSync"

private const val STRENGTH = "strength"
private const val ACTIVE_PLAN_ID = "active_plan"
private const val LAST_DAY_OF_WEEK = 6

/** How many past logs are scanned for the "previous" column: months of training, but bounded. */
private const val PREVIOUS_SCAN_LIMIT = 60

/** What happened to one attempt to push a log, which decides whether a retry run keeps going. */
private enum class SyncOutcome { SYNCED, REJECTED, UNREACHABLE }

class WorkoutPlanRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val planDao: WorkoutPlanDao,
    private val progressDao: ProgressDao,
    private val activeWorkoutDao: ActiveWorkoutDao,
    private val api: UserApi,
    // Injected rather than built inline so the fire-and-forget sync below runs on a scheduler
    // tests control. See CoroutinesModule.
    @ApplicationScope private val syncScope: CoroutineScope,
) : WorkoutPlanRepository {

    // One retry run at a time: app start and every completion both ask for one, and two runs would
    // push the same logs twice.
    private val syncMutex = Mutex()

    override fun getActivePlan(): Flow<WorkoutPlanEntity?> = planDao.getActivePlan()

    override fun getUserProgress(): Flow<UserProgressEntity?> = progressDao.getUserProgress()

    override fun getLogsForWeek(weekNumber: Int): Flow<List<WorkoutLogEntity>> = progressDao.getLogsForWeek(weekNumber)

    override fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>> = progressDao.getAllWorkoutLogs()

    override suspend fun updateProgress(progress: UserProgressEntity) {
        progressDao.insertOrUpdateProgress(progress)
    }

    override suspend fun toggleDayStatus(weekNumber: Int, dayIndex: Int) {
        // Fetch all completed logs for the target week safely
        val weeklyLogs = progressDao.getLogsForWeek(weekNumber).firstOrNull() ?: emptyList()

        val existingLog = weeklyLogs.find { it.dayIndex == dayIndex }

        if (existingLog != null) {
            progressDao.deleteWorkoutLog(existingLog)
        } else {
            val newLog = WorkoutLogEntity(
                weekNumber = weekNumber,
                dayIndex = dayIndex,
                timestamp = System.currentTimeMillis(),
                isCompleted = true,
                clientId = UUID.randomUUID().toString(),
            )
            progressDao.insertWorkoutLog(newLog)
        }
    }

    override suspend fun completeWorkout(
        log: WorkoutLogEntity,
        nextProgress: UserProgressEntity,
        workoutType: String,
        dayName: String,
    ) {
        val isStrength = workoutType == STRENGTH
        // The log carries its own title and type so history never has to look the day up in a plan
        // that may since have been regenerated. A strength log stays pending until the backend
        // confirms it, which is what lets a failed push be retried.
        val stored = log.copy(
            title = log.title ?: dayName,
            workoutType = log.workoutType ?: workoutType,
            syncPending = isStrength,
        )

        database.withTransaction {
            progressDao.insertWorkoutLog(stored)
            progressDao.insertOrUpdateProgress(nextProgress)
        }

        // Remote synchronization. Fire-and-forget on purpose: the user has already seen the
        // workout marked complete from the local write above, and a failed push is retried on the
        // next sync rather than surfaced as an error they cannot act on.
        syncScope.launch {
            try {
                when {
                    isStrength -> retryPendingSyncs()
                    workoutType == "cardio" || workoutType == "run" -> syncRun(log)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while syncing a completed workout", e)
            }
        }
    }

    override suspend fun completeSession(
        session: WorkoutSession,
        discardIncomplete: Boolean,
        finishedAt: Long,
    ): Result<Unit> = localWrite {
        val log = session.toWorkoutLog(finishedAt = finishedAt, completeAll = !discardIncomplete)

        database.withTransaction {
            progressDao.insertWorkoutLog(log)
            advanceProgressIfCurrent(session)
            activeWorkoutDao.clear()
        }

        launchPendingSync()
    }

    override suspend fun getWorkoutLog(id: Long): WorkoutLogEntity? = progressDao.getWorkoutLogById(id)

    override suspend fun updateWorkoutLog(log: WorkoutLogEntity): Result<Unit> = localWrite {
        // Pending again: the backend must receive the edit, under the same clientId.
        progressDao.updateWorkoutLog(log.copy(syncPending = true))
        launchPendingSync()
    }

    override suspend fun getLastPerformance(
        exerciseId: String?,
        name: String,
        beforeTimestamp: Long,
    ): List<LoggedSetEntity> {
        val recent = progressDao.getRecentWorkoutLogs(beforeTimestamp, PREVIOUS_SCAN_LIMIT)

        for (log in recent) {
            val exercise = log.loggedExercises.firstOrNull { it.matches(exerciseId, name) } ?: continue

            if (exercise.setLogs.isNotEmpty()) return exercise.setLogs
            // A log from before per-set detail: one weight for the whole exercise. Without a weight
            // there is nothing useful to show, so keep looking further back.
            if (exercise.weight.isNotBlank()) {
                return listOf(LoggedSetEntity(weight = exercise.weight, reps = exercise.reps, completed = true))
            }
        }
        return emptyList()
    }

    override suspend fun retryPendingSyncs() {
        syncMutex.withLock {
            for (log in progressDao.getPendingSyncLogs()) {
                // A network failure means the rest would fail the same way, each after a long
                // timeout: stop and wait for the next trigger. A rejection is specific to that log.
                if (syncStrength(log) == SyncOutcome.UNREACHABLE) break
            }
        }
    }

    override suspend fun clearActivePlanAndProgress() {
        // withTransaction guarantees all tables are cleared simultaneously. The logs are left
        // alone on purpose: they carry their own title and type, and are the athlete's history.
        database.withTransaction {
            planDao.clearPlan()
            progressDao.clearAllProgress()
            activeWorkoutDao.clear()
        }
    }

    // ------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------

    /** Runs a local write and reports its outcome, without ever swallowing cancellation. */
    private suspend fun localWrite(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Local workout write failed", e)
        Result.failure(e)
    }

    private fun launchPendingSync() {
        syncScope.launch {
            try {
                retryPendingSyncs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while syncing workouts", e)
            }
        }
    }

    /**
     * Moves the athlete on only if the finished day is the one they were up to. Finishing another
     * day (the quick-start sheet lets any pending day of the week be opened) must not skip ahead.
     */
    private suspend fun advanceProgressIfCurrent(session: WorkoutSession) {
        val progress = progressDao.getProgress()
        val currentWeek = progress?.currentWeekNumber ?: 1
        val currentDay = progress?.currentDayIndex ?: 0
        if (currentWeek != session.weekNumber || currentDay != session.dayIndex) return

        val isLastDayOfWeek = currentDay == LAST_DAY_OF_WEEK
        progressDao.insertOrUpdateProgress(
            UserProgressEntity(
                userId = progress?.userId ?: ACTIVE_PLAN_ID,
                currentWeekNumber = if (isLastDayOfWeek) currentWeek + 1 else currentWeek,
                currentDayIndex = if (isLastDayOfWeek) 0 else currentDay + 1,
            ),
        )
    }

    private suspend fun syncStrength(log: WorkoutLogEntity): SyncOutcome = try {
        val response = api.upsertStrengthWorkout(log.clientId, log.toStrengthDto())
        if (response.isSuccessful) {
            progressDao.setSyncPending(log.clientId, false)
            SyncOutcome.SYNCED
        } else {
            // Stays pending, so a later trigger tries again (a 402 after the trial ended, a 401
            // before the athlete signs back in, a backend that has not shipped the endpoint yet).
            Log.e(TAG, "Strength log rejected: ${response.code()} ${response.errorBody()?.string()}")
            SyncOutcome.REJECTED
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Strength log not sent: network or serialisation error", e)
        SyncOutcome.UNREACHABLE
    }

    private suspend fun syncRun(log: WorkoutLogEntity) {
        try {
            // The user's RPE if they entered one, or 8 by default.
            val rpeValue = log.loggedExercises.firstOrNull()?.rpe?.toIntOrNull() ?: 8

            val runPayload = WorkoutRunDto(
                userId = "dummy",
                distance = 0.0,
                duration = 0,
                targetPace = 0,
                actualPace = 0,
                elevationGain = 0.0,
                rpe = rpeValue,
                gpsPath = emptyList(),
                weekNumber = log.weekNumber,
                dayIndex = log.dayIndex,
            )

            val response = api.syncRunWorkout(runPayload)
            if (!response.isSuccessful) {
                Log.e(TAG, "Run log rejected: ${response.errorBody()?.string()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Run log not sent: network or serialisation error", e)
        }
    }

    private fun LoggedExerciseEntity.matches(exerciseId: String?, name: String): Boolean = if (exerciseId != null && this.exerciseId != null) {
        this.exerciseId == exerciseId
    } else {
        this.name.trim().equals(name.trim(), ignoreCase = true)
    }
}
