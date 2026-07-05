package com.example.hybrid_ai_app.coach.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A single prior turn in the conversation (Gemini roles: "user" | "model")
@Serializable
data class ChatMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

// Request payload matching
@Serializable
data class ChatRequest(
    @SerialName("message") val message: String,
    // Compact summary of the user's active workout plan (null when no plan yet)
    @SerialName("plan_context") val planContext: String? = null,
    // Prior conversation turns so the coach can answer follow-ups
    @SerialName("history") val history: List<ChatMessageDto> = emptyList()
)

// Response payload matching your Node.js
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