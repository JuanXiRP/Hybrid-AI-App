package com.example.hybrid_ai_app.tracking

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Singleton to bridge the Foreground Service and the Compose UI reactively
object WorkoutLocationManager {
    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints: StateFlow<List<LatLng>> = _pathPoints.asStateFlow()

    // 🟢 Tracks whether the workout is actively counting and positioning
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    // 🟢 Total elapsed time in seconds
    private val _elapsedTimeSec = MutableStateFlow(0L)
    val elapsedTimeSec: StateFlow<Long> = _elapsedTimeSec.asStateFlow()

    fun addPoint(point: LatLng) {
        _pathPoints.value = _pathPoints.value + point
    }

    fun setTrackingStatus(tracking: Boolean) {
        _isTracking.value = tracking
    }

    fun updateTime(seconds: Long) {
        _elapsedTimeSec.value = seconds
    }

    fun clearAll() {
        _pathPoints.value = emptyList()
        _isTracking.value = false
        _elapsedTimeSec.value = 0L
    }
}