package com.example.hybrid_ai_app.coach.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Request payload matching { "message": "..." }
@Serializable
data class ChatRequest(
    @SerialName("message") val message: String
)

// Response payload matching your Node.js response structure
@Serializable
data class ChatResponse(
    @SerialName("success") val success: Boolean,
    // Defaults to null to prevent crashes if the server omits them
    @SerialName("data") val data: ChatData? = null,
    @SerialName("message") val errorMessage: String? = null
)

@Serializable
data class ChatData(
    @SerialName("reply") val reply: String,
    @SerialName("timestamp") val timestamp: String? = null
)