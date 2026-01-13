# PRD-010: Route Optimization

**Priority**: P0  
**Phase**: 3 - Route Intelligence  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Optimize daily appointment routes to minimize travel time and distance—the core differentiator that no competitor offers.

### Business Value
- Saves 5+ hours/week in drive time
- Reduces fuel costs by 20%+
- First-to-market advantage
- Primary reason farriers choose Hoof Direct

### Success Metrics
| Metric | Target |
|--------|--------|
| Time savings per route | > 20% |
| User-reported weekly savings | > 3 hours |
| Optimization completion time | < 5 seconds |
| Drive time estimate accuracy | Within 15% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-010-01 | Farrier | Optimize my daily route | I save drive time | P0 |
| US-010-02 | Farrier | See before/after comparison | I see the savings | P0 |
| US-010-03 | Farrier | Manually reorder if needed | I have control | P1 |
| US-010-04 | Farrier | Lock certain stops in place | Some clients have fixed times | P1 |
| US-010-05 | Farrier | Start route navigation | I follow the optimized order | P0 |
| US-010-06 | Farrier | See estimated arrival times | I plan my day | P0 |

---

## Functional Requirements

### FR-010-01: Route Optimization
- Input: Day's appointments + start/end locations
- Output: Optimized stop order with ETAs
- Algorithm: Google Routes API with waypoint optimization
- Consider: Traffic conditions (if available)
- Recalculate on: Appointment changes

### FR-010-02: Start/End Location Options
- Start from: Home (default), Current Location, Custom
- End at: Home (default), Last Stop, Custom
- Save preference per route

### FR-010-03: Before/After Comparison
```
Before Optimization:
  Total: 68.4 miles, 2h 45m drive
  
After Optimization:
  Total: 47.3 miles, 1h 52m drive
  
Savings: 21.1 miles, 53 minutes
```

### FR-010-04: Manual Reorder
- Drag-and-drop stops
- Recalculates times after reorder
- "Reset to Optimized" option

### FR-010-05: Lock Stops
- Lock stop at specific position
- Optimization works around locked stops
- Visual indicator for locked stops

### FR-010-06: Start Route Mode
- Step-by-step navigation
- Current stop highlighted
- "Navigate" button for each stop
- "Complete & Next" action
- Auto-advance to next stop
- Track actual arrival times

### FR-010-07: Tier Limits
```kotlin
Route Optimization Limits:
- Free: View route (no optimization)
- Solo: 8 stops/day
- Growing: 15 stops/day
- Multi: Unlimited
```

---

## Technical Implementation

```kotlin
// RouteOptimizer.kt
class RouteOptimizer @Inject constructor(
    private val googleRoutesService: GoogleRoutesService,
    private val routePlanDao: RoutePlanDao,
    private val usageLimits: UsageLimitsManager
) {
    suspend fun optimizeRoute(
        date: LocalDate,
        appointments: List<AppointmentWithDetails>,
        startLocation: LatLng,
        endLocation: LatLng,
        lockedStops: Map<String, Int> = emptyMap() // appointmentId -> position
    ): Result<OptimizedRoute> {
        // Check tier limits
        val maxStops = usageLimits.getMaxRouteStops()
        if (maxStops == 0) {
            return Result.failure(RouteException.FeatureNotAvailable)
        }
        if (appointments.size > maxStops) {
            return Result.failure(RouteException.TierLimitExceeded(
                requested = appointments.size,
                allowed = maxStops
            ))
        }
        
        // Build waypoints
        val waypoints = appointments.mapNotNull { apt ->
            val lat = apt.latitudeOverride ?: apt.client.latitude
            val lng = apt.longitudeOverride ?: apt.client.longitude
            if (lat != null && lng != null) {
                Waypoint(
                    appointmentId = apt.appointment.id,
                    location = LatLng(lat, lng),
                    clientName = apt.client.name,
                    isLocked = lockedStops.containsKey(apt.appointment.id),
                    lockedPosition = lockedStops[apt.appointment.id]
                )
            } else null
        }
        
        if (waypoints.isEmpty()) {
            return Result.failure(RouteException.NoValidLocations)
        }
        
        return try {
            // Calculate original order stats
            val originalStats = calculateRouteStats(
                startLocation,
                waypoints.map { it.location },
                endLocation
            )
            
            // Get optimized order from Google
            val optimizedOrder = googleRoutesService.computeOptimalRoute(
                origin = startLocation,
                destination = endLocation,
                waypoints = waypoints.filterNot { it.isLocked }.map { it.location },
                lockedWaypoints = waypoints.filter { it.isLocked }
                    .sortedBy { it.lockedPosition }
                    .map { it.location to it.lockedPosition!! }
            )
            
            // Build route plan
            val stops = buildRouteStops(
                waypoints,
                optimizedOrder,
                startLocation
            )
            
            val routePlan = RoutePlan(
                id = UUID.randomUUID().toString(),
                userId = getCurrentUserId(),
                date = date,
                startLocation = startLocation,
                endLocation = endLocation,
                stops = stops,
                totalDistanceMiles = optimizedOrder.distanceMeters / 1609.34,
                totalDriveMinutes = optimizedOrder.durationSeconds / 60,
                optimizedAt = Instant.now()
            )
            
            // Cache route plan
            routePlanDao.upsert(routePlan.toEntity())
            
            Result.success(OptimizedRoute(
                plan = routePlan,
                originalStats = originalStats,
                savings = RouteSavings(
                    milesSaved = originalStats.totalMiles - routePlan.totalDistanceMiles,
                    minutesSaved = originalStats.totalMinutes - routePlan.totalDriveMinutes
                )
            ))
        } catch (e: Exception) {
            Result.failure(RouteException.OptimizationFailed(e.message))
        }
    }
    
    private fun buildRouteStops(
        waypoints: List<Waypoint>,
        optimizedOrder: OptimizedRouteResponse,
        startLocation: LatLng
    ): List<RouteStop> {
        val orderedWaypoints = optimizedOrder.waypointOrder.map { index ->
            waypoints[index]
        }
        
        var currentTime = LocalTime.of(8, 0) // Default start time
        var previousLocation = startLocation
        
        return orderedWaypoints.mapIndexed { index, waypoint ->
            val driveMinutes = optimizedOrder.legDurations[index] / 60
            currentTime = currentTime.plusMinutes(driveMinutes.toLong())
            
            val stop = RouteStop(
                appointmentId = waypoint.appointmentId,
                clientName = waypoint.clientName,
                location = waypoint.location,
                order = index + 1,
                estimatedArrival = currentTime,
                driveMinutesFromPrevious = driveMinutes,
                isLocked = waypoint.isLocked
            )
            
            // Add appointment duration for next calculation
            currentTime = currentTime.plusMinutes(getAppointmentDuration(waypoint.appointmentId))
            previousLocation = waypoint.location
            
            stop
        }
    }
}

// GoogleRoutesService.kt
class GoogleRoutesService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun computeOptimalRoute(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng>,
        lockedWaypoints: List<Pair<LatLng, Int>> = emptyList()
    ): OptimizedRouteResponse = withContext(Dispatchers.IO) {
        
        val requestBody = buildJsonObject {
            put("origin", buildWaypoint(origin))
            put("destination", buildWaypoint(destination))
            putJsonArray("intermediates") {
                waypoints.forEach { wp ->
                    add(buildWaypoint(wp))
                }
            }
            put("travelMode", "DRIVE")
            put("optimizeWaypointOrder", true)
            put("routingPreference", "TRAFFIC_AWARE")
            put("computeAlternativeRoutes", false)
        }
        
        val request = Request.Builder()
            .url("https://routes.googleapis.com/directions/v2:computeRoutes")
            .addHeader("X-Goog-Api-Key", BuildConfig.GOOGLE_MAPS_API_KEY)
            .addHeader("X-Goog-FieldMask", 
                "routes.optimizedIntermediateWaypointIndex," +
                "routes.duration,routes.distanceMeters," +
                "routes.legs.duration,routes.legs.distanceMeters," +
                "routes.polyline.encodedPolyline")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = okHttpClient.newCall(request).await()
        val responseBody = response.body?.string() 
            ?: throw RouteException.OptimizationFailed("Empty response")
        
        parseRouteResponse(responseBody)
    }
    
    private fun buildWaypoint(location: LatLng) = buildJsonObject {
        putJsonObject("location") {
            putJsonObject("latLng") {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
            }
        }
    }
}

// RouteViewModel.kt
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeOptimizer: RouteOptimizer,
    private val appointmentRepository: AppointmentRepository,
    private val navigationHelper: NavigationHelper
) : ViewModel() {
    
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _routeState = MutableStateFlow<RouteState>(RouteState.Loading)
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()
    
    private val _lockedStops = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    fun optimizeRoute() {
        viewModelScope.launch {
            _routeState.value = RouteState.Optimizing
            
            val appointments = appointmentRepository
                .getAppointmentsForDate(_selectedDate.value)
                .first()
            
            val result = routeOptimizer.optimizeRoute(
                date = _selectedDate.value,
                appointments = appointments,
                startLocation = getStartLocation(),
                endLocation = getEndLocation(),
                lockedStops = _lockedStops.value
            )
            
            _routeState.value = result.fold(
                onSuccess = { RouteState.Optimized(it) },
                onFailure = { RouteState.Error(it.message ?: "Optimization failed") }
            )
        }
    }
    
    fun reorderStop(fromIndex: Int, toIndex: Int) {
        val current = (_routeState.value as? RouteState.Optimized)?.route ?: return
        val newStops = current.plan.stops.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }.mapIndexed { index, stop -> stop.copy(order = index + 1) }
        
        // Recalculate times
        viewModelScope.launch {
            val updatedPlan = recalculateTimes(current.plan.copy(stops = newStops))
            _routeState.value = RouteState.Optimized(current.copy(plan = updatedPlan))
        }
    }
    
    fun lockStop(appointmentId: String, position: Int) {
        _lockedStops.value = _lockedStops.value + (appointmentId to position)
    }
    
    fun unlockStop(appointmentId: String) {
        _lockedStops.value = _lockedStops.value - appointmentId
    }
    
    fun startNavigation(stop: RouteStop) {
        navigationHelper.navigateTo(stop.location, stop.clientName)
    }
}

sealed interface RouteState {
    data object Loading : RouteState
    data object Optimizing : RouteState
    data class Optimized(val route: OptimizedRoute) : RouteState
    data class Error(val message: String) : RouteState
}
```

---

## Data Model

```kotlin
@Entity(tableName = "route_plans")
data class RoutePlanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: LocalDate,
    @ColumnInfo(name = "start_location") val startLocation: String, // JSON
    @ColumnInfo(name = "end_location") val endLocation: String, // JSON
    val stops: String, // JSON array
    @ColumnInfo(name = "total_distance_miles") val totalDistanceMiles: Double,
    @ColumnInfo(name = "total_drive_minutes") val totalDriveMinutes: Int,
    @ColumnInfo(name = "optimized_at") val optimizedAt: Instant,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now()
)

data class OptimizedRoute(
    val plan: RoutePlan,
    val originalStats: RouteStats,
    val savings: RouteSavings
)

data class RoutePlan(
    val id: String,
    val date: LocalDate,
    val startLocation: LatLng,
    val endLocation: LatLng,
    val stops: List<RouteStop>,
    val totalDistanceMiles: Double,
    val totalDriveMinutes: Int,
    val optimizedAt: Instant
)

data class RouteStop(
    val appointmentId: String,
    val clientName: String,
    val location: LatLng,
    val order: Int,
    val estimatedArrival: LocalTime,
    val driveMinutesFromPrevious: Int,
    val isLocked: Boolean = false,
    val isCompleted: Boolean = false
)

data class RouteStats(
    val totalMiles: Double,
    val totalMinutes: Int
)

data class RouteSavings(
    val milesSaved: Double,
    val minutesSaved: Int
)

sealed class RouteException : Exception() {
    data object FeatureNotAvailable : RouteException()
    data class TierLimitExceeded(val requested: Int, val allowed: Int) : RouteException()
    data object NoValidLocations : RouteException()
    data class OptimizationFailed(override val message: String?) : RouteException()
}
```

---

## UI Specifications

### Route Optimization Screen
```
┌─────────────────────────────────────────┐
│ [←] Today's Route           [Optimize]  │
├─────────────────────────────────────────┤
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ SAVINGS                           │  │
│  │ 🎉 Save 21.1 miles & 53 minutes  │  │
│  │                                   │  │
│  │ Before: 68.4 mi, 2h 45m          │  │
│  │ After:  47.3 mi, 1h 52m          │  │
│  └───────────────────────────────────┘  │
│                                         │
│  Start: Home (123 Main St)             │
│  ─────────────────────────────────────  │
│                                         │
│  ≡ 1. Johnson Ranch          8:00 AM   │
│       15 min drive                      │
│       [🔒 Lock] [Navigate]             │
│                                         │
│  ≡ 2. Williams Farm         10:15 AM   │
│       12 min drive                      │
│       [🔒 Lock] [Navigate]             │
│                                         │
│  ≡ 3. Martinez Stables      11:45 AM   │
│       18 min drive                      │
│       [🔒 Lock] [Navigate]             │
│                                         │
│  ≡ 4. Oak Hill Equestrian    1:30 PM   │
│       25 min drive                      │
│       [🔒 Lock] [Navigate]             │
│                                         │
│  End: Home                              │
│       22 min drive                      │
│                                         │
│  ────────────────────────────────────   │
│  [Start Route]                          │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class RouteOptimizerTest {
    @Test
    fun `optimizeRoute respects tier limits`() = runTest {
        every { usageLimits.getMaxRouteStops() } returns 8
        
        val result = optimizer.optimizeRoute(
            date = LocalDate.now(),
            appointments = (1..10).map { createAppointment() },
            startLocation = LatLng(30.0, -97.0),
            endLocation = LatLng(30.0, -97.0)
        )
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RouteException.TierLimitExceeded)
    }
    
    @Test
    fun `optimizeRoute calculates savings correctly`() = runTest {
        val result = optimizer.optimizeRoute(...)
        val route = result.getOrThrow()
        
        assertEquals(
            route.originalStats.totalMiles - route.plan.totalDistanceMiles,
            route.savings.milesSaved,
            0.1
        )
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-010-01 | Route optimizes to minimize distance | Integration test |
| AC-010-02 | Before/after comparison shown | UI test |
| AC-010-03 | Drag reorder works | UI test |
| AC-010-04 | Locked stops respected | Unit test |
| AC-010-05 | Tier limits enforced | Unit test |
| AC-010-06 | Navigation handoff works | Manual test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | Required |
| PRD-009 (Maps) | Internal | Required |
| Google Routes API | External | Required |
| Subscription tier | PRD-017 | Required |

---

## Cost Considerations

| API | Cost | Monthly Estimate |
|-----|------|------------------|
| Routes API - Compute Routes | $5 per 1000 requests | ~$2.50 (500 optimizations) |
| Routes API - with traffic | +$5 per 1000 | ~$2.50 |
| **Total per active user** | | **~$5/month** |
