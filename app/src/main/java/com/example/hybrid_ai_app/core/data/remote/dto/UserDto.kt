package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val email: String,
    // Onboarding fields (nullable because they might be empty right after registration)
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

// 🟢 Si tu backend de Node.js devuelve los datos envueltos en un "data" (ej: { "success": true, "data": { ... } })
// usa este wrapper en el UserApi. Si devuelve el usuario directamente, ignora esto.
@Serializable
data class UserProfileResponse(
    val success: Boolean,
    val data: UserDto
)