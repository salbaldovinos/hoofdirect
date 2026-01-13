# PRD-009: Maps Integration

**Priority**: P0  
**Phase**: 3 - Route Intelligence  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Integrate Google Maps to visualize client locations, display appointment routes, and enable seamless navigation to client addresses.

### Business Value
- Visual overview of client distribution
- Foundation for route optimization
- One-tap navigation to appointments
- Identifies geographic coverage gaps

### Success Metrics
| Metric | Target |
|--------|--------|
| Map load time | < 2 seconds |
| Pin display accuracy | 100% (geocoded clients) |
| Navigation handoff success | > 99% |
| Scroll/zoom performance | 60 FPS |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-009-01 | Farrier | See all clients on a map | I visualize my coverage | P0 |
| US-009-02 | Farrier | See today's route on map | I see my day visually | P0 |
| US-009-03 | Farrier | Navigate to client | I get directions easily | P0 |
| US-009-04 | Farrier | See client status from pin | I know which need visits | P1 |
| US-009-05 | Farrier | Filter map by criteria | I focus on relevant clients | P1 |
| US-009-06 | Farrier | See my service radius | I know my coverage area | P2 |

---

## Functional Requirements

### FR-009-01: Client Map View
- Display all geocoded clients as pins
- Pin clustering when zoomed out
- Current location indicator
- Center on current location or home base
- Smooth pan/zoom
- Offline map caching (limited area)

### FR-009-02: Pin Color Coding
```kotlin
Pin Colors by Client Status:
- Blue (Primary): Has upcoming appointment
- Yellow (Warning): Horses due soon (within 14 days)
- Gray: No upcoming appointments, not due
- Red (Error): Overdue horses
```

### FR-009-03: Pin Selection
- Tap pin → Show client info bottom sheet
- Bottom sheet shows:
  - Client name
  - Address
  - Next appointment (if any)
  - Horses due status
  - Quick actions: Navigate, Call, Schedule

### FR-009-04: Route Map View
- Show today's appointments as numbered pins
- Route line connecting stops in order
- Start point (home or current location)
- End point (home or last stop)
- Current position on route
- Estimated arrival times

### FR-009-05: Navigation Handoff
- "Navigate" button on client/appointment
- Choice: Google Maps or Waze
- Pre-fill destination address
- Remember preference

### FR-009-06: Map Filters
- All clients
- Today's appointments
- This week's appointments
- Due soon horses
- Custom date range

### FR-009-07: Service Radius (Optional)
- Circle overlay around home base
- Radius from user settings
- Visual indicator of coverage

---

## Technical Implementation

```kotlin
// MapScreen.kt
@Composable
fun ClientMapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onClientClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val clients by viewModel.clients.collectAsState()
    val selectedClient by viewModel.selectedClient.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val filter by viewModel.filter.collectAsState()
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: viewModel.homeLocation,
            10f
        )
    }
    
    Scaffold(
        topBar = {
            MapTopBar(
                filter = filter,
                onFilterChange = viewModel::setFilter
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = true,
                    mapType = MapType.NORMAL
                )
            ) {
                // Client markers with clustering
                Clustering(
                    items = clients.map { it.toClusterItem() },
                    onClusterClick = { /* Zoom in */ },
                    onClusterItemClick = { item ->
                        viewModel.selectClient(item.clientId)
                        false
                    },
                    clusterContent = { cluster ->
                        ClusterMarker(count = cluster.size)
                    },
                    clusterItemContent = { item ->
                        ClientMarker(status = item.status)
                    }
                )
                
                // Service radius circle
                if (viewModel.showServiceRadius) {
                    Circle(
                        center = viewModel.homeLocation,
                        radius = viewModel.serviceRadiusMeters,
                        strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                }
            }
            
            // Client info bottom sheet
            selectedClient?.let { client ->
                ClientInfoSheet(
                    client = client,
                    onNavigate = { onNavigate(client.id) },
                    onCall = { viewModel.callClient(client) },
                    onSchedule = { viewModel.scheduleAppointment(client) },
                    onDismiss = { viewModel.clearSelection() }
                )
            }
        }
    }
}

// RouteMapScreen.kt
@Composable
fun RouteMapScreen(
    viewModel: RouteViewModel = hiltViewModel(),
    onStopClick: (String) -> Unit
) {
    val route by viewModel.todayRoute.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                route?.bounds?.center ?: currentLocation,
                11f
            )
        }
    ) {
        route?.let { routePlan ->
            // Route polyline
            Polyline(
                points = routePlan.polylinePoints,
                color = MaterialTheme.colorScheme.primary,
                width = 8f
            )
            
            // Start marker
            Marker(
                state = MarkerState(position = routePlan.startLocation),
                icon = startMarkerIcon,
                title = "Start"
            )
            
            // Stop markers (numbered)
            routePlan.stops.forEachIndexed { index, stop ->
                Marker(
                    state = MarkerState(position = stop.location),
                    icon = numberedMarkerIcon(index + 1),
                    title = stop.clientName,
                    snippet = "ETA: ${stop.estimatedArrival}",
                    onClick = {
                        onStopClick(stop.appointmentId)
                        true
                    }
                )
            }
            
            // End marker
            Marker(
                state = MarkerState(position = routePlan.endLocation),
                icon = endMarkerIcon,
                title = "End"
            )
        }
        
        // Current location
        currentLocation?.let {
            Marker(
                state = MarkerState(position = it),
                icon = currentLocationIcon
            )
        }
    }
}

// NavigationHelper.kt
object NavigationHelper {
    fun navigateTo(
        context: Context,
        address: String,
        latitude: Double,
        longitude: Double,
        preferredApp: NavigationApp = NavigationApp.GOOGLE_MAPS
    ) {
        val uri = when (preferredApp) {
            NavigationApp.GOOGLE_MAPS -> {
                Uri.parse("google.navigation:q=$latitude,$longitude")
            }
            NavigationApp.WAZE -> {
                Uri.parse("waze://?ll=$latitude,$longitude&navigate=yes")
            }
        }
        
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(preferredApp.packageName)
        }
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to web
            val webUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
            )
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
}

enum class NavigationApp(val packageName: String) {
    GOOGLE_MAPS("com.google.android.apps.maps"),
    WAZE("com.waze")
}
```

---

## Data Model

```kotlin
data class ClientMapItem(
    val clientId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val status: ClientMapStatus,
    val nextAppointment: LocalDate?,
    val horsesCount: Int
) : ClusterItem {
    override fun getPosition() = LatLng(latitude, longitude)
    override fun getTitle() = name
    override fun getSnippet() = "$horsesCount horses"
    override fun getZIndex() = 0f
}

enum class ClientMapStatus {
    HAS_APPOINTMENT,  // Blue
    DUE_SOON,        // Yellow
    OVERDUE,         // Red
    INACTIVE         // Gray
}

data class RouteMapData(
    val startLocation: LatLng,
    val endLocation: LatLng,
    val stops: List<RouteStopMapItem>,
    val polylinePoints: List<LatLng>,
    val bounds: LatLngBounds,
    val totalDistanceMiles: Double,
    val totalDriveMinutes: Int
)

data class RouteStopMapItem(
    val appointmentId: String,
    val clientName: String,
    val location: LatLng,
    val order: Int,
    val estimatedArrival: LocalTime,
    val isCompleted: Boolean
)

enum class MapFilter {
    ALL_CLIENTS,
    TODAY_APPOINTMENTS,
    THIS_WEEK,
    DUE_SOON
}
```

---

## UI Specifications

### Client Map Screen
```
┌─────────────────────────────────────────┐
│ Clients Map              [Filter ▼] 🎯  │
├─────────────────────────────────────────┤
│                                         │
│           ┌───┐                         │
│        ●  │ 5 │  ●                     │
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

Legend:
🔵 = Has upcoming appointment
🟡 = Horses due soon
🔴 = Overdue
●  = No appointments scheduled
[5] = Cluster with 5 clients
```

### Route Map Screen
```
┌─────────────────────────────────────────┐
│ [←] Today's Route           [Start ▶]  │
├─────────────────────────────────────────┤
│                                         │
│    🏠 ─── 1️⃣ ─── 2️⃣ ─── 3️⃣ ─── 🏠   │
│                                         │
│         (route line connecting)         │
│                                         │
│    📍 (current position)               │
│                                         │
├─────────────────────────────────────────┤
│ 47.3 mi · 1h 52m drive · 4 stops       │
├─────────────────────────────────────────┤
│ 1️⃣ Johnson Ranch      8:00 AM  15 min  │
│ 2️⃣ Williams Farm     10:15 AM  12 min  │
│ 3️⃣ Martinez Stables  11:45 AM  18 min  │
│ 🏠 Home               1:30 PM  25 min  │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class MapViewModelTest {
    @Test
    fun `filter updates visible clients`() = runTest {
        viewModel.setFilter(MapFilter.DUE_SOON)
        val clients = viewModel.clients.first()
        assertTrue(clients.all { it.status == ClientMapStatus.DUE_SOON })
    }
}

@Composable
@Preview
fun ClientMapPreview() {
    ClientMapScreen(
        clients = previewClients,
        onClientClick = {},
        onNavigate = {}
    )
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-009-01 | All geocoded clients show on map | Integration test |
| AC-009-02 | Pin colors match status | UI test |
| AC-009-03 | Navigation opens Google Maps/Waze | Manual test |
| AC-009-04 | Route line displays correctly | UI test |
| AC-009-05 | Clustering works when zoomed out | Manual test |
| AC-009-06 | Map performs at 60 FPS | Performance test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-003 (Clients) | Internal | Required |
| PRD-005 (Appointments) | Internal | Required |
| Google Maps SDK | External | Required |
| Location permission | System | Required |
| maps-compose library | Library | Available |
