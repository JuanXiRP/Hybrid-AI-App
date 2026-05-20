package com.example.hybrid_ai_app.core.di

import com.example.hybrid_ai_app.home.data.remote.PlanApiService
import com.example.hybrid_ai_app.home.data.repository.PlanRepositoryImpl
import com.example.hybrid_ai_app.home.domain.repository.PlanRepository
import com.example.hybrid_ai_app.home.domain.usecase.GetActivePlanUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeModule {

    @Provides
    @Singleton
    fun providePlanApiService(retrofit: Retrofit): PlanApiService {
        return retrofit.create(PlanApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePlanRepository(apiService: PlanApiService): PlanRepository {
        return PlanRepositoryImpl(apiService)
    }

    @Provides
    @Singleton
    fun provideGetActivePlanUseCase(repository: PlanRepository): GetActivePlanUseCase {
        return GetActivePlanUseCase(repository)
    }
}