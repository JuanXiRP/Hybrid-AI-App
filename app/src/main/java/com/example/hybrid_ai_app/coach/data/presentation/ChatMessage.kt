package com.example.hybrid_ai_app.coach.data.presentation
// Defines who sent the message
enum class MessageSender {
    USER,
    COACH
}

// Data class representing a single message
data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender
)