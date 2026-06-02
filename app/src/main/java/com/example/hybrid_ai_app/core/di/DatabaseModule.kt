package com.example.hybrid_ai_app.core.di

import android.content.Context
import androidx.room.Room
import com.example.hybrid_ai_app.core.data.local.AppDatabase
import com.example.hybrid_ai_app.core.data.local.dao.ProgressDao // 🟢 Added missing import
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
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hybrid_ai_app_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideWorkoutPlanDao(database: AppDatabase): WorkoutPlanDao {
        return database.workoutPlanDao()
    }

    @Provides
    @Singleton
    fun provideProgressDao(database: AppDatabase): ProgressDao {
        return database.progressDao()
    }
}