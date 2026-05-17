package com.example.hybrid_ai_app.onboarding.data.remote.dto

// DTO representing the exact JSON structure expected by your Mongoose schema
data class ProfileUpdateRequest(
    val age: Int,
    val weight: Double,
    val height: Double,
    val sex: String,
    val goal: String,
    val fitnessLevel: String,
    val daysAvailable: Int,
    val planDuration: Int,
    val injuries: List<String>
)