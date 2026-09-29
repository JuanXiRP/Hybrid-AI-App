package com.example.hybrid_ai_app.core.di

import android.content.Context
import androidx.room.Room
import com.example.hybrid_ai_app.core.data.local.AppDatabase
import com.example.hybrid_ai_app.core.data.local.MIGRATION_3_4
import com.example.hybrid_ai_app.core.data.local.dao.ActiveWorkoutDao
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao
import com.example.hybrid_ai_app.core.data.local.dao.WorkoutPlanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "hybrid_ai_app_db",
    )
        .addMigrations(MIGRATION_3_4)
        // A safety net only: a version with no registered migration wipes the cache instead of
        // crashing. Every real schema change must ship a migration, because this drops the user's
        // plan, progress and logs silently.
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideWorkoutPlanDao(database: AppDatabase): WorkoutPlanDao = database.workoutPlanDao()

    @Provides
    @Singleton
    fun provideProgressDao(database: AppDatabase): ProgressDao = database.progressDao()

    @Provides
    @Singleton
    fun provideActiveWorkoutDao(database: AppDatabase): ActiveWorkoutDao = database.activeWorkoutDao()
}
