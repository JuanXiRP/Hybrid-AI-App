package com.example.hybrid_ai_app.core.di

import com.example.hybrid_ai_app.core.data.repository.UserRepositoryImpl
import com.example.hybrid_ai_app.core.data.repository.WorkoutPlanRepositoryImpl
import com.example.hybrid_ai_app.core.domain.repository.UserRepository
import com.example.hybrid_ai_app.core.domain.repository.WorkoutPlanRepository
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
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutPlanRepository(
        workoutPlanRepositoryImpl: WorkoutPlanRepositoryImpl
    ): WorkoutPlanRepository
}