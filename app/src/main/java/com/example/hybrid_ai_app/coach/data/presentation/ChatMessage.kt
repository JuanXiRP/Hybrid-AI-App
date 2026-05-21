package com.example.hybrid_ai_app.coach.data.presentation
// Defines who sent the message to render the bubble correctly (left or right)
enum class MessageSender {
    USER,
    COACH
}

// Data class representing a single message in the chat UI
data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender
)