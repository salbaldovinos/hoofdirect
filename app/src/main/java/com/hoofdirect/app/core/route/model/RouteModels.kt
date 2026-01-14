package com.hoofdirect.app.core.route.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * A single stop in an optimized route.
 */
@Serializable
data class RouteStop(
    val appointmentId: String,
    val clientId: String,
    val clientName: String,
    val clientBusinessName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val order: Int,
    val estimatedArrivalMinutes: Int, // Minutes from route start
    val appointmentDurationMinutes: Int,
    val driveMinutesFromPrevious: Int,
    val distanceMilesFromPrevious: Double,
    val isLocked: Boolean = false,
    val isCompleted: Boolean = false
)

/**
 * Statistics for a route (before or after optimization).
 */
data class RouteStats(
    val totalMiles: Double,
    val totalMinutes: Int
)

/**
 * Savings achieved through route optimization.
 */
data class RouteSavings(
    val milesSaved: Double,
    val minutesSaved: Int
) {
    val hasSavings: Boolean get() = milesSaved > 0 || minutesSaved > 0
}

/**
 * Complete route plan with optimization details.
 */
data class RoutePlan(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val startLatitude: Double,
    val startLongitude: Double,
    val startLocationName: String,
    val endLatitude: Double,
    val endLongitude: Double,
    val endLocationName: String,
    val stops: List<RouteStop>,
    val polylinePoints: String? = null,
    val totalDistanceMiles: Double,
    val totalDriveMinutes: Int,
    val originalDistanceMiles: Double? = null,
    val originalDriveMinutes: Int? = null,
    val isManuallyReordered: Boolean = false
) {
    val lastLegDriveMinutes: Int
        get() = if (stops.isNotEmpty()) {
            // Estimate based on average speed
            ((lastLegDistanceMiles / 30.0) * 60).toInt()
        } else 0

    val lastLegDistanceMiles: Double
        get() = if (stops.isNotEmpty()) {
            // Calculate from last stop to end location
            val lastStop = stops.last()
            calculateDistance(
                lastStop.latitude, lastStop.longitude,
                endLatitude, endLongitude
            )
        } else 0.0

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        // Haversine formula for distance in miles
        val r = 3958.8 // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

/**
 * Result of route optimization including comparison stats.
 */
data class OptimizedRoute(
    val plan: RoutePlan,
    val originalStats: RouteStats,
    val savings: RouteSavings
)

/**
 * Waypoint for route optimization request.
 */
data class Waypoint(
    val appointmentId: String,
    val clientId: String,
    val latitude: Double,
    val longitude: Double,
    val clientName: String,
    val clientBusinessName: String?,
    val address: String,
    val durationMinutes: Int,
    val isLocked: Boolean = false,
    val lockedPosition: Int? = null
)

/**
 * Location option for start/end configuration.
 */
sealed class LocationOption {
    data object Home : LocationOption()
    data object CurrentLocation : LocationOption()
    data object LastStop : LocationOption()
    data class Custom(val address: String, val latitude: Double, val longitude: Double) : LocationOption()
}

/**
 * Exceptions for route operations.
 */
sealed class RouteException : Exception() {
    data object FeatureNotAvailable : RouteException() {
        private fun readResolve(): Any = FeatureNotAvailable
        override val message: String = "Route optimization requires a paid subscription"
    }

    data class TierLimitExceeded(
        val requested: Int,
        val allowed: Int
    ) : RouteException() {
        override val message: String = "Your plan allows $allowed stops, but you have $requested appointments"
    }

    data object NoValidLocations : RouteException() {
        private fun readResolve(): Any = NoValidLocations
        override val message: String = "No appointments with valid addresses found"
    }

    data object NoHomeLocation : RouteException() {
        private fun readResolve(): Any = NoHomeLocation
        override val message: String = "Please set your home/start location in Settings"
    }

    data class OptimizationFailed(override val message: String?) : RouteException()

    data object NetworkError : RouteException() {
        private fun readResolve(): Any = NetworkError
        override val message: String = "Route optimization requires internet connection"
    }
}
