package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val email: String,
    // Onboarding fields
    val age: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val sex: String? = null,
    val goal: String? = null,
    val fitnessLevel: String? = null,
    val daysAvailable: Int? = null,
    val planDuration: Int? = null,
    val injuries: List<String> = emptyList(),
    val isPremium: Boolean = false
)

@Serializable
data class UserProfileResponse(
    val success: Boolean,
    val data: UserDto
)
data class GoogleAuthRequest(val idToken: String)