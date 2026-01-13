# FRD-011: Mileage Tracking

**Source PRD**: PRD-011-mileage-tracking.md  
**Priority**: P1  
**Phase**: 3 - Route Intelligence  
**Dependencies**: FRD-005 (Appointments), FRD-009 (Maps - Location Services)

---

## Overview

Track business mileage for tax deduction purposes with both manual entry and optional automatic GPS tracking. Farriers drive significant miles daily; accurate tracking enables substantial IRS Schedule C deductions while reducing manual record-keeping.

### Success Metrics
| Metric | Target |
|--------|--------|
| Manual entry completion | < 15 seconds |
| Tracking accuracy | Within 5% of actual |
| Battery impact (auto-tracking) | < 5% per day |
| Export success rate | 100% |

---

## Mileage Screen

### Route
`/mileage` (accessible from Settings or dedicated menu item)

### Screen Structure

**Top App Bar**
- Title: "Mileage"
- Action: FAB "+ Add Trip" (bottom-end positioned)

**Annual Summary Card** (top, always visible)
- Card with primary container color
- Shows current year (e.g., "2024 Summary")
- Total Miles: formatted with comma separator (e.g., "12,450")
- Estimated Deduction: formatted as currency (e.g., "$8,341.50")
- "Export Report" text button → opens ExportOptionsSheet
- Tap card → navigates to annual breakdown screen

**Monthly Section Header**
- Month/Year label (e.g., "January 2024")
- Monthly total miles aligned right
- Collapsible (tap to expand/collapse)

**Trip List**
- Grouped by date within month
- Date header shows day of week, date, and daily total (e.g., "Today - Jan 13     47.3 mi")
- Each trip as TripCard component
- Lazy loading with pagination (load 30 trips initially, more on scroll)
- Empty state: "No trips recorded. Tap + to add your first trip."

**Auto-Tracking Toggle** (bottom section)
- Switch with label "Auto-Tracking"
- Subtitle: "Track mileage automatically during working hours"
- Switch state persisted in UserPreferencesManager
- Tapping when OFF with location permission not granted → triggers permission flow

### Screen State
```kotlin
data class MileageScreenState(
    val isLoading: Boolean,
    val annualSummary: MileageSummary?,
    val tripsByMonth: Map<YearMonth, List<MileageTrip>>,
    val autoTrackingEnabled: Boolean,
    val isAutoTracking: Boolean,  // Currently tracking a trip
    val currentTripMiles: Double?,  // Miles accumulated in active trip
    val error: String?
)
```

---

## Trip Card Component

### Layout
- Start location → End location (arrow between)
- Miles with one decimal place (e.g., "23.5 mi")
- Purpose badge (CLIENT_VISIT, SUPPLY_RUN, TRAINING, OTHER)
- If linked to appointment: "Linked: 8:00 AM appointment" with link icon
- Auto-tracked indicator: small GPS icon if autoTracked=true
- Tap → opens TripDetailSheet
- Long press → shows context menu (Edit, Delete)

### Trip Purpose Badges
| Purpose | Label | Color |
|---------|-------|-------|
| CLIENT_VISIT | "Client Visit" | Primary |
| SUPPLY_RUN | "Supply Run" | Tertiary |
| TRAINING | "Training" | Secondary |
| OTHER | "Other" | Surface variant |

---

## Add Trip Flow

### Trigger
- Tap FAB "+ Add Trip"
- Opens AddTripSheet (bottom sheet, expanded)

### AddTripSheet Fields

**Date Field**
- Default: Today
- Tap → DatePickerDialog
- Cannot select future dates
- Display: "January 13, 2024" format

**From Field**
- Text input with autocomplete
- Quick options:
  - "Home" (user's home address from settings)
  - Recent locations (last 5 unique start locations)
  - Client names with addresses
- Tap → LocationInputSheet with search

**To Field**
- Same behavior as From field
- Quick options include all clients
- Tap → LocationInputSheet with search

**Miles Field**
- Numeric input with decimal keyboard
- Required field
- Validation: > 0, <= 1000
- One decimal place displayed
- "Calculate" link → opens route calculation (if both addresses valid)

**Calculate Distance**
- When both From and To are valid addresses
- "Calculate" link appears next to Miles field
- Tap → calls Google Distance Matrix API
- Shows loading spinner during calculation
- On success: populates Miles field with driving distance
- On failure: shows error toast, allows manual entry

**Purpose Selector**
- Segmented button group
- Options: Client Visit, Supply Run, Training, Other
- Default: Client Visit
- Required selection

**Link to Appointment (Optional)**
- Dropdown selector
- Shows appointments from selected date
- Format: "8:00 AM - Johnson Ranch"
- Can select "None"
- If date changed, appointment list refreshes

**Notes Field (Optional)**
- Multi-line text input
- Max 500 characters
- Placeholder: "Add notes (optional)"

### Validation Rules
| Field | Rule | Error Message |
|-------|------|---------------|
| Date | Not future | "Date cannot be in the future" |
| Miles | > 0 | "Miles must be greater than 0" |
| Miles | <= 1000 | "Miles cannot exceed 1,000" |
| Purpose | Required | "Please select a purpose" |

### Save Behavior
1. Validate all fields
2. Create MileageTrip with syncStatus = PENDING_CREATE
3. Insert into local database
4. Queue sync operation
5. Dismiss sheet
6. Show success snackbar: "Trip saved"
7. Trip appears in list immediately

### Cancel Behavior
- Tap outside sheet or X button
- If fields modified: "Discard changes?" confirmation
- If no changes: dismiss immediately

---

## Edit Trip Flow

### Trigger
- Long press trip card → "Edit" option
- Or tap trip card → TripDetailSheet → "Edit" button

### Behavior
- Opens AddTripSheet pre-filled with trip data
- Title changes to "Edit Trip"
- Save updates existing record (syncStatus = PENDING_UPDATE)
- Delete option available (red text button at bottom)

### Delete Trip
- "Delete Trip" button at bottom of edit sheet
- Confirmation dialog: "Delete this trip? This cannot be undone."
- On confirm: soft delete (syncStatus = PENDING_DELETE)
- Trip removed from UI immediately
- Snackbar: "Trip deleted" with "Undo" action (5 seconds)
- Undo: restores trip with previous syncStatus

---

## Trip Detail Sheet

### Trigger
- Tap trip card

### Content
- Full trip details displayed read-only
- Date with day of week
- From address (full)
- To address (full)
- Miles
- Purpose badge
- Linked appointment (if any) - tap to view appointment
- Notes (if any)
- Auto-tracked badge (if applicable)
- Created timestamp

### Actions
- "Edit" button → opens edit flow
- "Delete" button → delete confirmation
- Close: tap outside or drag down

---

## Auto-Tracking Feature

### Opt-In Flow
1. User enables "Auto-Tracking" toggle
2. If location permission not granted:
   - Show permission rationale sheet
   - Request ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION
   - If denied: toggle reverts to OFF, show explanation
3. If permission granted:
   - Show setup sheet explaining feature
   - Configure working hours (default: 7 AM - 6 PM)
   - Enable toggle, start MileageTrackingService

### Permission Requirements
- ACCESS_FINE_LOCATION: Required for accurate tracking
- ACCESS_BACKGROUND_LOCATION (Android 10+): Required for tracking when app backgrounded
- Shows in notification: "Mileage tracking active"

### Working Hours Configuration
- Start time picker (default: 7:00 AM)
- End time picker (default: 6:00 PM)
- Days of week checkboxes (default: Mon-Fri)
- Stored in UserPreferencesManager

### MileageTrackingService Behavior

**Service Lifecycle**
- Foreground service with persistent notification
- Starts automatically when:
  - Auto-tracking enabled
  - Current time within working hours
  - Working day (based on day-of-week settings)
- Stops automatically outside working hours
- WorkManager schedules start/stop based on working hours

**Location Tracking**
- Uses FusedLocationProviderClient
- LocationRequest configuration:
  - Priority: PRIORITY_HIGH_ACCURACY
  - Interval: 60 seconds
  - Min update distance: 100 meters
  - Small displacement: true (battery optimization)
- Location points stored in memory during trip

**Trip Detection**
- Trip starts when:
  - User moves > 0.5 miles from last known position
  - Significant location change detected
- Trip ends when:
  - User stationary for > 5 minutes
  - Service stopped (end of working hours)
  - User manually ends trip

**Geofence Integration**
- Register geofences for client locations with upcoming appointments
- Radius: 200 meters
- On GEOFENCE_TRANSITION_ENTER:
  - End current trip segment
  - Record arrival at client
  - Auto-link to matching appointment
- On GEOFENCE_TRANSITION_EXIT:
  - Start new trip segment
  - Record departure from client

### Auto-Trip Review
When auto-tracked trip ends:
1. Calculate total distance from location points
2. Show notification: "Trip recorded: 23.5 miles"
3. Notification action: "Review" → opens TripReviewSheet
4. TripReviewSheet shows:
   - Start/end addresses (reverse geocoded)
   - Calculated miles
   - Start/end times
   - Map preview with route (optional)
   - "Save" or "Discard" buttons
   - Edit option for any field
5. If not reviewed within 24 hours: auto-save with default purpose "Client Visit"

### Notification Display
```
Mileage Tracking Active
Currently tracking: 12.3 miles
[Stop Tracking]
```

### Battery Optimization
- Use PRIORITY_BALANCED_POWER_ACCURACY when stationary
- Increase interval to 120 seconds when on known route
- Reduce accuracy when battery < 20%
- Target: < 5% battery per 8-hour tracking day

---

## Trip Linking

### Auto-Link Logic
When saving a trip (manual or auto):
1. Check for appointments on same date
2. For each appointment, check if:
   - Trip end address matches client address (fuzzy match), OR
   - Trip end time within 30 minutes of appointment time
3. If match found: suggest linking in AddTripSheet
4. Auto-tracked trips auto-link to geofence-triggered appointments

### Manual Link
- Dropdown in AddTripSheet shows same-day appointments
- Shows unlinked appointments first
- Already-linked appointments show "(Linked)" suffix
- Can link multiple trips to same appointment

### Linked Trip Display
- Trip card shows link icon + appointment time
- Appointment detail screen shows linked trips section
- Total mileage displayed on appointment

---

## Mileage Reports

### Report Views

**Daily Summary**
- Access: Date header in trip list
- Shows: Total miles, trip count, deduction estimate for day
- List of trips for that day

**Weekly Summary**
- Access: "View Weekly" from annual summary card actions
- Week picker (Mon-Sun)
- Table: Day | Miles | Trips | Deduction
- Weekly total row
- Chart: Bar chart of daily miles

**Monthly Summary**
- Access: Tap month header in trip list
- Shows: Total miles, trip count, deduction estimate
- Breakdown by purpose (pie chart)
- Day-by-day bar chart
- "Export Month" button

**Annual Summary**
- Access: Tap annual summary card
- Route: `/mileage/annual/{year}`
- Full year overview
- Month-by-month breakdown with totals
- Purpose breakdown for year
- Year-over-year comparison (if prior year data exists)
- "Export Year" button

### Filtering
- Filter by purpose: ALL, CLIENT_VISIT, SUPPLY_RUN, TRAINING, OTHER
- Filter by date range: This Week, This Month, This Quarter, This Year, Custom
- Filters persist during session

---

## Tax Deduction Estimate

### IRS Mileage Rate
- 2024 rate: $0.67 per mile
- Rate stored as constant, updated with app releases
- Display: "IRS Standard Mileage Rate: $0.67/mile"

### Calculation
```kotlin
val estimatedDeduction = totalMiles * IRS_MILEAGE_RATE
```

### Disclaimer
- All deduction displays include footnote:
- "* Estimated based on IRS standard mileage rate. Consult a tax professional for advice."
- Disclaimer appears in:
  - Annual summary card
  - Export files
  - Annual breakdown screen

---

## Export Feature

### Export Options Sheet

**Trigger**
- "Export Report" button on annual summary card
- "Export" action in report screens

**Options**
- Date Range:
  - This Month
  - This Quarter
  - This Year
  - Custom Range (date pickers)
- Format: CSV (Schedule C compatible)
- Delivery:
  - Download (save to device)
  - Email (open email app with attachment)
  - Share (system share sheet)

### CSV Format
```csv
Date,Description,Start,End,Miles,Purpose,Deduction
2024-01-13,"Johnson Ranch to Williams Farm","123 Main St, Silsbee TX","456 Oak Rd, Lumberton TX",23.5,Client Visit,$15.75
2024-01-13,"Williams Farm to Home","456 Oak Rd, Lumberton TX","789 Pine St, Silsbee TX",23.8,Client Visit,$15.95
```

### CSV Columns
| Column | Description | Format |
|--------|-------------|--------|
| Date | Trip date | YYYY-MM-DD |
| Description | Start → End location names | Text |
| Start | Full start address | Text |
| End | Full end address | Text |
| Miles | Distance driven | Decimal, 1 place |
| Purpose | Trip purpose | Text |
| Deduction | Calculated deduction | Currency |

### Export File
- Filename: `mileage_{start_date}_{end_date}.csv`
- Saved to Downloads folder or shared via intent
- Success: "Report exported" snackbar
- Failure: "Export failed. Please try again." with retry option

### Email Export
- Opens default email app
- Subject: "Mileage Report {start_date} to {end_date}"
- Body: Brief summary + disclaimer
- Attachment: CSV file

---

## Location Input Sheet

### Trigger
- Tap From or To field in AddTripSheet

### Content

**Search Input**
- Auto-focus with keyboard
- Placeholder: "Enter address or client name"
- Real-time search as user types (debounce 300ms)

**Quick Options Section**
- "Home" (if home address configured)
- Recent locations (last 5 unique)
- Section divider

**Client Results**
- Client name with address subtitle
- Sorted by name
- Limited to 10 results

**Address Autocomplete Results**
- Google Places Autocomplete results
- Shows formatted address
- Limited to 5 results

### Selection
- Tap result → populates field, dismisses sheet
- Tap outside → dismisses without selection
- Selected location stored as:
  - Display name (e.g., "Johnson Ranch" or "Home")
  - Full address (for mapping/export)

---

## Data Models

### MileageLogEntity (Room)
```kotlin
@Entity(
    tableName = "mileage_logs",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["date"]),
        Index(value = ["appointment_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MileageLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: LocalDate,
    @ColumnInfo(name = "start_address") val startAddress: String?,
    @ColumnInfo(name = "start_display_name") val startDisplayName: String?,
    @ColumnInfo(name = "end_address") val endAddress: String?,
    @ColumnInfo(name = "end_display_name") val endDisplayName: String?,
    val miles: Double,
    val purpose: MileagePurpose,
    @ColumnInfo(name = "appointment_id") val appointmentId: String?,
    val notes: String?,
    @ColumnInfo(name = "auto_tracked") val autoTracked: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus
)
```

### MileageTrip (Domain)
```kotlin
data class MileageTrip(
    val id: String,
    val date: LocalDate,
    val startAddress: String?,
    val startDisplayName: String?,
    val endAddress: String?,
    val endDisplayName: String?,
    val miles: Double,
    val purpose: MileagePurpose,
    val appointmentId: String?,
    val linkedAppointment: AppointmentSummary?,  // Loaded for display
    val notes: String?,
    val autoTracked: Boolean,
    val createdAt: Instant
)
```

### MileageSummary (Domain)
```kotlin
data class MileageSummary(
    val period: SummaryPeriod,  // DAY, WEEK, MONTH, YEAR
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalMiles: Double,
    val tripCount: Int,
    val estimatedDeduction: Double,
    val byPurpose: Map<MileagePurpose, MileagePurposeStats>
)

data class MileagePurposeStats(
    val miles: Double,
    val tripCount: Int,
    val percentage: Double  // Of total miles
)
```

### MileagePurpose (Enum)
```kotlin
enum class MileagePurpose {
    CLIENT_VISIT,
    SUPPLY_RUN,
    TRAINING,
    OTHER
}
```

### AutoTrackingSettings (DataStore)
```kotlin
data class AutoTrackingSettings(
    val enabled: Boolean,
    val startTime: LocalTime,  // Default: 07:00
    val endTime: LocalTime,    // Default: 18:00
    val activeDays: Set<DayOfWeek>  // Default: Mon-Fri
)
```

### TripInProgress (In-Memory)
```kotlin
data class TripInProgress(
    val startTime: Instant,
    val startLocation: Location?,
    val locationPoints: List<Location>,
    val currentMiles: Double,
    val linkedAppointmentId: String?
)
```

---

## Repository Layer

### MileageRepository Interface
```kotlin
interface MileageRepository {
    // Trip CRUD
    suspend fun saveTrip(trip: MileageTrip): Result<MileageTrip>
    suspend fun updateTrip(trip: MileageTrip): Result<MileageTrip>
    suspend fun deleteTrip(tripId: String): Result<Unit>
    fun getTripById(tripId: String): Flow<MileageTrip?>
    
    // Trip lists
    fun getTripsForDate(date: LocalDate): Flow<List<MileageTrip>>
    fun getTripsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<MileageTrip>>
    fun getTripsByMonth(): Flow<Map<YearMonth, List<MileageTrip>>>
    
    // Summaries
    fun getDailySummary(date: LocalDate): Flow<MileageSummary>
    fun getWeeklySummary(weekStart: LocalDate): Flow<MileageSummary>
    fun getMonthlySummary(yearMonth: YearMonth): Flow<MileageSummary>
    fun getAnnualSummary(year: Int): Flow<MileageSummary>
    
    // Export
    suspend fun exportToCsv(startDate: LocalDate, endDate: LocalDate): Result<File>
    
    // Linking
    fun getUnlinkedAppointments(date: LocalDate): Flow<List<AppointmentSummary>>
    suspend fun linkTripToAppointment(tripId: String, appointmentId: String): Result<Unit>
}
```

### MileageLogDao
```kotlin
@Dao
interface MileageLogDao {
    @Query("SELECT * FROM mileage_logs WHERE user_id = :userId AND date = :date AND sync_status != 'PENDING_DELETE' ORDER BY created_at DESC")
    fun getTripsForDate(userId: String, date: LocalDate): Flow<List<MileageLogEntity>>
    
    @Query("SELECT * FROM mileage_logs WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate AND sync_status != 'PENDING_DELETE' ORDER BY date DESC, created_at DESC")
    fun getTripsInRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<MileageLogEntity>>
    
    @Query("SELECT SUM(miles) FROM mileage_logs WHERE user_id = :userId AND date = :date AND sync_status != 'PENDING_DELETE'")
    fun getTotalForDate(userId: String, date: LocalDate): Flow<Double?>
    
    @Query("SELECT * FROM mileage_logs WHERE user_id = :userId AND strftime('%Y', date) = :year AND sync_status != 'PENDING_DELETE'")
    fun getTripsForYear(userId: String, year: String): Flow<List<MileageLogEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: MileageLogEntity)
    
    @Update
    suspend fun update(trip: MileageLogEntity)
    
    @Query("UPDATE mileage_logs SET sync_status = 'PENDING_DELETE', updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Instant)
}
```

---

## MileageTrackingService

### Service Definition
```kotlin
@AndroidEntryPoint
class MileageTrackingService : Service() {
    
    companion object {
        const val ACTION_START_TRACKING = "START_TRACKING"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING"
        const val ACTION_GEOFENCE_ENTER = "GEOFENCE_ENTER"
        const val ACTION_GEOFENCE_EXIT = "GEOFENCE_EXIT"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mileage_tracking"
    }
    
    @Inject lateinit var locationClient: FusedLocationProviderClient
    @Inject lateinit var mileageRepository: MileageRepository
    @Inject lateinit var geofenceManager: GeofenceManager
    
    private var currentTrip: TripInProgress? = null
    private val locationPoints = mutableListOf<Location>()
}
```

### Service Lifecycle
- onCreate: Create notification channel, initialize location client
- onStartCommand: Handle START/STOP/GEOFENCE actions
- onDestroy: Stop location updates, save any in-progress trip

### Distance Calculation
```kotlin
private fun calculateTotalMiles(points: List<Location>): Double {
    if (points.size < 2) return 0.0
    
    var totalMeters = 0.0
    for (i in 1 until points.size) {
        totalMeters += points[i - 1].distanceTo(points[i])
    }
    return totalMeters / 1609.34  // Convert to miles
}
```

---

## Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Trip list load | < 200ms | 100 trips |
| Manual entry save | < 100ms | Including validation |
| Summary calculation | < 300ms | Full year |
| CSV export | < 2s | 1 year of data |
| Location update processing | < 50ms | Per update |
| Battery usage (auto-tracking) | < 5%/day | 8 hours tracking |

---

## Offline Behavior

| Feature | Offline Behavior |
|---------|------------------|
| View trips | Full functionality (local data) |
| Add/edit trip | Works, queued for sync |
| Delete trip | Works, queued for sync |
| View summaries | Full functionality |
| Export CSV | Works (local data only) |
| Auto-tracking | Works (location services) |
| Distance calculation | Disabled (requires API) |
| Address autocomplete | Disabled |

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Location permission denied | Disable auto-tracking, show explanation |
| Background location denied | Limited auto-tracking (app must be open) |
| GPS unavailable | Show warning, fall back to network location |
| Distance calc API failure | Allow manual entry, show error |
| Export failure | Retry option, error message |
| Sync failure | Local changes preserved, retry on connectivity |
| Invalid trip data | Prevent save, show field-specific errors |

---

## Acceptance Criteria

### Manual Entry
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-01 | Add trip with all fields | On mileage screen | I tap "+", enter "Home" to "Johnson Ranch", 23.5 miles, Client Visit, save | Trip appears in list with correct details |
| AC-011-02 | Add trip minimum fields | On add trip sheet | I enter only date (today), miles (15.0), purpose (Other), save | Trip saves successfully |
| AC-011-03 | Trip validation - no miles | On add trip sheet, miles empty | I tap Save | Error "Miles must be greater than 0", Save disabled |
| AC-011-04 | Trip validation - future date | On add trip sheet | I select tomorrow's date | Error "Date cannot be in the future" |
| AC-011-05 | Edit existing trip | Existing trip in list | I long press, tap Edit, change miles to 25.0, save | Trip updated with new mileage |
| AC-011-06 | Delete trip | Existing trip in list | I long press, tap Delete, confirm | Trip removed, "Trip deleted" snackbar appears |
| AC-011-07 | Undo delete | After deleting trip | I tap "Undo" within 5 seconds | Trip restored to list |

### Auto-Tracking
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-08 | Enable auto-tracking | Location permission granted | I toggle Auto-Tracking ON | Service starts, notification appears |
| AC-011-09 | Auto-tracking permission denied | Location permission not granted | I toggle Auto-Tracking ON | Permission request shown, toggle reverts to OFF |
| AC-011-10 | Trip detected | Auto-tracking enabled, I drive 5 miles | Trip ends (stationary 5+ min) | Notification "Trip recorded: 5.0 miles" appears |
| AC-011-11 | Review auto trip | Auto trip notification visible | I tap "Review" on notification | TripReviewSheet opens with trip details |
| AC-011-12 | Working hours respected | Auto-tracking ON, time is 8 PM | Working hours end at 6 PM | Service stopped, no tracking |

### Trip Linking
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-13 | Link trip to appointment | Trip on Jan 13, appointment same day | I select appointment in Link dropdown | Trip shows link icon and appointment time |
| AC-011-14 | Auto-link on geofence | Auto-tracking ON, appointment at client | I arrive at client location | Trip auto-linked to appointment |
| AC-011-15 | View linked trips | Appointment with 2 linked trips | I view appointment detail | "Mileage" section shows both trips, total miles |

### Reports & Summaries
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-16 | Daily total displayed | 3 trips today totaling 47.3 miles | I view mileage screen | Today header shows "47.3 mi" |
| AC-011-17 | Annual summary calculated | 12,450 miles in 2024 | I view annual summary | Deduction shows "$8,341.50" |
| AC-011-18 | Purpose breakdown | 10,000 client visit miles, 2,450 other | I tap annual summary | Pie chart shows 80% Client Visit, 20% Other |
| AC-011-19 | Monthly summary | January has 1,245 miles | I tap January header | Monthly summary shows 1,245 miles with breakdown |

### Export
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-20 | Export CSV - download | 50 trips in date range | I tap Export, select Download | CSV file saved to Downloads, snackbar confirms |
| AC-011-21 | Export CSV - email | 50 trips in date range | I tap Export, select Email | Email app opens with CSV attachment |
| AC-011-22 | CSV content valid | Export for January 2024 | I open exported CSV | Headers correct, all January trips included, deductions calculated |
| AC-011-23 | Export empty range | No trips in selected range | I tap Export | "No trips in selected range" message |

### Calculate Distance
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-24 | Calculate from addresses | Valid start and end addresses | I tap "Calculate" link | Miles field populated with driving distance |
| AC-011-25 | Calculate offline | No internet connection | I tap "Calculate" link | Error "Distance calculation requires internet" |

### Offline
| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-011-26 | Add trip offline | No internet | I add trip with manual miles | Trip saves locally, syncs when online |
| AC-011-27 | View trips offline | No internet, 50 local trips | I open mileage screen | All trips display correctly |

---

## File References

### Core Layer
- `core/database/entity/MileageLogEntity.kt`
- `core/database/dao/MileageLogDao.kt`
- `core/domain/model/MileageTrip.kt`
- `core/domain/model/MileageSummary.kt`
- `core/domain/model/MileagePurpose.kt`
- `core/domain/repository/MileageRepository.kt`
- `core/data/repository/MileageRepositoryImpl.kt`

### Service Layer
- `core/mileage/MileageTrackingService.kt`
- `core/mileage/GeofenceManager.kt`
- `core/mileage/TripDetector.kt`
- `core/mileage/MileageExporter.kt`

### Feature Layer
- `feature/mileage/ui/MileageScreen.kt`
- `feature/mileage/ui/MileageViewModel.kt`
- `feature/mileage/ui/components/TripCard.kt`
- `feature/mileage/ui/components/AnnualSummaryCard.kt`
- `feature/mileage/ui/components/AddTripSheet.kt`
- `feature/mileage/ui/components/TripDetailSheet.kt`
- `feature/mileage/ui/components/TripReviewSheet.kt`
- `feature/mileage/ui/components/LocationInputSheet.kt`
- `feature/mileage/ui/components/ExportOptionsSheet.kt`
- `feature/mileage/ui/AnnualBreakdownScreen.kt`
- `feature/mileage/ui/AnnualBreakdownViewModel.kt`

### Settings Integration
- `feature/settings/ui/AutoTrackingSettingsSection.kt`

---

## IRS Rate Updates

The IRS standard mileage rate is updated annually. Rate configuration:

```kotlin
object MileageRates {
    val rates = mapOf(
        2024 to 0.67,
        2025 to 0.70  // Updated when announced
    )
    
    fun getRateForYear(year: Int): Double {
        return rates[year] ?: rates.values.last()
    }
}
```

Rate updates delivered via app updates. No remote configuration required for MVP.
