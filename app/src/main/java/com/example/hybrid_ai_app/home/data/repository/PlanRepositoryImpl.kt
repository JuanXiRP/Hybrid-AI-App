package com.example.hybrid_ai_app.home.data.repository

import com.example.hybrid_ai_app.home.data.mapper.toDomain
import com.example.hybrid_ai_app.home.data.remote.PlanApiService
import com.example.hybrid_ai_app.home.domain.model.ActivePlan
import com.example.hybrid_ai_app.home.domain.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class PlanRepositoryImpl @Inject constructor(
    private val apiService: PlanApiService
) : PlanRepository {

    override suspend fun getActivePlan(token: String): Result<ActivePlan> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getActivePlan("Bearer $token")
                val planDto = response.data

                if (response.success && planDto != null) {
                    Result.success(planDto.toDomain())
                } else {
                    Result.failure(Exception("No active plan data returned from network"))
                }
            } catch (e: HttpException) {
                Result.failure(Exception("Server returned code ${e.code()}: ${e.message()}"))
            } catch (e: IOException) {
                Result.failure(Exception("Network failure. Check internet connectivity."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}