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
     * The stored conversation, oldest turn first, or a failure.
     *
     * Returned as [ChatMessageDto] — the same `role` / `content` shape the send path already uses
     * to describe a turn — so the role-to-sender mapping stays in the one place that owns it, the
     * ViewModel, instead of being written once per direction.
     *
     * A backend that does not serve this endpoint yet answers 404, which surfaces as a failure and
     * leaves the screen exactly as it behaved before: greeting, no transcript.
     */
    suspend fun loadHistory(limit: Int = DEFAULT_HISTORY_LIMIT): Result<List<ChatMessageDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHistory(limit)
            val body = response.body()

            when {
                response.isSuccessful && body != null -> Result.success(
                    body.data?.messages.orEmpty().map { turn ->
                        ChatMessageDto(role = turn.role, content = turn.content)
                    },
                )

                else -> Result.failure(
                    response.premiumRequiredOrNull()
                        ?: Exception("Coach history error: HTTP ${response.code()}"),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    private companion object {
        // One screenful of conversation. Matches the backend's own default page size.
        const val DEFAULT_HISTORY_LIMIT = 50
    }
}
