package com.example.hybrid_ai_app.auth.data.remote

import com.google.gson.annotations.SerializedName

// Request body for Login
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

// Request body for Register (You can send a dummy profile first, then update it in the Onboarding)
data class RegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    // Add default values for mandatory backend fields just to create the account
    @SerializedName("age") val age: Int = 18,
    @SerializedName("weight") val weight: Double = 70.0,
    @SerializedName("height") val height: Double = 170.0,
    @SerializedName("sex") val sex: String = "other",
    @SerializedName("goal") val goal: String = "both",
    @SerializedName("fitnessLevel") val fitnessLevel: String = "beginner",
    @SerializedName("daysAvailable") val daysAvailable: Int = 3,
    @SerializedName("planDuration") val planDuration: Int = 8
)

// The response your Node.js server sends back
data class AuthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String?,
    @SerializedName("message") val message: String?
)