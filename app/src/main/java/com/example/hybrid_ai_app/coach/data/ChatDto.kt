package com.example.hybrid_ai_app.coach.data

import com.google.gson.annotations.SerializedName

// Request payload matching { "message": "..." }
data class ChatRequest(
    @SerializedName("message") val message: String
)

// Response payload matching your Node.js response structure
data class ChatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: ChatData?,
    @SerializedName("message") val errorMessage: String?
)

data class ChatData(
    @SerializedName("reply") val reply: String,
    @SerializedName("timestamp") val timestamp: String
)