# FRD-009: Maps Integration

**PRD Reference**: PRD-009-maps.md  
**Priority**: P0  
**Phase**: 3 - Route Intelligence  
**Estimated Duration**: 2 weeks

---

## 1. Overview

### 1.1 Purpose

This document specifies the complete functional requirements for Google Maps integration to visualize client locations, display appointment routes, and enable seamless navigation to client addresses.

### 1.2 Scope

This FRD covers:
- Client map view with pin clustering
- Pin color coding by client/horse status
- Client selection and info bottom sheet
- Route map view with numbered stops
- Navigation handoff to Google Maps/Waze
- Map filtering options
- Service radius overlay
- Location permission handling

### 1.3 Dependencies

| Dependency | FRD | Description |
|------------|-----|-------------|
| Client Management | FRD-003 | Client location data |
| Horse Management | FRD-004 | Horse due dates for status |
| Appointments | FRD-005 | Appointment data for route view |
| Route Optimization | FRD-010 | Optimized route data |
| Google Maps SDK | External | Map rendering and clustering |
| Location Services | System | Current location access |

---

## 2. Location Permission Handling

### 2.1 Permission Requirements

- `ACCESS_FINE_LOCATION`: Required for current location and navigation
- `ACCESS_COARSE_LOCATION`: Fallback if fine location denied

### 2.2 LocationPermissionHandler

**File**: `core/location/LocationPermissionHandler.kt`

```kotlin
@Singleton
class LocationPermissionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getPermissionState(): LocationPermissionState {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return when {
            fineGranted -> LocationPermissionState.Granted(precise = true)
            coarseGranted -> LocationPermissionState.Granted(precise = false)
            else -> LocationPermissionState.NotGranted
        }
    }
}

sealed class LocationPermissionState {
    data class Granted(val precise: Boolean) : LocationPermissionState()
    object NotGranted : LocationPermissionState()
    object PermanentlyDenied : LocationPermissionState()
}
```

### 2.3 Permission Request Flow

When user opens Map screen without location permission:

1. **Check permission state**
2. **If NotGranted:**
   - Show rationale bottom sheet:
     ```
     ┌─────────────────────────────────────────┐
     │  📍 Location Access                     │
     │                                         │
     │  Hoof Direct needs location access to:  │
     │                                         │
     │  • Show your position on the map        │
     │  • Calculate routes to clients          │
     │  • Navigate to appointments             │
     │                                         │
     │  [Allow Location Access]                │
     │                                         │
     │  [Not Now]                              │
     └─────────────────────────────────────────┘
     ```
   - User taps "Allow" → Launch system permission dialog
   - User taps "Not Now" → Show map without current location

3. **If PermanentlyDenied:**
   - Show settings prompt:
     ```
     ┌─────────────────────────────────────────┐
     │  Location Access Required               │
     │                                         │
     │  Please enable location in Settings     │
     │  to use map features.                   │
     │                                         │
     │  [Open Settings]  [Cancel]              │
     └─────────────────────────────────────────┘
     ```

### 2.4 Map Behavior Without Location

If location permission is denied:
- Map centers on home base location (from user settings)
- "My Location" button hidden
- Navigation still works (uses destination only)
- Route calculation uses home base as start point
- Show banner: "Enable location for better experience"

---

## 3. Client Map View

### 3.1 Screen Layout

**Route**: `/map` (accessible from bottom nav "Map" tab)

```
┌─────────────────────────────────────────┐
│ Clients Map              [Filter ▼] 🎯  │
├─────────────────────────────────────────┤
│                                         │
│           ┌───┐                         │
│        ●  │ 5 │  ●                      │
│           └───┘                         │
│      ●         🔵            ●          │
│                                         │
│         🟡        🔵                    │
│                       ●                 │
│    ●            🔴                      │
│                                         │
│              📍 (you are here)          │
│                                         │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ Johnson Ranch                       │ │
│ │ 1234 Ranch Road, Austin TX         │ │
│ │                                     │ │
│ │ Next: Jan 20 (in 3 days)           │ │
│ │ 🟡 2 horses due soon               │ │
│ │                                     │ │
│ │ [Navigate]  [Call]  [Schedule]     │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 3.2 MapScreen Composable

**File**: `feature/map/ui/MapScreen.kt`

```kotlin
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onNavigateToClient: (String) -> Unit,
    onNavigateToAppointment: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState()
    
    // Initialize camera position
    LaunchedEffect(uiState.initialPosition) {
        uiState.initialPosition?.let { position ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(position, 10f)
            )
        }
    }
    
    Scaffold(
        topBar = {
            MapTopBar(
                filter = uiState.filter,
                onFilterChange = viewModel::setFilter,
                onCenterOnLocation = viewModel::centerOnCurrentLocation
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = uiState.locationEnabled,
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = uiState.locationEnabled,
                    mapToolbarEnabled = false
                )
            ) {
                // Clustered client markers
                ClientMarkerCluster(
                    clients = uiState.clients,
                    onClientClick = viewModel::selectClient
                )
                
                // Service radius circle (if enabled)
                uiState.serviceRadius?.let { radius ->
                    Circle(
                        center = uiState.homeLocation,
                        radius = radius.toMeters(),
                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2f,
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                }
            }
            
            // Client info bottom sheet
            uiState.selectedClient?.let { client ->
                ClientInfoBottomSheet(
                    client = client,
                    onNavigate = { viewModel.navigateToClient(client.id) },
                    onCall = { viewModel.callClient(client) },
                    onSchedule = { 
                        onNavigateToAppointment("new?clientId=${client.id}")
                    },
                    onViewDetails = { onNavigateToClient(client.id) },
                    onDismiss = viewModel::clearSelection
                )
            }
            
            // Location permission banner
            if (!uiState.locationEnabled && uiState.showLocationBanner) {
                LocationPermissionBanner(
                    onRequestPermission = viewModel::requestLocationPermission,
                    onDismiss = viewModel::dismissLocationBanner,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
```

### 3.3 MapViewModel

**File**: `feature/map/ui/MapViewModel.kt`

```kotlin
@HiltViewModel
class MapViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val appointmentRepository: AppointmentRepository,
    private val horseRepository: HorseRepository,
    private val userPreferencesManager: UserPreferencesManager,
    private val locationHelper: LocationHelper,
    private val navigationHelper: NavigationHelper
) : ViewModel() {

    private val _filter = MutableStateFlow(MapFilter.ALL_CLIENTS)
    val filter: StateFlow<MapFilter> = _filter.asStateFlow()
    
    private val _selectedClient = MutableStateFlow<ClientMapItem?>(null)
    val selectedClient: StateFlow<ClientMapItem?> = _selectedClient.asStateFlow()
    
    val clients: StateFlow<List<ClientMapItem>> = combine(
        clientRepository.observeAllActive(),
        appointmentRepository.observeUpcoming(),
        horseRepository.observeAllWithDueStatus(),
        _filter
    ) { clients, appointments, horses, filter ->
        clients
            .filter { it.latitude != null && it.longitude != null }
            .map { client ->
                val clientHorses = horses.filter { it.clientId == client.id }
                val nextAppointment = appointments
                    .filter { it.clientId == client.id }
                    .minByOrNull { it.date }
                
                ClientMapItem(
                    clientId = client.id,
                    name = "${client.firstName} ${client.lastName}",
                    businessName = client.businessName,
                    latitude = client.latitude!!,
                    longitude = client.longitude!!,
                    address = client.fullAddress,
                    phone = client.phone,
                    status = determineStatus(clientHorses, nextAppointment),
                    nextAppointmentDate = nextAppointment?.date,
                    horsesCount = clientHorses.size,
                    horsesDueSoon = clientHorses.count { it.isDueSoon },
                    horsesOverdue = clientHorses.count { it.isOverdue }
                )
            }
            .filter { applyFilter(it, filter) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private fun determineStatus(
        horses: List<HorseWithDueStatus>,
        nextAppointment: AppointmentEntity?
    ): ClientMapStatus {
        return when {
            nextAppointment != null -> ClientMapStatus.HAS_APPOINTMENT
            horses.any { it.isOverdue } -> ClientMapStatus.OVERDUE
            horses.any { it.isDueSoon } -> ClientMapStatus.DUE_SOON
            else -> ClientMapStatus.INACTIVE
        }
    }
    
    private fun applyFilter(client: ClientMapItem, filter: MapFilter): Boolean {
        return when (filter) {
            MapFilter.ALL_CLIENTS -> true
            MapFilter.TODAY_APPOINTMENTS -> client.nextAppointmentDate == LocalDate.now()
            MapFilter.THIS_WEEK -> client.nextAppointmentDate?.let { date ->
                date >= LocalDate.now() && date <= LocalDate.now().plusDays(7)
            } ?: false
            MapFilter.DUE_SOON -> client.status == ClientMapStatus.DUE_SOON || 
                                  client.status == ClientMapStatus.OVERDUE
        }
    }
    
    fun selectClient(clientId: String) {
        viewModelScope.launch {
            _selectedClient.value = clients.value.find { it.clientId == clientId }
        }
    }
    
    fun clearSelection() {
        _selectedClient.value = null
    }
    
    fun navigateToClient(clientId: String) {
        viewModelScope.launch {
            val client = clients.value.find { it.clientId == clientId } ?: return@launch
            navigationHelper.navigateTo(
                address = client.address,
                latitude = client.latitude,
                longitude = client.longitude
            )
        }
    }
    
    fun callClient(client: ClientMapItem) {
        client.phone?.let { phone ->
            // Launch phone dialer
            navigationHelper.dialPhone(phone)
        }
    }
}
```

---

## 4. Pin Color Coding

### 4.1 ClientMapStatus Enum

```kotlin
// core/domain/model/ClientMapStatus.kt

enum class ClientMapStatus {
    HAS_APPOINTMENT,  // Has upcoming scheduled appointment
    DUE_SOON,         // No appointment, but horses due within 14 days
    OVERDUE,          // Horses past due date
    INACTIVE          // No appointments, no horses due
}
```

### 4.2 Pin Colors

| Status | Color | Hex Value | Description |
|--------|-------|-----------|-------------|
| HAS_APPOINTMENT | Blue | #2196F3 | Has scheduled appointment |
| DUE_SOON | Yellow/Amber | #FFC107 | Horses due within 14 days |
| OVERDUE | Red | #F44336 | Horses past due date |
| INACTIVE | Gray | #9E9E9E | No urgency |

### 4.3 Custom Marker Icons

**File**: `feature/map/ui/components/ClientMarker.kt`

```kotlin
@Composable
fun ClientMarkerIcon(status: ClientMapStatus): BitmapDescriptor {
    val color = when (status) {
        ClientMapStatus.HAS_APPOINTMENT -> Color(0xFF2196F3)
        ClientMapStatus.DUE_SOON -> Color(0xFFFFC107)
        ClientMapStatus.OVERDUE -> Color(0xFFF44336)
        ClientMapStatus.INACTIVE -> Color(0xFF9E9E9E)
    }
    
    return remember(status) {
        createMarkerBitmap(color)
    }
}

private fun createMarkerBitmap(color: Color): BitmapDescriptor {
    // Create custom pin drawable with specified color
    // Returns BitmapDescriptor for use in Marker
}
```

### 4.4 Cluster Marker

When pins are clustered, show count with dominant status color:

```kotlin
@Composable
fun ClusterMarkerContent(
    cluster: Cluster<ClientMapItem>
) {
    val dominantStatus = cluster.items
        .groupBy { it.status }
        .maxByOrNull { it.value.size }
        ?.key ?: ClientMapStatus.INACTIVE
    
    val backgroundColor = when (dominantStatus) {
        ClientMapStatus.HAS_APPOINTMENT -> Color(0xFF2196F3)
        ClientMapStatus.DUE_SOON -> Color(0xFFFFC107)
        ClientMapStatus.OVERDUE -> Color(0xFFF44336)
        ClientMapStatus.INACTIVE -> Color(0xFF9E9E9E)
    }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(backgroundColor, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cluster.size.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

---

## 5. Client Selection and Info Sheet

### 5.1 Pin Tap Behavior

When user taps a client pin:

1. Pin highlights (slight scale animation)
2. Map animates to center on pin if needed
3. Bottom sheet slides up with client info
4. Other pins remain visible but dimmed

### 5.2 ClientInfoBottomSheet

**File**: `feature/map/ui/components/ClientInfoBottomSheet.kt`

```kotlin
@Composable
fun ClientInfoBottomSheet(
    client: ClientMapItem,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onSchedule: () -> Unit,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = client.businessName ?: client.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (client.businessName != null) {
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ClientStatusBadge(status = client.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Address
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = client.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Next appointment or horse status
            NextAppointmentOrStatus(client)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Navigation, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate")
                }
                
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    enabled = client.phone != null
                ) {
                    Icon(Icons.Default.Phone, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call")
                }
                
                FilledButton(
                    onClick = onSchedule,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // View details link
            TextButton(
                onClick = onViewDetails,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Client Details")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NextAppointmentOrStatus(client: ClientMapItem) {
    when {
        client.nextAppointmentDate != null -> {
            val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), client.nextAppointmentDate)
            val text = when {
                daysUntil == 0L -> "Today"
                daysUntil == 1L -> "Tomorrow"
                daysUntil <= 7 -> "In $daysUntil days"
                else -> "on ${client.nextAppointmentDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            }
            InfoRow(
                icon = Icons.Default.Event,
                label = "Next appointment",
                value = text,
                color = MaterialTheme.colorScheme.primary
            )
        }
        client.horsesOverdue > 0 -> {
            InfoRow(
                icon = Icons.Default.Warning,
                label = "${client.horsesOverdue} horse${if (client.horsesOverdue > 1) "s" else ""} overdue",
                value = "",
                color = Color(0xFFF44336)
            )
        }
        client.horsesDueSoon > 0 -> {
            InfoRow(
                icon = Icons.Default.Schedule,
                label = "${client.horsesDueSoon} horse${if (client.horsesDueSoon > 1) "s" else ""} due soon",
                value = "",
                color = Color(0xFFFFC107)
            )
        }
        else -> {
            InfoRow(
                icon = Icons.Default.CheckCircle,
                label = "${client.horsesCount} horses",
                value = "All up to date",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 6. Map Filters

### 6.1 MapFilter Enum

```kotlin
// core/domain/model/MapFilter.kt

enum class MapFilter(val displayName: String) {
    ALL_CLIENTS("All Clients"),
    TODAY_APPOINTMENTS("Today's Stops"),
    THIS_WEEK("This Week"),
    DUE_SOON("Due Soon")
}
```

### 6.2 Filter Dropdown

**File**: `feature/map/ui/components/MapFilterDropdown.kt`

```kotlin
@Composable
fun MapFilterDropdown(
    selectedFilter: MapFilter,
    onFilterChange: (MapFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true }
        ) {
            Text(selectedFilter.displayName)
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MapFilter.values().forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.displayName) },
                    onClick = {
                        onFilterChange(filter)
                        expanded = false
                    },
                    leadingIcon = {
                        if (filter == selectedFilter) {
                            Icon(Icons.Default.Check, null)
                        }
                    }
                )
            }
        }
    }
}
```

### 6.3 Filter Behavior

| Filter | Visible Pins |
|--------|--------------|
| All Clients | All clients with geocoded addresses |
| Today's Stops | Only clients with appointments today |
| This Week | Clients with appointments in next 7 days |
| Due Soon | Clients with horses due or overdue |

When filter reduces visible pins:
- Animate camera to fit all visible pins in bounds
- If no pins match filter, show message: "No clients match this filter"

---

## 7. Route Map View

### 7.1 Route Map Screen

When viewing today's route (from Route or Calendar screen):

**Route**: `/map/route?date={date}`

```
┌─────────────────────────────────────────┐
│ [←] Today's Route           [Start ▶]  │
├─────────────────────────────────────────┤
│                                         │
│    🏠 ─── 1️⃣ ─── 2️⃣ ─── 3️⃣ ─── 🏠     │
│                                         │
│         (route line connecting)         │
│                                         │
│    📍 (current position)                │
│                                         │
├─────────────────────────────────────────┤
│ 47.3 mi · 1h 52m drive · 4 stops        │
├─────────────────────────────────────────┤
│ 1️⃣ Johnson Ranch      8:00 AM  15 min  │
│ 2️⃣ Williams Farm     10:15 AM  12 min  │
│ 3️⃣ Martinez Stables  11:45 AM  18 min  │
│ 🏠 Home               1:30 PM  25 min   │
└─────────────────────────────────────────┘
```

### 7.2 RouteMapScreen Composable

**File**: `feature/map/ui/RouteMapScreen.kt`

```kotlin
@Composable
fun RouteMapScreen(
    viewModel: RouteMapViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onStopClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState()
    
    // Fit route bounds when loaded
    LaunchedEffect(uiState.route) {
        uiState.route?.let { route ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(route.bounds, 64)
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::startNavigation,
                        enabled = uiState.route != null
                    ) {
                        Icon(Icons.Default.Navigation, null)
                        Text("Start")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Map section (weight 2)
            Box(modifier = Modifier.weight(2f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = uiState.locationEnabled
                    )
                ) {
                    uiState.route?.let { route ->
                        // Route polyline
                        Polyline(
                            points = route.polylinePoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 8f,
                            pattern = listOf(Dot(), Gap(10f))
                        )
                        
                        // Start marker (home)
                        Marker(
                            state = MarkerState(position = route.startLocation),
                            icon = homeMarkerIcon(),
                            title = "Start",
                            anchor = Offset(0.5f, 0.5f)
                        )
                        
                        // Stop markers (numbered)
                        route.stops.forEachIndexed { index, stop ->
                            val markerColor = if (stop.isCompleted) {
                                Color.Gray
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            
                            Marker(
                                state = MarkerState(position = stop.location),
                                icon = numberedMarkerIcon(index + 1, markerColor),
                                title = stop.clientName,
                                snippet = "ETA: ${stop.estimatedArrival.format(timeFormatter)}",
                                onClick = {
                                    onStopClick(stop.appointmentId)
                                    true
                                }
                            )
                        }
                        
                        // End marker (if different from start)
                        if (route.endLocation != route.startLocation) {
                            Marker(
                                state = MarkerState(position = route.endLocation),
                                icon = endMarkerIcon(),
                                title = "End"
                            )
                        }
                    }
                }
            }
            
            // Route summary
            uiState.route?.let { route ->
                RouteSummaryBar(
                    totalMiles = route.totalDistanceMiles,
                    totalDriveMinutes = route.totalDriveMinutes,
                    stopCount = route.stops.size
                )
            }
            
            // Stop list (weight 1)
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                uiState.route?.let { route ->
                    items(route.stops) { stop ->
                        RouteStopItem(
                            stop = stop,
                            onClick = { onStopClick(stop.appointmentId) }
                        )
                    }
                    
                    // End point
                    item {
                        RouteEndItem(
                            location = route.endLocationName,
                            estimatedArrival = route.estimatedEndTime,
                            driveMinutes = route.lastLegDriveMinutes
                        )
                    }
                }
            }
        }
    }
}
```

### 7.3 Route Polyline

The route line connecting stops:

```kotlin
// Polyline configuration
Polyline(
    points = route.polylinePoints,
    color = MaterialTheme.colorScheme.primary,
    width = 8f,
    jointType = JointType.ROUND,
    startCap = RoundCap(),
    endCap = RoundCap()
)
```

### 7.4 Numbered Markers

Each stop shows a numbered marker 1, 2, 3, etc.:

```kotlin
private fun createNumberedMarkerBitmap(number: Int, color: Color): BitmapDescriptor {
    // Create 48dp circle with number inside
    // White text on colored background
    // Return as BitmapDescriptor
}
```

Marker states:
- **Pending**: Primary color (blue)
- **Completed**: Gray with checkmark overlay
- **Current/Next**: Slightly larger, animated pulse

---

## 8. Navigation Handoff

### 8.1 NavigationHelper

**File**: `core/navigation/NavigationHelper.kt`

```kotlin
@Singleton
class NavigationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend fun navigateTo(
        address: String,
        latitude: Double,
        longitude: Double
    ) {
        val preferredApp = userPreferencesManager.getNavigationApp()
        
        val intent = when (preferredApp) {
            NavigationApp.GOOGLE_MAPS -> createGoogleMapsIntent(latitude, longitude)
            NavigationApp.WAZE -> createWazeIntent(latitude, longitude)
            NavigationApp.SYSTEM_DEFAULT -> createDefaultIntent(latitude, longitude, address)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback to web navigation
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
            )
            context.startActivity(webIntent)
        }
    }
    
    private fun createGoogleMapsIntent(lat: Double, lng: Double): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("google.navigation:q=$lat,$lng")
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    private fun createWazeIntent(lat: Double, lng: Double): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
            setPackage("com.waze")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    private fun createDefaultIntent(lat: Double, lng: Double, address: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("geo:$lat,$lng?q=${Uri.encode(address)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    fun dialPhone(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
```

### 8.2 NavigationApp Enum

```kotlin
enum class NavigationApp(val displayName: String, val packageName: String?) {
    GOOGLE_MAPS("Google Maps", "com.google.android.apps.maps"),
    WAZE("Waze", "com.waze"),
    SYSTEM_DEFAULT("Default App", null)
}
```

### 8.3 Navigation Preference Setting

In Settings → Navigation:

```
┌─────────────────────────────────────────┐
│  Navigation App                         │
│                                         │
│  ○ Google Maps                          │
│  ○ Waze                                 │
│  ● System Default                       │
│                                         │
│  This affects the "Navigate" button     │
│  throughout the app.                    │
└─────────────────────────────────────────┘
```

Preference stored in `UserPreferencesManager` DataStore.

### 8.4 Start Route Navigation

When user taps "Start" on route map:

1. Get first uncompleted stop
2. Navigate to that stop's location
3. App moves to background
4. When user returns, check if at stop location (optional auto-advance)

---

## 9. Service Radius Overlay

### 9.1 Configuration

In Settings → Business Profile → Service Area:

```
┌─────────────────────────────────────────┐
│  Service Area                           │
│                                         │
│  Show service radius on map       [ON]  │
│                                         │
│  Radius:  [  50  ] miles                │
│                                         │
│  This helps visualize your coverage     │
│  area around your home base.            │
└─────────────────────────────────────────┘
```

### 9.2 Circle Overlay

When enabled, show a semi-transparent circle on the client map:

```kotlin
Circle(
    center = homeBaseLocation,
    radius = serviceRadiusMiles.toMeters(), // Convert miles to meters
    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    strokeWidth = 2f,
    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
)
```

### 9.3 Service Radius Data

```kotlin
// UserPreferencesManager
suspend fun getServiceRadius(): ServiceRadius? {
    return dataStore.data.first().let { prefs ->
        if (prefs[SHOW_SERVICE_RADIUS] == true) {
            ServiceRadius(
                radiusMiles = prefs[SERVICE_RADIUS_MILES] ?: 50,
                centerLat = prefs[HOME_BASE_LAT],
                centerLng = prefs[HOME_BASE_LNG]
            )
        } else null
    }
}

data class ServiceRadius(
    val radiusMiles: Int,
    val centerLat: Double?,
    val centerLng: Double?
) {
    fun toMeters(): Double = radiusMiles * 1609.34
    
    val isValid: Boolean = centerLat != null && centerLng != null
}
```

---

## 10. Marker Clustering

### 10.1 Clustering Implementation

Using `maps-compose-utils` library for clustering:

```kotlin
@Composable
fun ClientMarkerCluster(
    clients: List<ClientMapItem>,
    onClientClick: (String) -> Unit
) {
    val clusterManager = rememberClusterManager<ClientMapItem>()
    
    LaunchedEffect(clients) {
        clusterManager.clearItems()
        clusterManager.addItems(clients)
        clusterManager.cluster()
    }
    
    Clustering(
        clusterManager = clusterManager,
        clusterContent = { cluster ->
            ClusterMarkerContent(cluster)
        },
        clusterItemContent = { item ->
            ClientMarkerIcon(status = item.status)
        },
        onClusterClick = { cluster ->
            // Zoom in to show cluster items
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(
                    cluster.items.bounds(),
                    50
                )
            )
            true
        },
        onClusterItemClick = { item ->
            onClientClick(item.clientId)
            true
        }
    )
}
```

### 10.2 ClientMapItem ClusterItem Implementation

```kotlin
data class ClientMapItem(
    val clientId: String,
    val name: String,
    val businessName: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val phone: String?,
    val status: ClientMapStatus,
    val nextAppointmentDate: LocalDate?,
    val horsesCount: Int,
    val horsesDueSoon: Int,
    val horsesOverdue: Int
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(latitude, longitude)
    override fun getTitle(): String = businessName ?: name
    override fun getSnippet(): String = "$horsesCount horses"
    override fun getZIndex(): Float = when (status) {
        ClientMapStatus.OVERDUE -> 3f
        ClientMapStatus.DUE_SOON -> 2f
        ClientMapStatus.HAS_APPOINTMENT -> 1f
        ClientMapStatus.INACTIVE -> 0f
    }
}
```

### 10.3 Cluster Behavior

| Zoom Level | Behavior |
|------------|----------|
| < 8 | Heavy clustering (large areas) |
| 8-12 | Moderate clustering |
| > 12 | Minimal/no clustering (pins visible individually) |

---

## 11. Geocoding

### 11.1 Client Address Geocoding

When client address is saved, geocode to get coordinates:

**File**: `core/location/GeocodingService.kt`

```kotlin
@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    suspend fun geocodeAddress(address: String): GeocodingResult {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use async geocoding for Android 13+
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocationName(address, 1) { addresses ->
                            val result = addresses.firstOrNull()?.let {
                                GeocodingResult.Success(it.latitude, it.longitude)
                            } ?: GeocodingResult.NotFound
                            continuation.resume(result)
                        }
                    }
                } else {
                    // Legacy synchronous geocoding
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(address, 1)
                    addresses?.firstOrNull()?.let {
                        GeocodingResult.Success(it.latitude, it.longitude)
                    } ?: GeocodingResult.NotFound
                }
            } catch (e: IOException) {
                GeocodingResult.Error(e.message ?: "Geocoding failed")
            }
        }
    }
}

sealed class GeocodingResult {
    data class Success(val latitude: Double, val longitude: Double) : GeocodingResult()
    object NotFound : GeocodingResult()
    data class Error(val message: String) : GeocodingResult()
}
```

### 11.2 Geocoding Trigger

When client address is saved or updated in `ClientRepository`:

```kotlin
suspend fun saveClient(client: ClientEntity): Result<ClientEntity> {
    // If address changed and no coordinates, geocode
    if (client.needsGeocoding()) {
        val result = geocodingService.geocodeAddress(client.fullAddress)
        if (result is GeocodingResult.Success) {
            client = client.copy(
                latitude = result.latitude,
                longitude = result.longitude
            )
        }
    }
    
    clientDao.upsert(client)
    return Result.success(client)
}

private fun ClientEntity.needsGeocoding(): Boolean {
    return (latitude == null || longitude == null) && fullAddress.isNotBlank()
}
```

### 11.3 Handling Geocoding Failures

If geocoding fails:
- Store client without coordinates
- Client won't appear on map
- Show indicator on client card: "📍 Address not found on map"
- Allow manual coordinate entry (optional, advanced)

---

## 12. Current Location Tracking

### 12.1 LocationHelper

**File**: `core/location/LocationHelper.kt`

```kotlin
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationPermissionHandler: LocationPermissionHandler
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        30_000L // Update every 30 seconds
    )
        .setMinUpdateIntervalMillis(10_000L)
        .build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _currentLocation.value = LatLng(location.latitude, location.longitude)
            }
        }
    }
    
    fun startLocationUpdates() {
        if (locationPermissionHandler.getPermissionState() !is LocationPermissionState.Granted) {
            return
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission revoked
        }
    }
    
    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    suspend fun getLastKnownLocation(): LatLng? {
        if (locationPermissionHandler.getPermissionState() !is LocationPermissionState.Granted) {
            return null
        }
        
        return try {
            fusedLocationClient.lastLocation.await()?.let {
                LatLng(it.latitude, it.longitude)
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
```

### 12.2 Location Updates Lifecycle

- Start updates when MapScreen enters composition
- Stop updates when MapScreen leaves composition
- Use `DisposableEffect` to manage lifecycle

```kotlin
@Composable
fun MapScreen(...) {
    val locationHelper = remember { /* inject */ }
    
    DisposableEffect(Unit) {
        locationHelper.startLocationUpdates()
        onDispose {
            locationHelper.stopLocationUpdates()
        }
    }
    
    // ... rest of screen
}
```

---

## 13. Data Models

### 13.1 ClientMapItem

```kotlin
data class ClientMapItem(
    val clientId: String,
    val name: String,
    val businessName: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val phone: String?,
    val status: ClientMapStatus,
    val nextAppointmentDate: LocalDate?,
    val horsesCount: Int,
    val horsesDueSoon: Int,
    val horsesOverdue: Int
) : ClusterItem
```

### 13.2 RouteMapData

```kotlin
data class RouteMapData(
    val date: LocalDate,
    val startLocation: LatLng,
    val endLocation: LatLng,
    val startLocationName: String,
    val endLocationName: String,
    val stops: List<RouteStopMapItem>,
    val polylinePoints: List<LatLng>,
    val bounds: LatLngBounds,
    val totalDistanceMiles: Double,
    val totalDriveMinutes: Int,
    val estimatedEndTime: LocalTime,
    val lastLegDriveMinutes: Int
)
```

### 13.3 RouteStopMapItem

```kotlin
data class RouteStopMapItem(
    val appointmentId: String,
    val clientId: String,
    val clientName: String,
    val location: LatLng,
    val address: String,
    val order: Int,
    val estimatedArrival: LocalTime,
    val appointmentDurationMinutes: Int,
    val driveMinutesFromPrevious: Int,
    val isCompleted: Boolean,
    val horseCount: Int
)
```

### 13.4 MapUiState

```kotlin
data class MapUiState(
    val isLoading: Boolean = true,
    val clients: List<ClientMapItem> = emptyList(),
    val selectedClient: ClientMapItem? = null,
    val filter: MapFilter = MapFilter.ALL_CLIENTS,
    val initialPosition: LatLng? = null,
    val homeLocation: LatLng? = null,
    val serviceRadius: ServiceRadius? = null,
    val locationEnabled: Boolean = false,
    val showLocationBanner: Boolean = true,
    val error: String? = null
)
```

---

## 14. File References

### 14.1 Core Files

| File Path | Purpose |
|-----------|---------|
| `core/location/LocationPermissionHandler.kt` | Permission state management |
| `core/location/LocationHelper.kt` | Current location tracking |
| `core/location/GeocodingService.kt` | Address to coordinates |
| `core/navigation/NavigationHelper.kt` | Navigation app handoff |
| `core/domain/model/ClientMapStatus.kt` | Pin status enum |
| `core/domain/model/MapFilter.kt` | Filter options enum |
| `core/domain/model/NavigationApp.kt` | Navigation app enum |

### 14.2 Feature Files

| File Path | Purpose |
|-----------|---------|
| `feature/map/ui/MapScreen.kt` | Client map screen |
| `feature/map/ui/MapViewModel.kt` | Map business logic |
| `feature/map/ui/RouteMapScreen.kt` | Route visualization |
| `feature/map/ui/RouteMapViewModel.kt` | Route map logic |
| `feature/map/ui/components/ClientMarker.kt` | Custom marker icons |
| `feature/map/ui/components/ClusterMarkerContent.kt` | Cluster UI |
| `feature/map/ui/components/ClientInfoBottomSheet.kt` | Client details |
| `feature/map/ui/components/MapFilterDropdown.kt` | Filter dropdown |
| `feature/map/ui/components/RouteSummaryBar.kt` | Route stats bar |
| `feature/map/ui/components/RouteStopItem.kt` | Stop list item |

### 14.3 Navigation Graph

| Route | Screen |
|-------|--------|
| `/map` | ClientMapScreen |
| `/map/route?date={date}` | RouteMapScreen |

---

## 15. Acceptance Criteria

### 15.1 Client Map Display

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-01 | 10 clients with geocoded addresses | Open Map screen | All 10 pins visible on map |
| AC-009-02 | Client with upcoming appointment | View map | Pin is blue |
| AC-009-03 | Client with horse due in 5 days, no appointment | View map | Pin is yellow |
| AC-009-04 | Client with horse 3 days overdue | View map | Pin is red |
| AC-009-05 | Client with no appointments or due horses | View map | Pin is gray |
| AC-009-06 | Zoom out to show 50 clients | Map zoomed out | Pins cluster with count badge |
| AC-009-07 | Tap clustered marker | Cluster tapped | Map zooms to show individual pins |

### 15.2 Client Selection

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-08 | Map with client pins | Tap a pin | Bottom sheet appears with client info |
| AC-009-09 | Client has appointment Jan 25 | View bottom sheet | Shows "Next: Jan 25 (in X days)" |
| AC-009-10 | Client has 2 horses due soon | View bottom sheet | Shows "🟡 2 horses due soon" |
| AC-009-11 | Bottom sheet open | Tap "Navigate" | Navigation app opens with destination |
| AC-009-12 | Client has phone number | Tap "Call" | Phone dialer opens with number |
| AC-009-13 | Bottom sheet open | Tap "Schedule" | Navigate to create appointment for client |

### 15.3 Filtering

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-14 | 5 clients total, 2 with today's appointments | Select "Today's Stops" filter | Only 2 pins visible |
| AC-009-15 | Filter set to "Due Soon", 0 matches | Filter applied | Map shows "No clients match this filter" |
| AC-009-16 | Filter reduces visible pins | Filter changed | Camera animates to fit visible pins |

### 15.4 Route Map

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-17 | 4 appointments today | Open route map | 4 numbered markers plus home markers |
| AC-009-18 | Route calculated | View map | Blue polyline connects all stops |
| AC-009-19 | Route with 50 miles total | View summary bar | Shows "50 mi" |
| AC-009-20 | Stop 2 is completed | View map | Stop 2 marker is gray |
| AC-009-21 | Tap stop marker | Marker tapped | Navigate to appointment detail |
| AC-009-22 | Tap "Start" button | Button tapped | Navigation opens to first stop |

### 15.5 Navigation

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-23 | Google Maps installed, preference set | Tap Navigate | Google Maps opens in navigation mode |
| AC-009-24 | Waze installed, preference set | Tap Navigate | Waze opens with destination |
| AC-009-25 | No nav app installed | Tap Navigate | Opens Google Maps in browser |

### 15.6 Location Permission

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-26 | Location permission not granted | Open Map | Rationale bottom sheet appears |
| AC-009-27 | Permission denied, map open | View map | Map centers on home base, no current location |
| AC-009-28 | Permission permanently denied | Tap "Allow" | "Open Settings" option shown |
| AC-009-29 | Location permission granted | Open Map | Current location marker visible, map centered |

### 15.7 Service Radius

| ID | Given | When | Then |
|----|-------|------|------|
| AC-009-30 | Service radius enabled, 50 miles | View map | Circle overlay visible around home base |
| AC-009-31 | Service radius disabled | View map | No circle overlay |
| AC-009-32 | Home base not set, radius enabled | View map | No circle (missing center point) |

---

## 16. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Map initial load | < 2s | First paint to interactive |
| Pin rendering (100 clients) | < 500ms | Markers visible |
| Cluster calculation | < 200ms | On zoom change |
| Client info sheet open | < 100ms | Tap to sheet visible |
| Route polyline render | < 300ms | Points to line visible |
| Navigation handoff | < 500ms | Button tap to app switch |
| Geocoding API call | < 2s | Address to coordinates |
| Pan/zoom | 60 FPS | Smooth scrolling |

---

## 17. Offline Behavior

### 17.1 Map Tiles

- Google Maps SDK caches visited tiles automatically
- Offline map download not supported (requires Google Maps API license)
- Show placeholder for uncached areas

### 17.2 Client Data

- Client locations from local database
- Pins display offline
- Client info sheet works offline
- Geocoding requires network (queue for later)

### 17.3 Route Data

- Route polyline from local database
- Route map works offline if route was calculated online
- "Start Navigation" may fail if nav app needs network

### 17.4 Offline Indicators

When offline:
- Show offline banner at top of map
- Disable "Refresh" button
- Queue any new client addresses for geocoding when online

---

## 18. Error Handling

### 18.1 Map Load Failure

```kotlin
GoogleMap(
    onMapLoadFailed = { exception ->
        showSnackbar("Unable to load map: ${exception.message}")
    }
)
```

Display fallback message if map fails to load (e.g., no Google Play Services).

### 18.2 Geocoding Failures

- Log failure for analytics
- Mark client as "needs geocoding"
- Retry on next app launch or when address is edited
- Show indicator on client card

### 18.3 Navigation Failures

- Catch `ActivityNotFoundException`
- Fallback to web maps
- Show toast: "Opening in browser..."

---

## 19. Testing Scenarios

### 19.1 Unit Tests

```kotlin
class MapViewModelTest {
    @Test
    fun `determines HAS_APPOINTMENT status when appointment exists`()
    
    @Test
    fun `determines OVERDUE status when horse is past due`()
    
    @Test
    fun `applies TODAY_APPOINTMENTS filter correctly`()
    
    @Test
    fun `selects client and updates selectedClient state`()
}

class GeocodingServiceTest {
    @Test
    fun `returns Success for valid address`()
    
    @Test
    fun `returns NotFound for invalid address`()
    
    @Test
    fun `returns Error when geocoder throws IOException`()
}
```

### 19.2 UI Tests

```kotlin
class MapScreenTest {
    @Test
    fun `displays client pins on map`()
    
    @Test
    fun `shows bottom sheet when pin tapped`()
    
    @Test
    fun `filters pins when filter changed`()
    
    @Test
    fun `shows permission rationale when location not granted`()
}
```

### 19.3 Integration Tests

```kotlin
class NavigationHelperTest {
    @Test
    fun `opens Google Maps with correct coordinates`()
    
    @Test
    fun `opens Waze when preferred`()
    
    @Test
    fun `falls back to browser when apps not installed`()
}
```

---

## 20. Security Considerations

### 20.1 Location Data

- Store location with client data (encrypted at rest via Room)
- Don't log precise coordinates in production
- Location permission follows Android best practices

### 20.2 API Keys

- Google Maps API key in `local.properties` (not committed)
- Key restricted to app package name and SHA-1 fingerprint
- Separate keys for debug and release builds

### 20.3 Navigation Intent

- Use proper Intent flags
- Don't pass sensitive data through navigation intents
- Validate coordinates before navigation

---

## 21. Future Considerations

### 21.1 Potential Enhancements

- Offline map caching (requires enterprise Maps license)
- Traffic layer toggle
- Satellite view option
- Custom map styling (dark mode)
- Geofencing for arrival detection
- Route optimization from map view
- Bulk client selection for scheduling

### 21.2 Not in Scope

- Custom map tiles
- Indoor navigation
- Turn-by-turn within app
- Live location sharing
- Multiple vehicle support
