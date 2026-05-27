package com.example.hybrid_ai_app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {

    @Query("SELECT * FROM workout_plans WHERE id = 'active_plan' LIMIT 1")
    fun getActivePlan(): Flow<WorkoutPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity)

    @Query("DELETE FROM workout_plans")
    suspend fun clearPlan()
}