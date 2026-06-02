package com.example.hybrid_ai_app.core.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutRunDto(
    val userId: String,
    val distance: Double,
    val duration: Int,
    val targetPace: Int,
    val actualPace: Int,
    val elevationGain: Double = 0.0,
    val rpe: Int,
    val gpsPath: List<LatLngDto> = emptyList()
)

@Serializable
data class LatLngDto(
    val lat: Double,
    val lng: Double
)