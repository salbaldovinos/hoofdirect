package com.hoofdirect.app.core.route

import com.hoofdirect.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result from Google Routes API optimization.
 */
data class OptimizationResult(
    val waypointOrder: List<Int>,
    val legDurations: List<Int>, // seconds
    val legDistances: List<Double>, // meters
    val totalDurationSeconds: Int,
    val totalDistanceMeters: Double,
    val encodedPolyline: String?
)

/**
 * Service for calling Google Routes API for route optimization.
 */
@Singleton
class GoogleRoutesService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val baseUrl = "https://routes.googleapis.com/directions/v2:computeRoutes"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compute optimal route with waypoint reordering.
     *
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param destinationLat Destination latitude
     * @param destinationLng Destination longitude
     * @param waypoints List of waypoint coordinates (lat, lng pairs)
     * @param departureTime When the route starts
     * @return OptimizationResult with optimized waypoint order and route details
     */
    suspend fun computeOptimalRoute(
        originLat: Double,
        originLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        waypoints: List<Pair<Double, Double>>,
        departureTime: LocalDateTime
    ): Result<OptimizationResult> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                putJsonObject("origin") {
                    putJsonObject("location") {
                        putJsonObject("latLng") {
                            put("latitude", originLat)
                            put("longitude", originLng)
                        }
                    }
                }
                putJsonObject("destination") {
                    putJsonObject("location") {
                        putJsonObject("latLng") {
                            put("latitude", destinationLat)
                            put("longitude", destinationLng)
                        }
                    }
                }
                putJsonArray("intermediates") {
                    waypoints.forEach { (lat, lng) ->
                        add(buildJsonObject {
                            putJsonObject("location") {
                                putJsonObject("latLng") {
                                    put("latitude", lat)
                                    put("longitude", lng)
                                }
                            }
                        })
                    }
                }
                put("travelMode", "DRIVE")
                put("optimizeWaypointOrder", true)
                put("departureTime", departureTime.toInstant(ZoneOffset.UTC).toString())
                put("routingPreference", "TRAFFIC_AWARE")
                put("computeAlternativeRoutes", false)
                put("languageCode", "en-US")
                put("units", "IMPERIAL")
            }

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Goog-Api-Key", BuildConfig.GOOGLE_MAPS_API_KEY)
                .addHeader(
                    "X-Goog-FieldMask",
                    "routes.optimizedIntermediateWaypointIndex," +
                            "routes.legs.duration," +
                            "routes.legs.distanceMeters," +
                            "routes.polyline.encodedPolyline," +
                            "routes.duration," +
                            "routes.distanceMeters"
                )
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    RouteApiException("Routes API error: ${response.code} - $errorBody")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(RouteApiException("Empty response"))

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val routes = jsonResponse["routes"]?.jsonArray

            if (routes.isNullOrEmpty()) {
                return@withContext Result.failure(RouteApiException("No route found"))
            }

            val route = routes.first().jsonObject

            // Parse optimized waypoint order
            val waypointOrder = route["optimizedIntermediateWaypointIndex"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.int }
                ?: waypoints.indices.toList()

            // Parse leg details
            val legs = route["legs"]?.jsonArray ?: emptyList()
            val legDurations = legs.map { leg ->
                val durationStr = leg.jsonObject["duration"]?.jsonPrimitive?.content ?: "0s"
                durationStr.removeSuffix("s").toIntOrNull() ?: 0
            }
            val legDistances = legs.map { leg ->
                leg.jsonObject["distanceMeters"]?.jsonPrimitive?.double ?: 0.0
            }

            // Parse totals
            val totalDurationStr = route["duration"]?.jsonPrimitive?.content ?: "0s"
            val totalDurationSeconds = totalDurationStr.removeSuffix("s").toIntOrNull() ?: 0
            val totalDistanceMeters = route["distanceMeters"]?.jsonPrimitive?.double ?: 0.0

            // Parse polyline
            val encodedPolyline = route["polyline"]
                ?.jsonObject
                ?.get("encodedPolyline")
                ?.jsonPrimitive
                ?.content

            Result.success(
                OptimizationResult(
                    waypointOrder = waypointOrder,
                    legDurations = legDurations,
                    legDistances = legDistances,
                    totalDurationSeconds = totalDurationSeconds,
                    totalDistanceMeters = totalDistanceMeters,
                    encodedPolyline = encodedPolyline
                )
            )
        } catch (e: Exception) {
            Result.failure(RouteApiException("Route optimization failed: ${e.message}"))
        }
    }

    /**
     * Compute route without waypoint optimization (for calculating original stats).
     */
    suspend fun computeRoute(
        originLat: Double,
        originLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        waypoints: List<Pair<Double, Double>>
    ): Result<OptimizationResult> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonObject {
                putJsonObject("origin") {
                    putJsonObject("location") {
                        putJsonObject("latLng") {
                            put("latitude", originLat)
                            put("longitude", originLng)
                        }
                    }
                }
                putJsonObject("destination") {
                    putJsonObject("location") {
                        putJsonObject("latLng") {
                            put("latitude", destinationLat)
                            put("longitude", destinationLng)
                        }
                    }
                }
                putJsonArray("intermediates") {
                    waypoints.forEach { (lat, lng) ->
                        add(buildJsonObject {
                            putJsonObject("location") {
                                putJsonObject("latLng") {
                                    put("latitude", lat)
                                    put("longitude", lng)
                                }
                            }
                        })
                    }
                }
                put("travelMode", "DRIVE")
                put("optimizeWaypointOrder", false) // Keep original order
                put("routingPreference", "TRAFFIC_AWARE")
                put("computeAlternativeRoutes", false)
                put("languageCode", "en-US")
                put("units", "IMPERIAL")
            }

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Goog-Api-Key", BuildConfig.GOOGLE_MAPS_API_KEY)
                .addHeader(
                    "X-Goog-FieldMask",
                    "routes.legs.duration," +
                            "routes.legs.distanceMeters," +
                            "routes.duration," +
                            "routes.distanceMeters"
                )
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    RouteApiException("Routes API error: ${response.code}")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(RouteApiException("Empty response"))

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val routes = jsonResponse["routes"]?.jsonArray

            if (routes.isNullOrEmpty()) {
                return@withContext Result.failure(RouteApiException("No route found"))
            }

            val route = routes.first().jsonObject
            val legs = route["legs"]?.jsonArray ?: emptyList()

            val legDurations = legs.map { leg ->
                val durationStr = leg.jsonObject["duration"]?.jsonPrimitive?.content ?: "0s"
                durationStr.removeSuffix("s").toIntOrNull() ?: 0
            }
            val legDistances = legs.map { leg ->
                leg.jsonObject["distanceMeters"]?.jsonPrimitive?.double ?: 0.0
            }

            val totalDurationStr = route["duration"]?.jsonPrimitive?.content ?: "0s"
            val totalDurationSeconds = totalDurationStr.removeSuffix("s").toIntOrNull() ?: 0
            val totalDistanceMeters = route["distanceMeters"]?.jsonPrimitive?.double ?: 0.0

            Result.success(
                OptimizationResult(
                    waypointOrder = waypoints.indices.toList(),
                    legDurations = legDurations,
                    legDistances = legDistances,
                    totalDurationSeconds = totalDurationSeconds,
                    totalDistanceMeters = totalDistanceMeters,
                    encodedPolyline = null
                )
            )
        } catch (e: Exception) {
            Result.failure(RouteApiException("Route calculation failed: ${e.message}"))
        }
    }
}

class RouteApiException(message: String) : Exception(message)
