package com.example.hybrid_ai_app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM user_progress LIMIT 1")
    fun getUserProgress(): Flow<UserProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: UserProgressEntity)

    @Query("SELECT * FROM workout_logs WHERE weekNumber = :weekNumber")
    fun getLogsForWeek(weekNumber: Int): Flow<List<WorkoutLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutLog(log: WorkoutLogEntity)

    @Query("SELECT * FROM workout_logs ORDER BY timestamp DESC")
    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>>

    @Delete
    suspend fun deleteWorkoutLog(log: WorkoutLogEntity)

    @Update
    suspend fun updateWorkoutLog(log: WorkoutLogEntity)

    @Query("SELECT * FROM workout_logs WHERE id = :id LIMIT 1")
    suspend fun getWorkoutLogById(id: Long): WorkoutLogEntity?

    /** The newest logs strictly older than [before], for the "previous" column of a session. */
    @Query("SELECT * FROM workout_logs WHERE timestamp < :before ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentWorkoutLogs(before: Long, limit: Int): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs WHERE syncPending = 1 ORDER BY timestamp ASC")
    suspend fun getPendingSyncLogs(): List<WorkoutLogEntity>

    @Query("UPDATE workout_logs SET syncPending = :pending WHERE clientId = :clientId")
    suspend fun setSyncPending(clientId: String, pending: Boolean)

    @Query("SELECT * FROM user_progress LIMIT 1")
    suspend fun getProgress(): UserProgressEntity?

    @Update
    suspend fun updateProgress(progress: UserProgressEntity)

    @Query("DELETE FROM user_progress")
    suspend fun clearAllProgress()

    @Query("DELETE FROM workout_logs")
    suspend fun clearAllLogs()
}
