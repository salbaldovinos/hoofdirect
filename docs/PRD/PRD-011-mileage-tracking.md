# PRD-011: Mileage Tracking

**Priority**: P1  
**Phase**: 3 - Route Intelligence  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Track business mileage for tax deduction purposes with both manual entry and optional automatic GPS tracking.

### Business Value
- Simplifies IRS Schedule C deductions
- Saves hours of manual record-keeping
- Automatic tracking reduces forgotten entries
- Professional mileage reports for tax filing

### Success Metrics
| Metric | Target |
|--------|--------|
| Tracking accuracy | Within 5% |
| Manual entry time | < 15 seconds |
| Battery impact (auto) | < 5%/day |
| Export success rate | 100% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-011-01 | Farrier | Log miles manually | I track when auto fails | P0 |
| US-011-02 | Farrier | Auto-track my routes | I don't forget to log | P1 |
| US-011-03 | Farrier | See daily/weekly totals | I know my mileage | P0 |
| US-011-04 | Farrier | Export for taxes | I file my Schedule C | P0 |
| US-011-05 | Farrier | See annual deduction estimate | I plan for taxes | P1 |
| US-011-06 | Farrier | Link trips to appointments | Records are complete | P1 |

---

## Functional Requirements

### FR-011-01: Manual Entry
- Date (default: today)
- Start location (address or "Home")
- End location (address or client name)
- Miles driven (numeric)
- Purpose: Client Visit, Supply Run, Training, Other
- Link to appointment (optional)
- Notes (optional)

### FR-011-02: Auto-Tracking (Optional)
- User opt-in required
- Track during working hours only
- Geofence detection for client arrival/departure
- Background location updates
- Battery-efficient (significant location changes)
- Trip segmentation
- Review before saving

### FR-011-03: Trip Linking
- Auto-link trips to appointments by time/location
- Manual link option
- Linked trips show in appointment history

### FR-011-04: Mileage Reports
- Daily summary
- Weekly summary
- Monthly summary
- Annual summary
- Filter by purpose
- Filter by date range

### FR-011-05: Tax Deduction Estimate
```
IRS Standard Mileage Rate (2024): $0.67/mile

Annual Summary:
Total Business Miles: 12,450
Estimated Deduction: $8,341.50

Note: Consult a tax professional for advice.
```

### FR-011-06: Export
- CSV format (Schedule C compatible)
- Date range selection
- Columns: Date, Description, Miles, Purpose, Deduction
- Email or download option

---

## Technical Implementation

```kotlin
// MileageRepository.kt
class MileageRepository @Inject constructor(
    private val mileageLogDao: MileageLogDao,
    private val appointmentDao: AppointmentDao,
    private val syncManager: SyncManager
) {
    suspend fun logTrip(trip: MileageTrip): Result<MileageTrip> {
        return try {
            val entity = trip.toEntity().copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE
            )
            mileageLogDao.insert(entity)
            
            syncManager.queueChange(
                entityType = "mileage_logs",
                entityId = entity.id,
                operation = SyncOperation.INSERT,
                payload = Json.encodeToString(entity)
            )
            
            Result.success(trip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getDailyTotal(date: LocalDate): Flow<Double> {
        return mileageLogDao.getTotalForDate(date)
    }
    
    fun getAnnualSummary(year: Int): Flow<MileageSummary> {
        return mileageLogDao.getAnnualSummary(year).map { entries ->
            val totalMiles = entries.sumOf { it.miles }
            MileageSummary(
                totalMiles = totalMiles,
                tripCount = entries.size,
                estimatedDeduction = totalMiles * IRS_MILEAGE_RATE_2024,
                byPurpose = entries.groupBy { it.purpose }
                    .mapValues { (_, trips) -> trips.sumOf { it.miles } }
            )
        }
    }
    
    suspend fun exportToCsv(
        startDate: LocalDate,
        endDate: LocalDate
    ): File {
        val entries = mileageLogDao.getEntriesInRange(startDate, endDate)
        
        val csv = buildString {
            appendLine("Date,Description,Start,End,Miles,Purpose,Deduction")
            entries.forEach { entry ->
                appendLine(
                    "${entry.date}," +
                    "\"${entry.description}\"," +
                    "\"${entry.startAddress}\"," +
                    "\"${entry.endAddress}\"," +
                    "${entry.miles}," +
                    "${entry.purpose}," +
                    "${entry.miles * IRS_MILEAGE_RATE_2024}"
                )
            }
        }
        
        val file = File(context.cacheDir, "mileage_${startDate}_${endDate}.csv")
        file.writeText(csv)
        return file
    }
    
    companion object {
        const val IRS_MILEAGE_RATE_2024 = 0.67
    }
}

// MileageTrackingService.kt
@AndroidEntryPoint
class MileageTrackingService : Service() {
    
    @Inject lateinit var locationClient: FusedLocationProviderClient
    @Inject lateinit var mileageRepository: MileageRepository
    @Inject lateinit var geofenceManager: GeofenceManager
    
    private var currentTrip: TripInProgress? = null
    private val locationPoints = mutableListOf<Location>()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                locationPoints.add(location)
                updateTripDistance()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_ARRIVED_AT_CLIENT -> handleClientArrival(
                intent.getStringExtra(EXTRA_CLIENT_ID)
            )
        }
        return START_STICKY
    }
    
    private fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000)
            .setMinUpdateDistanceMeters(100f)
            .build()
        
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        
        currentTrip = TripInProgress(
            startTime = Instant.now(),
            startLocation = locationPoints.lastOrNull()
        )
    }
    
    private fun stopTracking() {
        locationClient.removeLocationUpdates(locationCallback)
        
        currentTrip?.let { trip ->
            val totalMiles = calculateTotalMiles(locationPoints)
            
            viewModelScope.launch {
                mileageRepository.logTrip(
                    MileageTrip(
                        date = LocalDate.now(),
                        startAddress = trip.startLocation?.toAddress() ?: "Unknown",
                        endAddress = locationPoints.lastOrNull()?.toAddress() ?: "Unknown",
                        miles = totalMiles,
                        purpose = MileagePurpose.CLIENT_VISIT,
                        autoTracked = true
                    )
                )
            }
        }
        
        locationPoints.clear()
        currentTrip = null
    }
    
    private fun calculateTotalMiles(points: List<Location>): Double {
        if (points.size < 2) return 0.0
        
        var totalMeters = 0f
        for (i in 1 until points.size) {
            totalMeters += points[i - 1].distanceTo(points[i])
        }
        return totalMeters / 1609.34 // Convert to miles
    }
}

// GeofenceManager.kt
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val clientRepository: ClientRepository
) {
    suspend fun setupClientGeofences() {
        val clients = clientRepository.getActiveClientsWithCoordinates()
        
        val geofences = clients.mapNotNull { client ->
            if (client.latitude != null && client.longitude != null) {
                Geofence.Builder()
                    .setRequestId(client.id)
                    .setCircularRegion(client.latitude, client.longitude, 150f)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or 
                        Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()
            } else null
        }
        
        val request = GeofencingRequest.Builder()
            .addGeofences(geofences)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .build()
        
        geofencingClient.addGeofences(request, geofencePendingIntent)
    }
}
```

---

## Data Model

```kotlin
@Entity(
    tableName = "mileage_logs",
    indices = [Index(value = ["user_id"]), Index(value = ["date"])]
)
data class MileageLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    val date: LocalDate,
    @ColumnInfo(name = "start_address") val startAddress: String?,
    @ColumnInfo(name = "end_address") val endAddress: String?,
    val miles: Double,
    val purpose: MileagePurpose,
    @ColumnInfo(name = "appointment_id") val appointmentId: String?,
    val notes: String?,
    @ColumnInfo(name = "auto_tracked") val autoTracked: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)

enum class MileagePurpose {
    CLIENT_VISIT,
    SUPPLY_RUN,
    TRAINING,
    OTHER
}

data class MileageSummary(
    val totalMiles: Double,
    val tripCount: Int,
    val estimatedDeduction: Double,
    val byPurpose: Map<MileagePurpose, Double>
)

data class MileageTrip(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val startAddress: String?,
    val endAddress: String?,
    val miles: Double,
    val purpose: MileagePurpose,
    val appointmentId: String? = null,
    val notes: String? = null,
    val autoTracked: Boolean = false
)
```

---

## UI Specifications

### Mileage Screen
```
┌─────────────────────────────────────────┐
│ Mileage                    [+ Add Trip] │
├─────────────────────────────────────────┤
│                                         │
│  2024 Summary                           │
│  ─────────────────────────────────────  │
│  Total Miles: 12,450                    │
│  Estimated Deduction: $8,341.50         │
│  [Export Report]                        │
│                                         │
│  January 2024             1,245 miles   │
│  ─────────────────────────────────────  │
│                                         │
│  Today - Jan 13                47.3 mi  │
│  ┌─────────────────────────────────┐   │
│  │ Johnson Ranch → Williams Farm   │   │
│  │ 23.5 mi · Client Visit         │   │
│  │ Linked: 8:00 AM appointment    │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ Williams Farm → Home            │   │
│  │ 23.8 mi · Client Visit         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Yesterday - Jan 12           52.1 mi   │
│  ┌─────────────────────────────────┐   │
│  │ Home → Martinez Stables        │   │
│  │ 28.2 mi · Client Visit         │   │
│  └─────────────────────────────────┘   │
│  ...                                    │
│                                         │
│  Auto-Tracking                 [  ON ]  │
│  Track mileage automatically during    │
│  working hours                         │
│                                         │
└─────────────────────────────────────────┘
```

### Add Trip Modal
```
┌─────────────────────────────────────────┐
│ [×]  Add Trip                   [Save]  │
├─────────────────────────────────────────┤
│                                         │
│  Date                                   │
│  ┌─────────────────────────────────┐   │
│  │ January 13, 2024               │   │
│  └─────────────────────────────────┘   │
│                                         │
│  From                                   │
│  ┌─────────────────────────────────┐   │
│  │ Home                           │   │
│  └─────────────────────────────────┘   │
│                                         │
│  To                                     │
│  ┌─────────────────────────────────┐   │
│  │ Johnson Ranch                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Miles                                  │
│  ┌─────────────────────────────────┐   │
│  │ 23.5                           │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Purpose                                │
│  (●) Client Visit  ( ) Supply Run      │
│  ( ) Training      ( ) Other           │
│                                         │
│  Link to Appointment (optional)        │
│  ┌─────────────────────────────────┐   │
│  │ Select appointment...          │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class MileageRepositoryTest {
    @Test
    fun `getAnnualSummary calculates correct deduction`() = runTest {
        insertTestTrips(totalMiles = 1000.0)
        
        val summary = repository.getAnnualSummary(2024).first()
        
        assertEquals(1000.0, summary.totalMiles)
        assertEquals(670.0, summary.estimatedDeduction, 0.01)
    }
    
    @Test
    fun `exportToCsv generates valid CSV`() = runTest {
        val file = repository.exportToCsv(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        )
        
        assertTrue(file.exists())
        assertTrue(file.readText().contains("Date,Description"))
    }
}

class MileageTrackingServiceTest {
    @Test
    fun `calculateTotalMiles returns accurate distance`() {
        val points = listOf(
            Location("").apply { latitude = 30.0; longitude = -97.0 },
            Location("").apply { latitude = 30.1; longitude = -97.0 }
        )
        
        val miles = service.calculateTotalMiles(points)
        assertEquals(6.9, miles, 0.5) // ~6.9 miles
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-011-01 | Manual trip entry saves correctly | Integration test |
| AC-011-02 | Annual summary calculates deduction | Unit test |
| AC-011-03 | CSV export generates valid file | Integration test |
| AC-011-04 | Auto-tracking records trips | Manual test |
| AC-011-05 | Battery usage < 5%/day | Performance test |
| AC-011-06 | Works offline | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | For linking |
| Location permission | System | Required |
| Background location | System | For auto-tracking |
| Fused Location Provider | Library | Available |

---

## Privacy & Compliance

- Background location requires user consent
- Data stored locally, synced to user's account only
- No location sharing with third parties
- IRS rate updated annually with app updates
- Disclaimer: "Consult a tax professional"
