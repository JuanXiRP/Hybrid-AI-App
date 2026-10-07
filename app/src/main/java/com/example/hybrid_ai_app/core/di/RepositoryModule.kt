package com.example.hybrid_ai_app.core.di

import com.example.hybrid_ai_app.core.data.repository.ActiveRunRepositoryImpl
import com.example.hybrid_ai_app.core.data.repository.ExerciseCatalogRepositoryImpl
import com.example.hybrid_ai_app.core.data.repository.UserRepositoryImpl
import com.example.hybrid_ai_app.core.data.repository.WorkoutPlanRepositoryImpl
import com.example.hybrid_ai_app.core.domain.repository.ActiveRunRepository
import com.example.hybrid_ai_app.core.domain.repository.ExerciseCatalogRepository
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
import com.example.hybrid_ai_app.home.data.repository.ActiveWorkoutRepositoryImpl
import com.example.hybrid_ai_app.home.domain.repository.ActiveWorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl,
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutPlanRepository(
        workoutPlanRepositoryImpl: WorkoutPlanRepositoryImpl,
    ): WorkoutPlanRepository

    @Binds
    @Singleton
    abstract fun bindExerciseCatalogRepository(
        exerciseCatalogRepositoryImpl: ExerciseCatalogRepositoryImpl,
    ): ExerciseCatalogRepository

    @Binds
    @Singleton
    abstract fun bindActiveWorkoutRepository(
        activeWorkoutRepositoryImpl: ActiveWorkoutRepositoryImpl,
    ): ActiveWorkoutRepository

    @Binds
    @Singleton
    abstract fun bindActiveRunRepository(
        activeRunRepositoryImpl: ActiveRunRepositoryImpl,
    ): ActiveRunRepository
}
