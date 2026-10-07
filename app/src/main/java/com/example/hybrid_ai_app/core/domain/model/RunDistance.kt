package com.example.hybrid_ai_app.core.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0088

/**
 * The length of a tracked path, in kilometres: the sum of the great-circle distances between
 * consecutive fixes (haversine). Pure Kotlin, so it gives the same answer on the JVM as on a
 * device, unlike `Location.distanceBetween`.
 */
fun List<RunPoint>.distanceKm(): Double = zipWithNext { from, to -> haversineKm(from, to) }.sum()

private fun haversineKm(from: RunPoint, to: RunPoint): Double {
    val lat1 = Math.toRadians(from.lat)
    val lat2 = Math.toRadians(to.lat)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(to.lng - from.lng)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
