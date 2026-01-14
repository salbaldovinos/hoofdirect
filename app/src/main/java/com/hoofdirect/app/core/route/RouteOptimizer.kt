package com.hoofdirect.app.core.route

import com.hoofdirect.app.core.database.dao.RoutePlanDao
import com.hoofdirect.app.core.database.entity.RoutePlanEntity
import com.hoofdirect.app.core.network.NetworkMonitor
import com.hoofdirect.app.core.route.model.OptimizedRoute
import com.hoofdirect.app.core.route.model.RouteException
import com.hoofdirect.app.core.route.model.RoutePlan
import com.hoofdirect.app.core.route.model.RouteSavings
import com.hoofdirect.app.core.route.model.RouteStats
import com.hoofdirect.app.core.route.model.RouteStop
import com.hoofdirect.app.core.route.model.Waypoint
import com.hoofdirect.app.core.subscription.model.LimitCheckResult
import com.hoofdirect.app.core.subscription.UsageLimitsManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core route optimization logic.
 * Handles tier limits, locked stops, and coordinates with Google Routes API.
 */
@Singleton
class RouteOptimizer @Inject constructor(
    private val googleRoutesService: GoogleRoutesService,
    private val routePlanDao: RoutePlanDao,
    private val usageLimitsManager: UsageLimitsManager,
    private val networkMonitor: NetworkMonitor
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Optimize a route for the given appointments.
     *
     * @param userId Current user ID
     * @param date Date of the route
     * @param waypoints List of appointment waypoints
     * @param startLat Start location latitude
     * @param startLng Start location longitude
     * @param startName Name of start location (e.g., "Home")
     * @param endLat End location latitude
     * @param endLng End location longitude
     * @param endName Name of end location
     * @param lockedStops Map of appointmentId to locked position (1-indexed)
     * @param startTime Expected start time
     * @return Result with optimized route or error
     */
    suspend fun optimizeRoute(
        userId: String,
        date: LocalDate,
        waypoints: List<Waypoint>,
        startLat: Double,
        startLng: Double,
        startName: String,
        endLat: Double,
        endLng: Double,
        endName: String,
        lockedStops: Map<String, Int> = emptyMap(),
        startTime: LocalTime = LocalTime.of(8, 0)
    ): Result<OptimizedRoute> {
        // 1. Check network connectivity
        if (!networkMonitor.isCurrentlyOnline()) {
            return Result.failure(RouteException.NetworkError)
        }

        // 2. Check tier limits
        val limitCheck = usageLimitsManager.canUseRouteOptimization(waypoints.size)
        when (limitCheck) {
            is LimitCheckResult.FeatureNotAvailable -> {
                return Result.failure(RouteException.FeatureNotAvailable)
            }
            is LimitCheckResult.Blocked -> {
                return Result.failure(
                    RouteException.TierLimitExceeded(
                        requested = waypoints.size,
                        allowed = limitCheck.limit
                    )
                )
            }
            is LimitCheckResult.Allowed, is LimitCheckResult.Warning -> {
                // Proceed with optimization
            }
        }

        // 3. Validate waypoints
        if (waypoints.isEmpty()) {
            return Result.failure(RouteException.NoValidLocations)
        }

        val validWaypoints = waypoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        if (validWaypoints.isEmpty()) {
            return Result.failure(RouteException.NoValidLocations)
        }

        // 4. Separate locked and unlocked waypoints
        val (lockedWaypoints, unlockableWaypoints) = validWaypoints.partition {
            lockedStops.containsKey(it.appointmentId)
        }.let { (locked, unlocked) ->
            // Attach locked positions to waypoints
            val lockedWithPositions = locked.map { wp ->
                wp.copy(isLocked = true, lockedPosition = lockedStops[wp.appointmentId])
            }
            Pair(lockedWithPositions, unlocked)
        }

        return try {
            // 5. Calculate original route stats (unoptimized order)
            val originalStats = calculateOriginalStats(
                startLat, startLng,
                endLat, endLng,
                validWaypoints
            )

            // 6. Optimize with Google Routes API (only unlocked waypoints)
            val optimizationResult = if (unlockableWaypoints.isNotEmpty()) {
                googleRoutesService.computeOptimalRoute(
                    originLat = startLat,
                    originLng = startLng,
                    destinationLat = endLat,
                    destinationLng = endLng,
                    waypoints = unlockableWaypoints.map { Pair(it.latitude, it.longitude) },
                    departureTime = date.atTime(startTime)
                ).getOrThrow()
            } else {
                // All stops are locked - use original order
                OptimizationResult(
                    waypointOrder = emptyList(),
                    legDurations = emptyList(),
                    legDistances = emptyList(),
                    totalDurationSeconds = 0,
                    totalDistanceMeters = 0.0,
                    encodedPolyline = null
                )
            }

            // 7. Reorder unlocked waypoints based on optimization
            val reorderedUnlocked = if (unlockableWaypoints.isNotEmpty()) {
                optimizationResult.waypointOrder.map { index -> unlockableWaypoints[index] }
            } else {
                emptyList()
            }

            // 8. Merge locked stops back into optimized order
            val orderedWaypoints = mergeLockedStops(reorderedUnlocked, lockedWaypoints)

            // 9. Build route stops with ETAs
            val stops = buildRouteStops(
                waypoints = orderedWaypoints,
                legDurations = calculateLegDurations(
                    startLat, startLng,
                    orderedWaypoints,
                    endLat, endLng
                ),
                legDistances = calculateLegDistances(
                    startLat, startLng,
                    orderedWaypoints,
                    endLat, endLng
                ),
                startTime = startTime
            )

            // 10. Calculate totals
            val totalDistanceMiles = stops.sumOf { it.distanceMilesFromPrevious } +
                    calculateDistanceMiles(
                        stops.lastOrNull()?.latitude ?: startLat,
                        stops.lastOrNull()?.longitude ?: startLng,
                        endLat, endLng
                    )
            val totalDriveMinutes = stops.sumOf { it.driveMinutesFromPrevious } +
                    estimateDriveMinutes(
                        calculateDistanceMiles(
                            stops.lastOrNull()?.latitude ?: startLat,
                            stops.lastOrNull()?.longitude ?: startLng,
                            endLat, endLng
                        )
                    )

            // 11. Build route plan
            val routePlan = RoutePlan(
                id = UUID.randomUUID().toString(),
                userId = userId,
                date = date,
                startLatitude = startLat,
                startLongitude = startLng,
                startLocationName = startName,
                endLatitude = endLat,
                endLongitude = endLng,
                endLocationName = endName,
                stops = stops,
                polylinePoints = optimizationResult.encodedPolyline,
                totalDistanceMiles = totalDistanceMiles,
                totalDriveMinutes = totalDriveMinutes,
                originalDistanceMiles = originalStats.totalMiles,
                originalDriveMinutes = originalStats.totalMinutes,
                isManuallyReordered = false
            )

            // 12. Save to local database
            routePlanDao.upsert(routePlan.toEntity())

            // 13. Calculate savings
            val savings = RouteSavings(
                milesSaved = originalStats.totalMiles - totalDistanceMiles,
                minutesSaved = originalStats.totalMinutes - totalDriveMinutes
            )

            Result.success(
                OptimizedRoute(
                    plan = routePlan,
                    originalStats = originalStats,
                    savings = savings
                )
            )
        } catch (e: RouteApiException) {
            Result.failure(RouteException.OptimizationFailed(e.message))
        } catch (e: Exception) {
            Result.failure(RouteException.OptimizationFailed(e.message))
        }
    }

    /**
     * Merge locked stops into the optimized order at their specified positions.
     */
    private fun mergeLockedStops(
        optimizedOrder: List<Waypoint>,
        lockedWaypoints: List<Waypoint>
    ): List<Waypoint> {
        if (lockedWaypoints.isEmpty()) return optimizedOrder

        val result = optimizedOrder.toMutableList()

        // Insert locked stops at their specified positions (sorted by position)
        lockedWaypoints
            .sortedBy { it.lockedPosition ?: Int.MAX_VALUE }
            .forEach { locked ->
                val position = ((locked.lockedPosition ?: 1) - 1).coerceIn(0, result.size)
                result.add(position, locked)
            }

        return result
    }

    /**
     * Build route stops with calculated ETAs.
     */
    private fun buildRouteStops(
        waypoints: List<Waypoint>,
        legDurations: List<Int>,
        legDistances: List<Double>,
        startTime: LocalTime
    ): List<RouteStop> {
        var cumulativeMinutes = 0

        return waypoints.mapIndexed { index, waypoint ->
            val driveMinutes = legDurations.getOrElse(index) { 0 }
            val distanceMiles = legDistances.getOrElse(index) { 0.0 }

            cumulativeMinutes += driveMinutes
            val arrivalMinutes = cumulativeMinutes

            // Add appointment duration for next stop calculation
            cumulativeMinutes += waypoint.durationMinutes

            RouteStop(
                appointmentId = waypoint.appointmentId,
                clientId = waypoint.clientId,
                clientName = waypoint.clientName,
                clientBusinessName = waypoint.clientBusinessName,
                latitude = waypoint.latitude,
                longitude = waypoint.longitude,
                address = waypoint.address,
                order = index + 1,
                estimatedArrivalMinutes = arrivalMinutes,
                appointmentDurationMinutes = waypoint.durationMinutes,
                driveMinutesFromPrevious = driveMinutes,
                distanceMilesFromPrevious = distanceMiles,
                isLocked = waypoint.isLocked,
                isCompleted = false
            )
        }
    }

    /**
     * Calculate original (unoptimized) route statistics.
     */
    private fun calculateOriginalStats(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        waypoints: List<Waypoint>
    ): RouteStats {
        var totalDistance = 0.0
        var prevLat = startLat
        var prevLng = startLng

        for (waypoint in waypoints) {
            totalDistance += calculateDistanceMiles(prevLat, prevLng, waypoint.latitude, waypoint.longitude)
            prevLat = waypoint.latitude
            prevLng = waypoint.longitude
        }

        // Add distance to end location
        totalDistance += calculateDistanceMiles(prevLat, prevLng, endLat, endLng)

        // Estimate time at 30 mph average
        val totalMinutes = (totalDistance * 2).toInt()

        return RouteStats(
            totalMiles = totalDistance,
            totalMinutes = totalMinutes
        )
    }

    /**
     * Calculate leg durations (simplified estimation when API data not available).
     */
    private fun calculateLegDurations(
        startLat: Double,
        startLng: Double,
        waypoints: List<Waypoint>,
        endLat: Double,
        endLng: Double
    ): List<Int> {
        val durations = mutableListOf<Int>()
        var prevLat = startLat
        var prevLng = startLng

        for (waypoint in waypoints) {
            val distance = calculateDistanceMiles(prevLat, prevLng, waypoint.latitude, waypoint.longitude)
            durations.add(estimateDriveMinutes(distance))
            prevLat = waypoint.latitude
            prevLng = waypoint.longitude
        }

        return durations
    }

    /**
     * Calculate leg distances.
     */
    private fun calculateLegDistances(
        startLat: Double,
        startLng: Double,
        waypoints: List<Waypoint>,
        endLat: Double,
        endLng: Double
    ): List<Double> {
        val distances = mutableListOf<Double>()
        var prevLat = startLat
        var prevLng = startLng

        for (waypoint in waypoints) {
            distances.add(calculateDistanceMiles(prevLat, prevLng, waypoint.latitude, waypoint.longitude))
            prevLat = waypoint.latitude
            prevLng = waypoint.longitude
        }

        return distances
    }

    /**
     * Calculate distance between two points using Haversine formula.
     */
    private fun calculateDistanceMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 3958.8 // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * Estimate drive time based on distance (assumes 30 mph average).
     */
    private fun estimateDriveMinutes(distanceMiles: Double): Int {
        return (distanceMiles * 2).toInt() // 30 mph = 0.5 miles per minute
    }

    /**
     * Convert RoutePlan to database entity.
     */
    private fun RoutePlan.toEntity(): RoutePlanEntity {
        return RoutePlanEntity(
            id = id,
            userId = userId,
            date = date,
            startLat = startLatitude,
            startLng = startLongitude,
            startName = startLocationName,
            endLat = endLatitude,
            endLng = endLongitude,
            endName = endLocationName,
            stops = json.encodeToString(stops),
            polylinePoints = polylinePoints,
            totalDistanceMiles = totalDistanceMiles,
            totalDriveMinutes = totalDriveMinutes,
            originalDistanceMiles = originalDistanceMiles,
            originalDriveMinutes = originalDriveMinutes,
            optimizedAt = Instant.now(),
            isManuallyReordered = isManuallyReordered,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
