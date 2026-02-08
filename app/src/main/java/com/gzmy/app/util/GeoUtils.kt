package com.gzmy.app.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GeoUtils — Haversine distance and bearing calculations.
 * No external dependencies (pure math).
 */
object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Haversine distance between two lat/lng points in kilometers.
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Bearing (azimuth) from point 1 to point 2, in degrees (0-360, 0=North).
     */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val x = sin(dLon) * cos(lat2Rad)
        val y = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(x, y)).toFloat()
        return (bearing + 360) % 360
    }

    /**
     * Format distance as human-readable string.
     */
    fun formatDistance(km: Double): String {
        return when {
            km < 0.01 -> "Çok yakın!"
            km < 1.0 -> "${(km * 1000).roundToInt()} m"
            km < 10.0 -> String.format("%.1f km", km)
            km < 100.0 -> "${km.roundToInt()} km"
            else -> "${km.roundToInt()} km"
        }
    }

    /**
     * Compass direction from bearing angle.
     */
    fun bearingToDirection(bearing: Float): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "Kuzey"
            bearing < 67.5 -> "Kuzeydoğu"
            bearing < 112.5 -> "Doğu"
            bearing < 157.5 -> "Güneydoğu"
            bearing < 202.5 -> "Güney"
            bearing < 247.5 -> "Güneybatı"
            bearing < 292.5 -> "Batı"
            else -> "Kuzeybatı"
        }
    }
}
