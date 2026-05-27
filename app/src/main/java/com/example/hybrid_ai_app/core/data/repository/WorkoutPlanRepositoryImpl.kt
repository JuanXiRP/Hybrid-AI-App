package com.example.hybrid_ai_app.core.data.repository

import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao // Adjust to your actual plan DAO name
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import androidx.room.withTransaction
import com.example.hybrid_ai_app.core.data.local.AppDatabase // Replace with your Room DB class
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WorkoutPlanRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val planDao: WorkoutPlanDao,
    private val progressDao: ProgressDao
) : WorkoutPlanRepository {

    override fun getActivePlan(): Flow<WorkoutPlanEntity?> = planDao.getActivePlan()

    override fun getUserProgress(): Flow<UserProgressEntity?> = progressDao.getUserProgress()

    override fun getLogsForWeek(weekNumber: Int): Flow<List<WorkoutLogEntity>> =
        progressDao.getLogsForWeek(weekNumber)

    override suspend fun updateProgress(progress: UserProgressEntity) {
        progressDao.insertOrUpdateProgress(progress)
    }

    override suspend fun completeWorkout(log: WorkoutLogEntity, nextProgress: UserProgressEntity) {
        // Atomic database operations using Room Transaction
        database.withTransaction {
            progressDao.insertWorkoutLog(log)
            progressDao.insertOrUpdateProgress(nextProgress)
        }
    }
    override fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>> = progressDao.getAllWorkoutLogs()
}