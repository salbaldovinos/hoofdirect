# FRD-010: Route Optimization

**PRD Reference**: PRD-010-route-optimization.md  
**Priority**: P0  
**Phase**: 3 - Route Intelligence  
**Estimated Duration**: 2 weeks

---

## 1. Overview

### 1.1 Purpose

This document specifies the complete functional requirements for route optimization—the core differentiating feature that calculates optimal stop order to minimize travel time and distance for daily appointments.

### 1.2 Scope

This FRD covers:
- Route optimization algorithm and API integration
- Before/after comparison display
- Manual stop reordering via drag-and-drop
- Stop locking mechanism for fixed time constraints
- Start/end location configuration
- Route navigation mode
- Tier-based feature limits
- ETA calculation and tracking

### 1.3 Dependencies

| Dependency | FRD | Description |
|------------|-----|-------------|
| Appointments | FRD-005 | Appointment data for route stops |
| Maps Integration | FRD-009 | Map visualization and navigation |
| Usage Limits | FRD-017 | Tier-based stop limits |
| Offline Architecture | FRD-002 | Cached route plans |
| Google Routes API | External | Optimization algorithm |

---

## 2. Route Optimization Algorithm

### 2.1 Optimization Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    ROUTE OPTIMIZATION FLOW                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Load day's appointments                                     │
│     ↓                                                           │
│  2. Check tier limits (max stops)                               │
│     ↓                                                           │
│  3. Extract geocoded locations                                  │
│     ↓                                                           │
│  4. Identify locked stops (fixed positions)                     │
│     ↓                                                           │
│  5. Calculate original route stats (before)                     │
│     ↓                                                           │
│  6. Call Google Routes API with waypoint optimization           │
│     ↓                                                           │
│  7. Apply locked stops to optimized order                       │
│     ↓                                                           │
│  8. Calculate ETAs based on stop order + durations              │
│     ↓                                                           │
│  9. Calculate savings (before - after)                          │
│     ↓                                                           │
│  10. Cache route plan locally                                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 RouteOptimizer

**File**: `core/route/RouteOptimizer.kt`

```kotlin
@Singleton
class RouteOptimizer @Inject constructor(
    private val googleRoutesService: GoogleRoutesService,
    private val routePlanDao: RoutePlanDao,
    private val usageLimitsManager: UsageLimitsManager,
    private val userRepository: UserRepository
) {
    suspend fun optimizeRoute(
        date: LocalDate,
        appointments: List<AppointmentWithDetails>,
        startLocation: LatLng,
        endLocation: LatLng,
        lockedStops: Map<String, Int> = emptyMap(), // appointmentId -> position (1-indexed)
        startTime: LocalTime = LocalTime.of(8, 0)
    ): Result<OptimizedRoute> {
        // 1. Check tier limits
        val maxStops = usageLimitsManager.getMaxRouteStops()
        if (maxStops == 0) {
            return Result.failure(RouteException.FeatureNotAvailable)
        }
        if (appointments.size > maxStops) {
            return Result.failure(RouteException.TierLimitExceeded(
                requested = appointments.size,
                allowed = maxStops
            ))
        }
        
        // 2. Build waypoints with valid locations
        val waypoints = appointments.mapNotNull { apt ->
            val location = apt.getLocation() ?: return@mapNotNull null
            Waypoint(
                appointmentId = apt.appointment.id,
                location = location,
                clientName = "${apt.client.firstName} ${apt.client.lastName}",
                clientBusinessName = apt.client.businessName,
                address = apt.getAddress(),
                durationMinutes = apt.appointment.durationMinutes,
                isLocked = lockedStops.containsKey(apt.appointment.id),
                lockedPosition = lockedStops[apt.appointment.id]
            )
        }
        
        if (waypoints.isEmpty()) {
            return Result.failure(RouteException.NoValidLocations)
        }
        
        // 3. Calculate original (unoptimized) stats
        val originalStats = calculateRouteStats(
            startLocation = startLocation,
            waypoints = waypoints.map { it.location },
            endLocation = endLocation
        )
        
        // 4. Optimize route via Google Routes API
        return try {
            val (lockedWaypoints, unlockableWaypoints) = waypoints.partition { it.isLocked }
            
            val optimizationResult = googleRoutesService.computeOptimalRoute(
                origin = startLocation,
                destination = endLocation,
                waypoints = unlockableWaypoints.map { it.location },
                departureTime = date.atTime(startTime)
            )
            
            // 5. Merge locked stops back into optimized order
            val orderedWaypoints = mergeLockedStops(
                optimizedOrder = optimizationResult.waypointOrder.map { unlockableWaypoints[it] },
                lockedWaypoints = lockedWaypoints
            )
            
            // 6. Build route stops with ETAs
            val stops = buildRouteStops(
                waypoints = orderedWaypoints,
                legDurations = optimizationResult.legDurations,
                legDistances = optimizationResult.legDistances,
                startTime = startTime
            )
            
            // 7. Build route plan
            val routePlan = RoutePlan(
                id = UUID.randomUUID().toString(),
                userId = userRepository.getCurrentUserId(),
                date = date,
                startLocation = startLocation,
                startLocationName = "Home", // Or user setting
                endLocation = endLocation,
                endLocationName = "Home",
                stops = stops,
                totalDistanceMiles = optimizationResult.totalDistanceMeters / 1609.34,
                totalDriveMinutes = optimizationResult.totalDurationSeconds / 60,
                polylinePoints = optimizationResult.encodedPolyline?.let { 
                    PolyUtil.decode(it) 
                } ?: emptyList(),
                optimizedAt = Instant.now()
            )
            
            // 8. Cache route plan
            routePlanDao.upsert(routePlan.toEntity())
            
            // 9. Calculate and return savings
            val savings = RouteSavings(
                milesSaved = originalStats.totalMiles - routePlan.totalDistanceMiles,
                minutesSaved = originalStats.totalMinutes - routePlan.totalDriveMinutes
            )
            
            Result.success(OptimizedRoute(
                plan = routePlan,
                originalStats = originalStats,
                savings = savings
            ))
        } catch (e: Exception) {
            Result.failure(RouteException.OptimizationFailed(e.message))
        }
    }
    
    private fun mergeLockedStops(
        optimizedOrder: List<Waypoint>,
        lockedWaypoints: List<Waypoint>
    ): List<Waypoint> {
        val result = optimizedOrder.toMutableList()
        
        // Insert locked stops at their specified positions
        lockedWaypoints
            .sortedBy { it.lockedPosition }
            .forEach { locked ->
                val position = (locked.lockedPosition!! - 1).coerceIn(0, result.size)
                result.add(position, locked)
            }
        
        return result
    }
    
    private fun buildRouteStops(
        waypoints: List<Waypoint>,
        legDurations: List<Int>, // seconds
        legDistances: List<Double>, // meters
        startTime: LocalTime
    ): List<RouteStop> {
        var currentTime = startTime
        
        return waypoints.mapIndexed { index, waypoint ->
            val driveMinutes = if (index < legDurations.size) {
                legDurations[index] / 60
            } else 0
            
            val distanceMiles = if (index < legDistances.size) {
                legDistances[index] / 1609.34
            } else 0.0
            
            currentTime = currentTime.plusMinutes(driveMinutes.toLong())
            val arrivalTime = currentTime
            
            // Add appointment duration for next stop calculation
            currentTime = currentTime.plusMinutes(waypoint.durationMinutes.toLong())
            
            RouteStop(
                appointmentId = waypoint.appointmentId,
                clientName = waypoint.clientName,
                clientBusinessName = waypoint.clientBusinessName,
                location = waypoint.location,
                address = waypoint.address,
                order = index + 1,
                estimatedArrival = arrivalTime,
                appointmentDurationMinutes = waypoint.durationMinutes,
                driveMinutesFromPrevious = driveMinutes,
                distanceMilesFromPrevious = distanceMiles,
                isLocked = waypoint.isLocked,
                isCompleted = false
            )
        }
    }
    
    private suspend fun calculateRouteStats(
        startLocation: LatLng,
        waypoints: List<LatLng>,
        endLocation: LatLng
    ): RouteStats {
        // Calculate simple point-to-point distances in original order
        var totalDistance = 0.0
        var previousLocation = startLocation
        
        for (waypoint in waypoints) {
            totalDistance += SphericalUtil.computeDistanceBetween(previousLocation, waypoint)
            previousLocation = waypoint
        }
        totalDistance += SphericalUtil.computeDistanceBetween(previousLocation, endLocation)
        
        // Estimate time at 30 mph average
        val totalMinutes = ((totalDistance / 1609.34) * 2).toInt()
        
        return RouteStats(
            totalMiles = totalDistance / 1609.34,
            totalMinutes = totalMinutes
        )
    }
}
```

### 2.3 Google Routes API Integration

**File**: `core/network/GoogleRoutesService.kt`

```kotlin
@Singleton
class GoogleRoutesService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("googleRoutesApiKey") private val apiKey: String
) {
    private val baseUrl = "https://routes.googleapis.com/directions/v2:computeRoutes"
    
    suspend fun computeOptimalRoute(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng>,
        departureTime: LocalDateTime
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("origin", locationJson(origin))
            put("destination", locationJson(destination))
            putJsonArray("intermediates") {
                waypoints.forEach { add(locationJson(it)) }
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
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", 
                "routes.optimizedIntermediateWaypointIndex," +
                "routes.legs.duration," +
                "routes.legs.distanceMeters," +
                "routes.polyline.encodedPolyline," +
                "routes.duration," +
                "routes.distanceMeters"
            )
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = okHttpClient.newCall(request).await()
        if (!response.isSuccessful) {
            throw RouteApiException("Routes API error: ${response.code}")
        }
        
        val json = Json.parseToJsonElement(response.body!!.string()).jsonObject
        val route = json["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw RouteApiException("No route found")
        
        OptimizationResult(
            waypointOrder = route["optimizedIntermediateWaypointIndex"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.int }
                ?: (waypoints.indices).toList(),
            legDurations = route["legs"]
                ?.jsonArray
                ?.map { it.jsonObject["duration"]?.jsonPrimitive?.content?.removeSuffix("s")?.toInt() ?: 0 }
                ?: emptyList(),
            legDistances = route["legs"]
                ?.jsonArray
                ?.map { it.jsonObject["distanceMeters"]?.jsonPrimitive?.double ?: 0.0 }
                ?: emptyList(),
            totalDurationSeconds = route["duration"]
                ?.jsonPrimitive?.content?.removeSuffix("s")?.toInt() ?: 0,
            totalDistanceMeters = route["distanceMeters"]?.jsonPrimitive?.double ?: 0.0,
            encodedPolyline = route["polyline"]?.jsonObject?.get("encodedPolyline")?.jsonPrimitive?.content
        )
    }
    
    private fun locationJson(latLng: LatLng) = buildJsonObject {
        putJsonObject("location") {
            putJsonObject("latLng") {
                put("latitude", latLng.latitude)
                put("longitude", latLng.longitude)
            }
        }
    }
}

data class OptimizationResult(
    val waypointOrder: List<Int>,
    val legDurations: List<Int>,
    val legDistances: List<Double>,
    val totalDurationSeconds: Int,
    val totalDistanceMeters: Double,
    val encodedPolyline: String?
)
```

---

## 3. Tier-Based Limits

### 3.1 Stop Limits by Tier

| Tier | Max Stops per Route | Description |
|------|---------------------|-------------|
| Free | 0 (view only) | Can view route but not optimize |
| Solo | 8 | Typical solo farrier day |
| Professional | 15 | Busy practitioners |
| Business | Unlimited | Multi-employee operations |

### 3.2 Limit Enforcement

**File**: `core/subscription/UsageLimitsManager.kt`

```kotlin
fun getMaxRouteStops(): Int {
    return when (getCurrentTier()) {
        SubscriptionTier.FREE_TRIAL -> 0
        SubscriptionTier.SOLO -> 8
        SubscriptionTier.PROFESSIONAL -> 15
        SubscriptionTier.BUSINESS -> Int.MAX_VALUE
    }
}

fun canOptimizeRoute(stopCount: Int): RouteOptimizationEligibility {
    val maxStops = getMaxRouteStops()
    return when {
        maxStops == 0 -> RouteOptimizationEligibility.UpgradeRequired
        stopCount <= maxStops -> RouteOptimizationEligibility.Allowed
        else -> RouteOptimizationEligibility.TooManyStops(
            requested = stopCount,
            allowed = maxStops
        )
    }
}

sealed class RouteOptimizationEligibility {
    object Allowed : RouteOptimizationEligibility()
    object UpgradeRequired : RouteOptimizationEligibility()
    data class TooManyStops(val requested: Int, val allowed: Int) : RouteOptimizationEligibility()
}
```

### 3.3 Limit Exceeded UI

When user has more stops than tier allows:

```
┌─────────────────────────────────────────┐
│  ⚠️ Too Many Stops                      │
│                                         │
│  Your plan allows 8 stops per route,    │
│  but you have 12 appointments today.    │
│                                         │
│  Options:                               │
│  • Reschedule 4 appointments            │
│  • Upgrade to Professional (15 stops)   │
│                                         │
│  [Upgrade]  [View Appointments]         │
└─────────────────────────────────────────┘
```

---

## 4. Route Screen

### 4.1 Screen Layout

**Route**: `/route?date={date}` or `/route` (defaults to today)

```
┌─────────────────────────────────────────┐
│ [←] Today's Route           [Optimize]  │
├─────────────────────────────────────────┤
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ SAVINGS                           │  │
│  │ 🎉 Save 21.1 miles & 53 minutes   │  │
│  │                                   │  │
│  │ Before: 68.4 mi, 2h 45m           │  │
│  │ After:  47.3 mi, 1h 52m           │  │
│  └───────────────────────────────────┘  │
│                                         │
│  Start: Home (123 Main St)         [✎] │
│  ─────────────────────────────────────  │
│                                         │
│  ≡ 1. Johnson Ranch          8:00 AM   │
│       🔒 Locked                         │
│       15 min drive · 12 mi              │
│                                         │
│  ≡ 2. Williams Farm         10:15 AM   │
│       12 min drive · 8 mi               │
│                                         │
│  ≡ 3. Martinez Stables      11:45 AM   │
│       18 min drive · 14 mi              │
│                                         │
│  ≡ 4. Oak Hill Equestrian    1:30 PM   │
│       25 min drive · 18 mi              │
│                                         │
│  ─────────────────────────────────────  │
│  End: Home                         [✎] │
│       22 min drive · 15 mi              │
│                                         │
├─────────────────────────────────────────┤
│  [View on Map]    [Start Route]         │
└─────────────────────────────────────────┘
```

### 4.2 RouteScreen Composable

**File**: `feature/route/ui/RouteScreen.kt`

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RouteScreen(
    viewModel: RouteViewModel = hiltViewModel(),
    onNavigateToMap: (LocalDate) -> Unit,
    onNavigateToAppointment: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dragState = rememberDragState()
    
    Scaffold(
        topBar = {
            RouteTopBar(
                date = uiState.date,
                onBack = onNavigateBack,
                onDateChange = viewModel::setDate,
                onOptimize = viewModel::optimizeRoute,
                isOptimizing = uiState.isOptimizing,
                canOptimize = uiState.canOptimize
            )
        },
        bottomBar = {
            RouteBottomBar(
                onViewMap = { onNavigateToMap(uiState.date) },
                onStartRoute = viewModel::startRouteMode,
                enabled = uiState.routePlan != null
            )
        }
    ) { padding ->
        when (val state = uiState.routeState) {
            is RouteState.Loading -> LoadingIndicator()
            is RouteState.NoAppointments -> NoAppointmentsMessage(uiState.date)
            is RouteState.NotOptimized -> UnoptimizedRouteView(
                appointments = uiState.appointments,
                onOptimize = viewModel::optimizeRoute
            )
            is RouteState.Optimizing -> OptimizingIndicator()
            is RouteState.Optimized -> OptimizedRouteContent(
                route = state.route,
                onReorder = viewModel::reorderStop,
                onLockToggle = viewModel::toggleLock,
                onStopClick = onNavigateToAppointment,
                onStartLocationChange = viewModel::setStartLocation,
                onEndLocationChange = viewModel::setEndLocation,
                modifier = Modifier.padding(padding)
            )
            is RouteState.Error -> ErrorMessage(
                message = state.message,
                onRetry = viewModel::optimizeRoute
            )
        }
    }
}

@Composable
private fun OptimizedRouteContent(
    route: OptimizedRoute,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onLockToggle: (appointmentId: String) -> Unit,
    onStopClick: (String) -> Unit,
    onStartLocationChange: () -> Unit,
    onEndLocationChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to -> onReorder(from.index - 2, to.index - 2) } // Offset for header items
    )
    
    LazyColumn(
        state = reorderState.listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Savings card
        item(key = "savings") {
            SavingsCard(
                savings = route.savings,
                originalStats = route.originalStats,
                optimizedStats = RouteStats(
                    totalMiles = route.plan.totalDistanceMiles,
                    totalMinutes = route.plan.totalDriveMinutes
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Start location
        item(key = "start") {
            StartEndLocationItem(
                label = "Start",
                locationName = route.plan.startLocationName,
                onClick = onStartLocationChange
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Route stops (draggable)
        itemsIndexed(
            items = route.plan.stops,
            key = { _, stop -> stop.appointmentId }
        ) { index, stop ->
            ReorderableItem(
                state = reorderState,
                key = stop.appointmentId
            ) { isDragging ->
                RouteStopCard(
                    stop = stop,
                    isDragging = isDragging,
                    onLockToggle = { onLockToggle(stop.appointmentId) },
                    onClick = { onStopClick(stop.appointmentId) },
                    modifier = Modifier.detectReorderAfterLongPress(reorderState)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // End location
        item(key = "end") {
            StartEndLocationItem(
                label = "End",
                locationName = route.plan.endLocationName,
                driveInfo = "${route.plan.lastLegDriveMinutes} min · " +
                           "${route.plan.lastLegDistanceMiles.formatMiles()}",
                onClick = onEndLocationChange
            )
        }
    }
}
```

### 4.3 RouteViewModel

**File**: `feature/route/ui/RouteViewModel.kt`

```kotlin
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val routeOptimizer: RouteOptimizer,
    private val routePlanRepository: RoutePlanRepository,
    private val usageLimitsManager: UsageLimitsManager,
    private val userPreferencesManager: UserPreferencesManager,
    private val navigationHelper: NavigationHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _date = MutableStateFlow(
        savedStateHandle.get<String>("date")?.let { LocalDate.parse(it) } 
            ?: LocalDate.now()
    )
    val date: StateFlow<LocalDate> = _date.asStateFlow()
    
    private val _routeState = MutableStateFlow<RouteState>(RouteState.Loading)
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()
    
    private val _lockedStops = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    private val _isRouteMode = MutableStateFlow(false)
    val isRouteMode: StateFlow<Boolean> = _isRouteMode.asStateFlow()
    
    private val _currentStopIndex = MutableStateFlow(0)
    val currentStopIndex: StateFlow<Int> = _currentStopIndex.asStateFlow()
    
    val appointments = _date.flatMapLatest { date ->
        appointmentRepository.observeAppointmentsForDate(date)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val canOptimize = combine(
        appointments,
        _routeState
    ) { appts, state ->
        appts.isNotEmpty() && state !is RouteState.Optimizing
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    init {
        loadRoute()
    }
    
    private fun loadRoute() {
        viewModelScope.launch {
            _routeState.value = RouteState.Loading
            
            // Check for cached route plan
            val cachedPlan = routePlanRepository.getRouteForDate(_date.value)
            if (cachedPlan != null) {
                _routeState.value = RouteState.Optimized(
                    OptimizedRoute(
                        plan = cachedPlan,
                        originalStats = RouteStats(0.0, 0), // Not available from cache
                        savings = RouteSavings(0.0, 0) // Recalculate if needed
                    )
                )
                return@launch
            }
            
            // Check if appointments exist
            val appts = appointments.first()
            if (appts.isEmpty()) {
                _routeState.value = RouteState.NoAppointments
            } else {
                _routeState.value = RouteState.NotOptimized
            }
        }
    }
    
    fun optimizeRoute() {
        viewModelScope.launch {
            val appts = appointments.value
            if (appts.isEmpty()) return@launch
            
            // Check tier limits first
            when (val eligibility = usageLimitsManager.canOptimizeRoute(appts.size)) {
                is RouteOptimizationEligibility.Allowed -> {
                    // Proceed with optimization
                }
                is RouteOptimizationEligibility.UpgradeRequired -> {
                    _routeState.value = RouteState.Error("Upgrade required for route optimization")
                    return@launch
                }
                is RouteOptimizationEligibility.TooManyStops -> {
                    _routeState.value = RouteState.Error(
                        "Your plan allows ${eligibility.allowed} stops, " +
                        "but you have ${eligibility.requested} appointments"
                    )
                    return@launch
                }
            }
            
            _routeState.value = RouteState.Optimizing
            
            val startLocation = userPreferencesManager.getHomeLocation()
                ?: getCurrentLocation()
                ?: run {
                    _routeState.value = RouteState.Error("No start location available")
                    return@launch
                }
            
            val endLocation = userPreferencesManager.getEndLocationPreference()
                ?: startLocation
            
            val startTime = appts.minOfOrNull { it.appointment.time }
                ?: LocalTime.of(8, 0)
            
            val result = routeOptimizer.optimizeRoute(
                date = _date.value,
                appointments = appts,
                startLocation = startLocation,
                endLocation = endLocation,
                lockedStops = _lockedStops.value,
                startTime = startTime
            )
            
            result.fold(
                onSuccess = { route ->
                    _routeState.value = RouteState.Optimized(route)
                },
                onFailure = { exception ->
                    _routeState.value = RouteState.Error(
                        when (exception) {
                            is RouteException.FeatureNotAvailable -> 
                                "Route optimization requires a paid subscription"
                            is RouteException.TierLimitExceeded ->
                                "Too many stops for your plan"
                            is RouteException.NoValidLocations ->
                                "No valid addresses found. Please check client locations."
                            is RouteException.OptimizationFailed ->
                                "Optimization failed: ${exception.message}"
                            else -> exception.message ?: "Unknown error"
                        }
                    )
                }
            )
        }
    }
    
    fun reorderStop(fromIndex: Int, toIndex: Int) {
        val currentRoute = (_routeState.value as? RouteState.Optimized)?.route ?: return
        
        viewModelScope.launch {
            val newStops = currentRoute.plan.stops.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            
            // Recalculate ETAs and update order
            val updatedStops = recalculateStops(newStops, currentRoute.plan.startLocation)
            val updatedPlan = currentRoute.plan.copy(stops = updatedStops)
            
            // Recalculate totals
            val totalMiles = updatedStops.sumOf { it.distanceMilesFromPrevious }
            val totalMinutes = updatedStops.sumOf { it.driveMinutesFromPrevious }
            
            val finalPlan = updatedPlan.copy(
                totalDistanceMiles = totalMiles,
                totalDriveMinutes = totalMinutes
            )
            
            // Update savings (recalculate against original)
            val newSavings = RouteSavings(
                milesSaved = currentRoute.originalStats.totalMiles - totalMiles,
                minutesSaved = currentRoute.originalStats.totalMinutes - totalMinutes
            )
            
            _routeState.value = RouteState.Optimized(
                currentRoute.copy(
                    plan = finalPlan,
                    savings = newSavings
                )
            )
            
            // Persist updated plan
            routePlanRepository.update(finalPlan)
        }
    }
    
    fun toggleLock(appointmentId: String) {
        val currentRoute = (_routeState.value as? RouteState.Optimized)?.route ?: return
        val stop = currentRoute.plan.stops.find { it.appointmentId == appointmentId } ?: return
        
        if (_lockedStops.value.containsKey(appointmentId)) {
            // Unlock
            _lockedStops.value = _lockedStops.value - appointmentId
        } else {
            // Lock at current position
            _lockedStops.value = _lockedStops.value + (appointmentId to stop.order)
        }
        
        // Update UI to reflect lock state
        updateLockStates()
    }
    
    fun startRouteMode() {
        _isRouteMode.value = true
        _currentStopIndex.value = 0
    }
    
    fun navigateToStop(stop: RouteStop) {
        navigationHelper.navigateTo(
            address = stop.address,
            latitude = stop.location.latitude,
            longitude = stop.location.longitude
        )
    }
    
    fun completeCurrentStop() {
        val currentRoute = (_routeState.value as? RouteState.Optimized)?.route ?: return
        val currentIndex = _currentStopIndex.value
        
        if (currentIndex < currentRoute.plan.stops.size) {
            viewModelScope.launch {
                val stop = currentRoute.plan.stops[currentIndex]
                
                // Mark stop as completed in route plan
                val updatedStops = currentRoute.plan.stops.toMutableList().apply {
                    set(currentIndex, stop.copy(isCompleted = true))
                }
                
                _routeState.value = RouteState.Optimized(
                    currentRoute.copy(
                        plan = currentRoute.plan.copy(stops = updatedStops)
                    )
                )
                
                // Advance to next stop
                if (currentIndex < currentRoute.plan.stops.size - 1) {
                    _currentStopIndex.value = currentIndex + 1
                } else {
                    // Route complete
                    _isRouteMode.value = false
                }
            }
        }
    }
    
    private fun recalculateStops(
        stops: List<RouteStop>,
        startLocation: LatLng
    ): List<RouteStop> {
        // Simple recalculation without API call
        // Uses cached distances or estimates
        var previousLocation = startLocation
        var currentTime = stops.firstOrNull()?.estimatedArrival 
            ?: LocalTime.of(8, 0)
        
        return stops.mapIndexed { index, stop ->
            val distance = SphericalUtil.computeDistanceBetween(
                previousLocation,
                stop.location
            ) / 1609.34 // to miles
            
            val driveMinutes = (distance * 2).toInt() // Estimate at 30 mph
            currentTime = currentTime.plusMinutes(driveMinutes.toLong())
            val arrivalTime = currentTime
            currentTime = currentTime.plusMinutes(stop.appointmentDurationMinutes.toLong())
            
            previousLocation = stop.location
            
            stop.copy(
                order = index + 1,
                estimatedArrival = arrivalTime,
                driveMinutesFromPrevious = driveMinutes,
                distanceMilesFromPrevious = distance
            )
        }
    }
}

sealed interface RouteState {
    data object Loading : RouteState
    data object NoAppointments : RouteState
    data object NotOptimized : RouteState
    data object Optimizing : RouteState
    data class Optimized(val route: OptimizedRoute) : RouteState
    data class Error(val message: String) : RouteState
}
```

---

## 5. Savings Display

### 5.1 SavingsCard

**File**: `feature/route/ui/components/SavingsCard.kt`

```kotlin
@Composable
fun SavingsCard(
    savings: RouteSavings,
    originalStats: RouteStats,
    optimizedStats: RouteStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (savings.milesSaved > 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "SAVINGS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (savings.milesSaved > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save ${savings.milesSaved.formatMiles()} & ${savings.minutesSaved} minutes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Route is already optimized",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Before",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${originalStats.totalMiles.formatMiles()}, ${originalStats.totalMinutes.formatDuration()}",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "After",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${optimizedStats.totalMiles.formatMiles()}, ${optimizedStats.totalMinutes.formatDuration()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun Double.formatMiles(): String = String.format("%.1f mi", this)

private fun Int.formatDuration(): String {
    val hours = this / 60
    val minutes = this % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
```

---

## 6. Stop Reordering

### 6.1 Drag-and-Drop Behavior

Using `reorderable` library for drag-and-drop:

1. **Long press** on stop card initiates drag
2. **Drag indicator** (≡) visible on left side
3. **Haptic feedback** on start and during drag
4. **Drop zone highlights** as user drags
5. **On drop**: Recalculate ETAs for all stops

### 6.2 RouteStopCard

**File**: `feature/route/ui/components/RouteStopCard.kt`

```kotlin
@Composable
fun RouteStopCard(
    stop: RouteStop,
    isDragging: Boolean,
    onLockToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isDragging) 0.8f else 1f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .shadow(if (isDragging) 8.dp else 1.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (stop.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Stop number badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (stop.isCompleted) Color.Gray 
                        else MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (stop.isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = stop.order.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Stop details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.clientBusinessName ?: stop.clientName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stop.isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${stop.driveMinutesFromPrevious} min · ${stop.distanceMilesFromPrevious.formatMiles()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // ETA
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stop.estimatedArrival.format(
                        DateTimeFormatter.ofPattern("h:mm a")
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "+${stop.appointmentDurationMinutes}m appt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Lock toggle button
            IconButton(onClick = onLockToggle) {
                Icon(
                    imageVector = if (stop.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (stop.isLocked) "Unlock" else "Lock",
                    tint = if (stop.isLocked) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
```

### 6.3 Reset to Optimized

After manual reordering, show option to reset:

```kotlin
if (hasBeenManuallyReordered) {
    TextButton(
        onClick = viewModel::resetToOptimized
    ) {
        Icon(Icons.Default.Refresh, null)
        Text("Reset to Optimized Order")
    }
}
```

---

## 7. Stop Locking

### 7.1 Lock Behavior

When a stop is locked:
- It stays in its current position during re-optimization
- Other stops are optimized around it
- Visual lock icon shown on stop card
- Lock persists until manually unlocked

### 7.2 Use Cases

| Scenario | Solution |
|----------|----------|
| Client requires morning appointment | Lock at position 1 |
| Must end at specific location | Lock at last position |
| Client has fixed lunch schedule | Lock at specific position |

### 7.3 Re-Optimization with Locks

When "Optimize" is tapped with locked stops:

1. Locked stops are held at their positions
2. Remaining stops are optimized
3. ETAs are recalculated for all stops
4. Savings calculated based on original vs. new route

---

## 8. Start/End Location

### 8.1 Location Options

| Option | Description |
|--------|-------------|
| Home | User's home/business address |
| Current Location | GPS location at optimization time |
| Custom | User-specified address |
| Last Stop | Route ends at final appointment |

### 8.2 Location Selection Sheet

**File**: `feature/route/ui/components/LocationSelectionSheet.kt`

```kotlin
@Composable
fun LocationSelectionSheet(
    title: String, // "Start Location" or "End Location"
    currentOption: LocationOption,
    homeAddress: String?,
    onOptionSelected: (LocationOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Home option
            LocationOptionItem(
                icon = Icons.Default.Home,
                title = "Home",
                subtitle = homeAddress ?: "Not set",
                isSelected = currentOption == LocationOption.Home,
                onClick = { onOptionSelected(LocationOption.Home) }
            )
            
            // Current location option
            LocationOptionItem(
                icon = Icons.Default.MyLocation,
                title = "Current Location",
                subtitle = "Use GPS location",
                isSelected = currentOption == LocationOption.CurrentLocation,
                onClick = { onOptionSelected(LocationOption.CurrentLocation) }
            )
            
            // Last stop option (for end location only)
            if (title == "End Location") {
                LocationOptionItem(
                    icon = Icons.Default.Flag,
                    title = "Last Stop",
                    subtitle = "End at final appointment",
                    isSelected = currentOption == LocationOption.LastStop,
                    onClick = { onOptionSelected(LocationOption.LastStop) }
                )
            }
            
            // Custom option
            LocationOptionItem(
                icon = Icons.Default.Edit,
                title = "Custom Address",
                subtitle = (currentOption as? LocationOption.Custom)?.address ?: "Enter address",
                isSelected = currentOption is LocationOption.Custom,
                onClick = { onOptionSelected(LocationOption.Custom("")) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

sealed class LocationOption {
    object Home : LocationOption()
    object CurrentLocation : LocationOption()
    object LastStop : LocationOption()
    data class Custom(val address: String) : LocationOption()
}
```

---

## 9. Route Navigation Mode

### 9.1 Active Route Screen

When user taps "Start Route":

```
┌─────────────────────────────────────────┐
│ 🚗 En Route              [End Route ✕]  │
├─────────────────────────────────────────┤
│                                         │
│         ┌─────────────────────┐         │
│         │  NEXT STOP          │         │
│         │                     │         │
│         │  Johnson Ranch      │         │
│         │  1234 Ranch Rd      │         │
│         │                     │         │
│         │  ETA: 8:15 AM       │         │
│         │  15 min away        │         │
│         │                     │         │
│         │  [Navigate]         │         │
│         └─────────────────────┘         │
│                                         │
├─────────────────────────────────────────┤
│  Coming up:                             │
│                                         │
│  2. Williams Farm          10:15 AM     │
│  3. Martinez Stables       11:45 AM     │
│  4. Oak Hill Equestrian     1:30 PM     │
│                                         │
├─────────────────────────────────────────┤
│  [✓ Complete & Next]                    │
│                                         │
└─────────────────────────────────────────┘
```

### 9.2 Route Mode Behavior

1. **Navigate button** → Opens navigation app with current stop
2. **Complete & Next** → Marks stop complete, advances to next
3. **Coming up list** → Shows remaining stops with ETAs
4. **End Route** → Exits route mode (confirms if stops remain)

### 9.3 Completion Flow

When "Complete & Next" is tapped:

1. Mark current stop as completed
2. Advance `currentStopIndex`
3. If linked to appointment, prompt to complete appointment
4. Recalculate ETAs for remaining stops based on current time
5. If last stop, show completion screen

---

## 10. Data Models

### 10.1 Route Plan Entity

```kotlin
// core/database/entity/RoutePlanEntity.kt
@Entity(tableName = "route_plans")
data class RoutePlanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: LocalDate,
    @ColumnInfo(name = "start_lat") val startLat: Double,
    @ColumnInfo(name = "start_lng") val startLng: Double,
    @ColumnInfo(name = "start_name") val startName: String,
    @ColumnInfo(name = "end_lat") val endLat: Double,
    @ColumnInfo(name = "end_lng") val endLng: Double,
    @ColumnInfo(name = "end_name") val endName: String,
    val stops: String, // JSON array of RouteStop
    @ColumnInfo(name = "polyline_points") val polylinePoints: String?, // Encoded polyline
    @ColumnInfo(name = "total_distance_miles") val totalDistanceMiles: Double,
    @ColumnInfo(name = "total_drive_minutes") val totalDriveMinutes: Int,
    @ColumnInfo(name = "optimized_at") val optimizedAt: Instant,
    @ColumnInfo(name = "is_manually_reordered") val isManuallyReordered: Boolean = false,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.SYNCED
)
```

### 10.2 Domain Models

```kotlin
// core/domain/model/RoutePlan.kt
data class RoutePlan(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val startLocation: LatLng,
    val startLocationName: String,
    val endLocation: LatLng,
    val endLocationName: String,
    val stops: List<RouteStop>,
    val polylinePoints: List<LatLng>,
    val totalDistanceMiles: Double,
    val totalDriveMinutes: Int,
    val lastLegDriveMinutes: Int,
    val lastLegDistanceMiles: Double,
    val optimizedAt: Instant,
    val isManuallyReordered: Boolean
)

data class RouteStop(
    val appointmentId: String,
    val clientName: String,
    val clientBusinessName: String?,
    val location: LatLng,
    val address: String,
    val order: Int,
    val estimatedArrival: LocalTime,
    val appointmentDurationMinutes: Int,
    val driveMinutesFromPrevious: Int,
    val distanceMilesFromPrevious: Double,
    val isLocked: Boolean = false,
    val isCompleted: Boolean = false
)

data class OptimizedRoute(
    val plan: RoutePlan,
    val originalStats: RouteStats,
    val savings: RouteSavings
)

data class RouteStats(
    val totalMiles: Double,
    val totalMinutes: Int
)

data class RouteSavings(
    val milesSaved: Double,
    val minutesSaved: Int
) {
    val hasSavings: Boolean = milesSaved > 0 || minutesSaved > 0
}

sealed class RouteException : Exception() {
    data object FeatureNotAvailable : RouteException()
    data class TierLimitExceeded(
        val requested: Int,
        val allowed: Int
    ) : RouteException()
    data object NoValidLocations : RouteException()
    data class OptimizationFailed(override val message: String?) : RouteException()
}
```

---

## 11. File References

### 11.1 Core Files

| File Path | Purpose |
|-----------|---------|
| `core/route/RouteOptimizer.kt` | Optimization logic |
| `core/network/GoogleRoutesService.kt` | Routes API client |
| `core/database/entity/RoutePlanEntity.kt` | Database entity |
| `core/database/dao/RoutePlanDao.kt` | Database queries |
| `core/domain/model/RoutePlan.kt` | Domain models |
| `core/domain/repository/RoutePlanRepository.kt` | Repository interface |
| `core/data/repository/RoutePlanRepositoryImpl.kt` | Repository implementation |

### 11.2 Feature Files

| File Path | Purpose |
|-----------|---------|
| `feature/route/ui/RouteScreen.kt` | Main route screen |
| `feature/route/ui/RouteViewModel.kt` | Business logic |
| `feature/route/ui/components/SavingsCard.kt` | Savings display |
| `feature/route/ui/components/RouteStopCard.kt` | Stop item |
| `feature/route/ui/components/LocationSelectionSheet.kt` | Location picker |
| `feature/route/ui/ActiveRouteScreen.kt` | Navigation mode |

### 11.3 Navigation

| Route | Screen |
|-------|--------|
| `/route` | RouteScreen (today) |
| `/route?date={date}` | RouteScreen (specific date) |
| `/route/active` | ActiveRouteScreen |

---

## 12. Acceptance Criteria

### 12.1 Route Optimization

| ID | Given | When | Then |
|----|-------|------|------|
| AC-010-01 | 4 appointments scattered across city | Tap "Optimize" | Route reordered to minimize distance |
| AC-010-02 | Optimized route | View savings card | Shows miles and minutes saved |
| AC-010-03 | Original: 68 mi, Optimized: 47 mi | View savings | Shows "Save 21 miles" |
| AC-010-04 | Solo tier (8 stop limit), 5 appointments | Optimize route | Optimization succeeds |
| AC-010-05 | Solo tier (8 stop limit), 10 appointments | Optimize route | Error: "Your plan allows 8 stops" |
| AC-010-06 | Free tier | Tap Optimize | Error: upgrade required message |

### 12.2 Stop Reordering

| ID | Given | When | Then |
|----|-------|------|------|
| AC-010-07 | Optimized route with 4 stops | Long press and drag stop 2 to position 4 | Order updated, ETAs recalculated |
| AC-010-08 | Manually reordered route | View screen | "Reset to Optimized" option visible |
| AC-010-09 | Manually reordered route | Tap reset | Route returns to optimized order |

### 12.3 Stop Locking

| ID | Given | When | Then |
|----|-------|------|------|
| AC-010-10 | Stop at position 1 | Tap lock icon | Stop shows locked indicator |
| AC-010-11 | Stop 1 locked | Tap Optimize | Stop 1 stays at position 1, others optimized around it |
| AC-010-12 | Locked stop | Tap lock icon again | Stop unlocked |

### 12.4 Start/End Location

| ID | Given | When | Then |
|----|-------|------|------|
| AC-010-13 | Home address set | View route | Start shows "Home (address)" |
| AC-010-14 | Start location | Tap edit | Location options sheet appears |
| AC-010-15 | Select "Current Location" | Confirm | GPS location used as start |

### 12.5 Route Navigation

| ID | Given | When | Then |
|----|-------|------|------|
| AC-010-16 | Optimized route | Tap "Start Route" | Active route screen appears |
| AC-010-17 | Active route, stop 1 current | Tap Navigate | Navigation app opens with stop 1 address |
| AC-010-18 | Active route, stop 1 current | Tap "Complete & Next" | Stop 1 marked complete, stop 2 becomes current |
| AC-010-19 | Last stop, tap Complete | Complete stop | Route completion message shown |

### 12.6 ETA Accuracy

| ID | Given | When | Then |
|----|-------|------|------|
| AC-010-20 | 4 stops, 8 AM start | View ETAs | Each stop shows estimated arrival time |
| AC-010-21 | Stop 1: 8 AM arrival, 45 min appointment, 15 min drive to stop 2 | View stop 2 ETA | Shows 9:00 AM |
| AC-010-22 | Current time is 9:30 AM, behind schedule | Tap refresh ETAs | ETAs recalculated from current time |

---

## 13. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Optimization API call | < 5s | Google Routes API response |
| UI update after optimization | < 200ms | State update to render |
| Drag-and-drop reorder | 60 FPS | Smooth animation |
| ETA recalculation | < 100ms | After reorder/complete |
| Route plan cache load | < 100ms | From local database |

---

## 14. Offline Behavior

### 14.1 Cached Routes

- Route plans cached locally after optimization
- Cached routes available offline
- Manual reordering works offline
- ETA recalculation works offline (estimated)

### 14.2 Optimization Requires Network

- "Optimize" button disabled when offline
- Show message: "Route optimization requires internet"
- Queue optimization request for when online (optional)

### 14.3 Sync

- Route plans sync to server after optimization
- Manual reorders sync when online
- Completion status syncs with appointment status

---

## 15. Error Handling

### 15.1 API Errors

| Error | User Message | Action |
|-------|--------------|--------|
| Network failure | "Unable to optimize. Check connection." | Retry button |
| Routes API error | "Optimization failed. Try again." | Retry button |
| No valid locations | "Some addresses couldn't be found." | Show which |
| Timeout | "Taking too long. Try again." | Retry button |

### 15.2 Data Errors

| Error | Handling |
|-------|----------|
| No appointments for date | Show "No appointments" message |
| Appointments without addresses | Exclude from route, show warning |
| Corrupted cached route | Clear cache, re-optimize |

---

## 16. API Cost Tracking

### 16.1 Cost per Request

| API | Cost |
|-----|------|
| Routes API - Compute Routes | $5 per 1,000 requests |
| Routes API - with traffic | +$5 per 1,000 requests |

### 16.2 Estimated Monthly Cost

| Scenario | Optimizations/Month | Cost |
|----------|---------------------|------|
| Light user (1/day) | ~20 | $0.20 |
| Active user (2/day) | ~60 | $0.60 |
| Power user (5/day) | ~100 | $1.00 |

### 16.3 Cost Control

- Cache route plans (don't re-optimize same day unless changes)
- Batch requests where possible
- Monitor usage per user
- Consider rate limiting for abuse

---

## 17. Testing Scenarios

### 17.1 Unit Tests

```kotlin
class RouteOptimizerTest {
    @Test
    fun `optimizeRoute respects tier limits`()
    
    @Test
    fun `optimizeRoute excludes invalid locations`()
    
    @Test
    fun `mergeLockedStops places locked stops correctly`()
    
    @Test
    fun `buildRouteStops calculates ETAs correctly`()
    
    @Test
    fun `calculateRouteStats computes distance accurately`()
}

class RouteViewModelTest {
    @Test
    fun `reorderStop updates stop order`()
    
    @Test
    fun `toggleLock updates locked state`()
    
    @Test
    fun `completeCurrentStop advances to next stop`()
}
```

### 17.2 Integration Tests

```kotlin
class GoogleRoutesServiceTest {
    @Test
    fun `computeOptimalRoute returns valid response`()
    
    @Test
    fun `computeOptimalRoute handles API errors`()
}

class RoutePlanRepositoryTest {
    @Test
    fun `saves and retrieves route plan correctly`()
    
    @Test
    fun `updates route plan after reorder`()
}
```

---

## 18. Future Considerations

### 18.1 Potential Enhancements

- Multi-day route planning
- Recurring route templates
- Time windows for appointments
- Break/lunch time scheduling
- Real-time traffic updates during route
- Alternative route suggestions
- Carbon footprint tracking

### 18.2 Not in Scope

- Turn-by-turn navigation within app
- Fleet/multi-vehicle routing
- Customer-facing route sharing
- Historical route analytics dashboard
