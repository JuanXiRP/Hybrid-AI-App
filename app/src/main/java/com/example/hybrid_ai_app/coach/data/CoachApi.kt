package com.example.hybrid_ai_app.coach.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CoachApi {
    // Executes the HTTP POST request to your Node.js backend
    @POST("api/ai/chat")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>

    /**
     * The stored conversation, oldest turn first.
     *
     * Only the most recent page is requested: the screen renders a transcript, not an archive.
     * The endpoint also paginates backwards with a `before` cursor, which is what a future
     * scroll-up would use.
     */
    @GET("api/ai/chat/history")
    suspend fun getHistory(@Query("limit") limit: Int): Response<ChatHistoryResponse>
}
