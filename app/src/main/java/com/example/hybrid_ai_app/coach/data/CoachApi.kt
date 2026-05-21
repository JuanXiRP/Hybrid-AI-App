package com.example.hybrid_ai_app.coach.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface CoachApi {
    // Executes the HTTP POST request to your Node.js backend
    @POST("api/ai/chat")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>
}