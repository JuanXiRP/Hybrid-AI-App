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
    val gpsPath: List<LatLngDto> = emptyList(),
    // Plan markers: which planned session this log closed. Optional on the wire (both are
    // omitted when null, since NetworkJson leaves encodeDefaults off), because the backend
    // accepts logs from clients that predate them. They are what lets the AI coach say which
    // sessions are left this week instead of inferring it from how many logs exist.
    val weekNumber: Int? = null,
    val dayIndex: Int? = null,
    // True for a session the athlete added on top of the plan. It still names the week and day it
    // was done on, but the backend never counts it as closing that plan day. Sent only when true.
    val isExtra: Boolean? = null,
)

@Serializable
data class LatLngDto(
    val lat: Double,
    val lng: Double,
)
