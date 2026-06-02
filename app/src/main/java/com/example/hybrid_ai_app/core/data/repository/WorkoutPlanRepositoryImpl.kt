// src/main/java/com/example/hybrid_ai_app/core/data/repository/WorkoutPlanRepositoryImpl.kt
package com.example.hybrid_ai_app.core.data.repository

import androidx.room.withTransaction
import com.example.hybrid_ai_app.core.data.local.AppDatabase
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import com.example.hybrid_ai_app.core.data.remote.UserApi
import com.example.hybrid_ai_app.core.data.remote.dto.StrengthExerciseDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutRunDto
import com.example.hybrid_ai_app.core.data.remote.dto.WorkoutStrengthDto
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

class WorkoutPlanRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val planDao: WorkoutPlanDao,
    private val progressDao: ProgressDao,
    private val api: UserApi
) : WorkoutPlanRepository {

    override fun getActivePlan(): Flow<WorkoutPlanEntity?> = planDao.getActivePlan()

    override fun getUserProgress(): Flow<UserProgressEntity?> = progressDao.getUserProgress()

    override fun getLogsForWeek(weekNumber: Int): Flow<List<WorkoutLogEntity>> =
        progressDao.getLogsForWeek(weekNumber)

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
                isCompleted = true
            )
            progressDao.insertWorkoutLog(newLog)
        }
    }

    override suspend fun completeWorkout(
        log: WorkoutLogEntity,
        nextProgress: UserProgressEntity,
        workoutType: String,
        dayName: String
    ) {
        //Persistencia local en Room
        database.withTransaction {
            progressDao.insertWorkoutLog(log)
            progressDao.insertOrUpdateProgress(nextProgress)
        }

        // remote synchronization
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (workoutType == "strength") {
                    val strengthPayload = WorkoutStrengthDto(
                        userId = null, // Backend extracts from JWT
                        routineType = dayName,
                        exercises = log.loggedExercises.map { entity ->
                            StrengthExerciseDto(
                                exerciseName = entity.name,
                                sets = entity.sets.toIntOrNull() ?: 1,
                                reps = entity.reps.toIntOrNull() ?: 1,
                                targetWeight = 0.0,
                                actualWeight = entity.weight.toDoubleOrNull() ?: 0.0,
                                targetRpe = entity.rpe.toIntOrNull() ?: 8,
                                actualRpe = entity.rpe.toIntOrNull() ?: 8
                            )
                        }
                    )

                    val response = api.syncStrengthWorkout(strengthPayload)
                    if (response.isSuccessful) android.util.Log.d("MONGO_SYNC", " ÉXITO: Entreno de FUERZA guardado en MongoDB")
                    else android.util.Log.e("MONGO_SYNC", " ERROR: ${response.errorBody()?.string()}")

                } else if (workoutType == "cardio" || workoutType == "run") {

                    // Extraemos el RPE si el usuario lo introdujo, o mandamos 8 por defecto
                    val rpeValue = log.loggedExercises.firstOrNull()?.rpe?.toIntOrNull() ?: 8

                    val runPayload = WorkoutRunDto(
                        userId = "dummy",
                        distance = 0.0,
                        duration = 0,
                        targetPace = 0,
                        actualPace = 0,
                        elevationGain = 0.0,
                        rpe = rpeValue,
                        gpsPath = emptyList()
                    )

                    val response = api.syncRunWorkout(runPayload)
                    if (response.isSuccessful) android.util.Log.d("MONGO_SYNC", " ÉXITO: Entreno de CARRERA guardado en MongoDB")
                    else android.util.Log.e("MONGO_SYNC", " ERROR: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MONGO_SYNC", " ERROR de Red/Código", e)
            }
        }
    }
    override suspend fun clearActivePlanAndProgress() {
        // withTransaction guarantees all tables are cleared simultaneously
        database.withTransaction {
            planDao.clearPlan()          // Uses your existing method in WorkoutPlanDao
            progressDao.clearAllProgress() // Uses the new method
            progressDao.clearAllLogs()     // Uses the new method
        }
    }
}