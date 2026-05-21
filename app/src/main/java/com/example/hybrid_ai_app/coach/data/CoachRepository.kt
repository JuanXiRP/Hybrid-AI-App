package com.example.hybrid_ai_app.coach.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val api: CoachApi // Hilt provides the Retrofit instance
) {
    suspend fun sendMessage(message: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ChatRequest(message = message)
                val response = api.sendMessage(request)

                if (response.isSuccessful) {
                    // Extract the reply from the nested JSON structure
                    response.body()?.data?.reply
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}