package com.example.hybrid_ai_app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.hybrid_ai_app.core.data.local.converter.WorkoutLogConverters
import com.example.hybrid_ai_app.core.data.local.converter.WorkoutPlanConverters
import com.example.hybrid_ai_app.core.data.local.dao.ActiveRunDao
import com.example.hybrid_ai_app.core.data.local.dao.ActiveWorkoutDao
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunEntity
import com.example.hybrid_ai_app.core.data.local.entity.ActiveRunPointEntity
import com.example.hybrid_ai_app.core.data.local.entity.ActiveWorkoutEntity
import com.example.hybrid_ai_app.core.data.local.entity.UserProgressEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutLogEntity
import com.example.hybrid_ai_app.core.data.local.entity.WorkoutPlanEntity

@Database(
    entities = [
        WorkoutPlanEntity::class,
        UserProgressEntity::class,
        WorkoutLogEntity::class,
        ActiveWorkoutEntity::class,
        ActiveRunEntity::class,
        ActiveRunPointEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(WorkoutPlanConverters::class, WorkoutLogConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workoutPlanDao(): WorkoutPlanDao

    abstract fun progressDao(): ProgressDao

    abstract fun activeWorkoutDao(): ActiveWorkoutDao

    abstract fun activeRunDao(): ActiveRunDao
}
