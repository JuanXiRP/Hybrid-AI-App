package com.example.hybrid_ai_app.coach.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val api: CoachApi
) {
    suspend fun sendMessage(
        message: String,
        planContext: String?,
        history: List<ChatMessageDto>
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ChatRequest(
                    message = message,
                    planContext = planContext,
                    history = history
                )
                val response = api.sendMessage(request)

                if (response.isSuccessful) {
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