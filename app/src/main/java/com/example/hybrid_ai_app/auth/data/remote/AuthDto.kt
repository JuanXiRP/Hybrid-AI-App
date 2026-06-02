package com.example.hybrid_ai_app.auth.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Request body for Login
@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)

// Request body for Register
@Serializable
data class RegisterRequest(
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    // Add default values for mandatory backend fields just to create the account
    @SerialName("age") val age: Int = 18,
    @SerialName("weight") val weight: Double = 70.0,
    @SerialName("height") val height: Double = 170.0,
    @SerialName("sex") val sex: String = "other",
    @SerialName("goal") val goal: String = "both",
    @SerialName("fitnessLevel") val fitnessLevel: String = "beginner",
    @SerialName("daysAvailable") val daysAvailable: Int = 3,
    @SerialName("planDuration") val planDuration: Int = 8
)

@Serializable
data class AuthResponse(
    @SerialName("success") val success: Boolean,
    // Provide explicit null defaults to avoid crashes if the backend omits these fields
    @SerialName("token") val token: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("has_completed_onboarding") val hasCompletedOnboarding: Boolean = false
)