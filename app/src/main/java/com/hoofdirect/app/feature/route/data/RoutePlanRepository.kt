package com.hoofdirect.app.feature.route.data

import com.hoofdirect.app.core.database.dao.AppointmentDao
import com.hoofdirect.app.core.database.dao.ClientDao
import com.hoofdirect.app.core.database.dao.RoutePlanDao
import com.hoofdirect.app.core.database.dao.UserDao
import com.hoofdirect.app.core.database.entity.RoutePlanEntity
import com.hoofdirect.app.core.route.RouteOptimizer
import com.hoofdirect.app.core.route.model.OptimizedRoute
import com.hoofdirect.app.core.route.model.RouteException
import com.hoofdirect.app.core.route.model.RoutePlan
import com.hoofdirect.app.core.route.model.RouteSavings
import com.hoofdirect.app.core.route.model.RouteStats
import com.hoofdirect.app.core.route.model.RouteStop
import com.hoofdirect.app.core.route.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for route plan operations.
 * Follows offline-first pattern - reads from local DB, writes optimized routes.
 */
@Singleton
class RoutePlanRepository @Inject constructor(
    private val routePlanDao: RoutePlanDao,
    private val appointmentDao: AppointmentDao,
    private val clientDao: ClientDao,
    private val userDao: UserDao,
    private val routeOptimizer: RouteOptimizer
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get cached route plan for a specific date.
     */
    fun getRouteForDate(userId: String, date: LocalDate): Flow<RoutePlan?> {
        return routePlanDao.getRouteForDate(userId, date).map { entity ->
            entity?.toDomain()
        }
    }

    /**
     * Get cached route plan once (not reactive).
     */
    suspend fun getRouteForDateOnce(userId: String, date: LocalDate): RoutePlan? {
        return routePlanDao.getRouteForDateOnce(userId, date)?.toDomain()
    }

    /**
     * Check if a route exists for the given date.
     */
    suspend fun hasRouteForDate(userId: String, date: LocalDate): Boolean {
        return routePlanDao.hasRouteForDate(userId, date) > 0
    }

    /**
     * Get total appointment count for a date (regardless of location data).
     */
    suspend fun getAppointmentCountForDate(userId: String, date: LocalDate): Int {
        return appointmentDao.getAppointmentsForDateOnce(userId, date).size
    }

    /**
     * Get appointments for a date that can be optimized.
     * Returns waypoints with location data.
     */
    suspend fun getWaypointsForDate(userId: String, date: LocalDate): List<Waypoint> {
        val appointments = appointmentDao.getAppointmentsForDateOnce(userId, date)

        return appointments.mapNotNull { appointment ->
            // Get client for location data
            val client = clientDao.getClientByIdOnce(appointment.clientId) ?: return@mapNotNull null

            // Skip appointments without valid locations
            val lat = appointment.latitude ?: client.latitude ?: return@mapNotNull null
            val lng = appointment.longitude ?: client.longitude ?: return@mapNotNull null

            if (lat == 0.0 || lng == 0.0) return@mapNotNull null

            Waypoint(
                appointmentId = appointment.id,
                clientId = client.id,
                latitude = lat,
                longitude = lng,
                clientName = "${client.firstName} ${client.lastName}".trim(),
                clientBusinessName = client.businessName,
                address = buildAddress(client),
                durationMinutes = appointment.durationMinutes ?: 45,
                isLocked = false,
                lockedPosition = null
            )
        }
    }

    /**
     * Get user's home location for route start/end.
     */
    suspend fun getUserHomeLocation(userId: String): Pair<Double, Double>? {
        val user = userDao.getUserByIdOnce(userId) ?: return null
        val lat = user.homeLatitude ?: return null
        val lng = user.homeLongitude ?: return null
        if (lat == 0.0 || lng == 0.0) return null
        return Pair(lat, lng)
    }

    /**
     * Optimize route for the given date.
     */
    suspend fun optimizeRoute(
        userId: String,
        date: LocalDate,
        lockedStops: Map<String, Int> = emptyMap(),
        startTime: LocalTime = LocalTime.of(8, 0)
    ): Result<OptimizedRoute> {
        // Get waypoints
        val waypoints = getWaypointsForDate(userId, date)
        if (waypoints.isEmpty()) {
            return Result.failure(RouteException.NoValidLocations)
        }

        // Get user's home location
        val homeLocation = getUserHomeLocation(userId)
            ?: return Result.failure(RouteException.NoHomeLocation)

        // Get user's address name
        val user = userDao.getUserByIdOnce(userId)
        val homeName = user?.address?.takeIf { it.isNotBlank() } ?: "Home"

        return routeOptimizer.optimizeRoute(
            userId = userId,
            date = date,
            waypoints = waypoints,
            startLat = homeLocation.first,
            startLng = homeLocation.second,
            startName = homeName,
            endLat = homeLocation.first, // Return to home by default
            endLng = homeLocation.second,
            endName = homeName,
            lockedStops = lockedStops,
            startTime = startTime
        )
    }

    /**
     * Update route after manual reordering.
     */
    suspend fun updateRouteStops(
        routeId: String,
        stops: List<RouteStop>,
        isManuallyReordered: Boolean = true
    ) {
        val stopsJson = json.encodeToString(RouteStop.serializer().list, stops)
        routePlanDao.updateStops(
            id = routeId,
            stops = stopsJson,
            isManuallyReordered = isManuallyReordered,
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    /**
     * Delete route for a specific date.
     */
    suspend fun deleteRouteForDate(userId: String, date: LocalDate) {
        routePlanDao.deleteForDate(userId, date)
    }

    /**
     * Build full address string from client entity.
     */
    private fun buildAddress(client: com.hoofdirect.app.core.database.entity.ClientEntity): String {
        return listOfNotNull(
            client.address,
            client.city,
            client.state,
            client.zipCode
        ).filter { it.isNotBlank() }.joinToString(", ")
    }

    /**
     * Convert database entity to domain model.
     */
    private fun RoutePlanEntity.toDomain(): RoutePlan {
        val stopsList = try {
            json.decodeFromString<List<RouteStop>>(stops)
        } catch (e: Exception) {
            emptyList()
        }

        return RoutePlan(
            id = id,
            userId = userId,
            date = date,
            startLatitude = startLat,
            startLongitude = startLng,
            startLocationName = startName,
            endLatitude = endLat,
            endLongitude = endLng,
            endLocationName = endName,
            stops = stopsList,
            polylinePoints = polylinePoints,
            totalDistanceMiles = totalDistanceMiles,
            totalDriveMinutes = totalDriveMinutes,
            originalDistanceMiles = originalDistanceMiles,
            originalDriveMinutes = originalDriveMinutes,
            isManuallyReordered = isManuallyReordered
        )
    }
}

// Extension to get list serializer
private val <T> kotlinx.serialization.KSerializer<T>.list: kotlinx.serialization.KSerializer<List<T>>
    get() = kotlinx.serialization.builtins.ListSerializer(this)
