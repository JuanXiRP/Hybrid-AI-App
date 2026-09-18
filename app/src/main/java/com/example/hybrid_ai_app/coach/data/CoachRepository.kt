package com.example.hybrid_ai_app.coach.data

import com.example.hybrid_ai_app.core.data.remote.premiumRequiredOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val api: CoachApi,
) {
    /**
     * @return the coach's reply, or a failure. A spent daily quota arrives as a
     * `PremiumRequiredException`, distinguishable from a network error — previously both
     * collapsed into `null` and the UI blamed the connection.
     */
    suspend fun sendMessage(
        message: String,
        planContext: String?,
        history: List<ChatMessageDto>,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = ChatRequest(
                message = message,
                planContext = planContext,
                history = history,
            )
            val response = api.sendMessage(request)
            val reply = response.body()?.data?.reply

            when {
                response.isSuccessful && !reply.isNullOrBlank() -> Result.success(reply)
                else -> Result.failure(
                    response.premiumRequiredOrNull()
                        ?: Exception("Coach error: HTTP ${response.code()}"),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
