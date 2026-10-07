package com.example.hybrid_ai_app.tracking

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The live path of the run in progress, for the map: [LocationTrackingService] publishes every fix
 * here as it arrives, and restores the stored path after a restart.
 *
 * It is a mirror, not the source of truth: the run itself (its clock, its structure, every point)
 * is stored through `ActiveRunRepository`, and the run screen derives the clock and the phase from
 * that, so nothing on screen depends on this object surviving.
 */
object WorkoutLocationManager {
    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints: StateFlow<List<LatLng>> = _pathPoints.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    fun addPoint(point: LatLng) {
        _pathPoints.value = _pathPoints.value + point
    }

    fun setTrackingStatus(tracking: Boolean) {
        _isTracking.value = tracking
    }

    /** Replaces the path with the one stored for a run this process had not seen yet. */
    fun restore(points: List<LatLng>) {
        _pathPoints.value = points
    }

    fun clearAll() {
        _pathPoints.value = emptyList()
        _isTracking.value = false
    }
}
