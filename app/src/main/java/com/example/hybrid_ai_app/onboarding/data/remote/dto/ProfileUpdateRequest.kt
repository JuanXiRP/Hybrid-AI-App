package com.example.hybrid_ai_app.onboarding.data.remote.dto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class ProfileUpdateRequest(
    val age: Int,
    val weight: Double,
    val height: Double,
    val sex: String,
    val goal: String,
    val fitnessLevel: String,
    val daysAvailable: Int,
    val planDuration: Int,
    val injuries: List<String>,
    // ISO yyyy-MM-dd start date of the user's last menstrual period.
    // Only sent for female users; omitted from the JSON otherwise (kotlinx skips defaults).
    @SerialName("last_period_date") val lastPeriodDate: String? = null
)