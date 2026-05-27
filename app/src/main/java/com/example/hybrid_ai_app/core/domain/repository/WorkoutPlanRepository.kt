package com.example.hybrid_ai_app.core.domain.repository

import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

interface WorkoutPlanRepository {
    fun getActivePlan(): Flow<WorkoutPlanEntity?>
    fun getUserProgress(): Flow<UserProgressEntity?>
    fun getLogsForWeek(weekNumber: Int): Flow<List<WorkoutLogEntity>>
    suspend fun updateProgress(progress: UserProgressEntity)
    suspend fun completeWorkout(log: WorkoutLogEntity, nextProgress: UserProgressEntity)

    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>>
}