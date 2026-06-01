package com.example.hybrid_ai_app.core.data.local.entity

import kotlinx.serialization.Serializable

@Serializable
data class LoggedExerciseEntity(
    val name: String,
    val sets: String,
    val reps: String,
    val weight: String, // Holds the real-world performance input from the TextField
    val rpe: String
)