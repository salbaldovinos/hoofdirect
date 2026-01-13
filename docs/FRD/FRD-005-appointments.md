# FRD-005: Appointment Creation & Management

**Source PRD**: PRD-005-appointments.md  
**Priority**: P0  
**Phase**: 2 - Scheduling Core  
**Status**: Draft  
**Last Updated**: 2025-01-13

---

## 1. Overview

### 1.1 Purpose

Enable farriers to create, manage, and complete appointments with full horse and service tracking. This is the core scheduling workflow that connects clients, horses, services, and invoicing.

### 1.2 Scope

This document specifies:
- Appointment creation with client/horse/service selection
- Appointment status workflow and transitions
- Recurring appointment generation
- Appointment completion flow with service date updates
- Reschedule and cancellation handling
- Appointment history and detail views

### 1.3 Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| FRD-001 | Required | User authentication and business profile |
| FRD-002 | Required | Offline-first data architecture |
| FRD-003 | Required | Client management (client selection) |
| FRD-004 | Required | Horse management (horse selection, due dates) |
| FRD-007 | Optional | Calendar sync integration |
| FRD-012 | Optional | Service prices for defaults |

---

## 2. Appointment Status Workflow

### 2.1 Status State Machine

```
                    ┌─────────────────────────────────────┐
                    │                                     │
                    ▼                                     │
    ┌───────────────────────────┐                        │
    │        SCHEDULED          │────────────────────────┤
    │   (Initial creation)      │                        │
    └───────────────────────────┘                        │
           │           │                                  │
           │           │                                  │
           │           ▼                                  │
           │    ┌──────────────────┐                     │
           │    │    CONFIRMED     │─────────────────────┤
           │    │  (Client ack)    │                     │
           │    └──────────────────┘                     │
           │           │                                  │
           │           │                                  │
           │           ▼                                  │
           │    ┌──────────────────┐                     │
           └───▶│    COMPLETED     │                     │
                │  (Work done)     │                     │
                └──────────────────┘                     │
                                                         │
    ┌───────────────────────────┐                        │
    │       CANCELLED           │◀───────────────────────┤
    │   (User cancelled)        │                        │
    └───────────────────────────┘                        │
                                                         │
    ┌───────────────────────────┐                        │
    │        NO_SHOW            │◀───────────────────────┘
    │   (Client absent)         │
    └───────────────────────────┘
```

### 2.2 Valid Status Transitions

```kotlin
// AppointmentStatusTransition.kt
enum class AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}

object AppointmentStatusTransition {
    private val validTransitions = mapOf(
        AppointmentStatus.SCHEDULED to setOf(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW
        ),
        AppointmentStatus.CONFIRMED to setOf(
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW
        ),
        // Terminal states - no transitions allowed
        AppointmentStatus.COMPLETED to emptySet(),
        AppointmentStatus.CANCELLED to emptySet(),
        AppointmentStatus.NO_SHOW to emptySet()
    )
    
    fun canTransition(from: AppointmentStatus, to: AppointmentStatus): Boolean {
        return validTransitions[from]?.contains(to) ?: false
    }
    
    fun validateTransition(from: AppointmentStatus, to: AppointmentStatus): Result<Unit> {
        return if (canTransition(from, to)) {
            Result.success(Unit)
        } else {
            Result.failure(InvalidStatusTransitionException(from, to))
        }
    }
}
```

### 2.3 Status Display

| Status | Color | Icon | Display Text |
|--------|-------|------|--------------|
| SCHEDULED | Blue (`#2196F3`) | `calendar_today` | "Scheduled" |
| CONFIRMED | Green (`#4CAF50`) | `check_circle` | "Confirmed" |
| COMPLETED | Gray (`#9E9E9E`) | `done_all` | "Completed" |
| CANCELLED | Red (`#F44336`) | `cancel` | "Cancelled" |
| NO_SHOW | Orange (`#FF9800`) | `person_off` | "No Show" |

---

## 3. Appointment List Screen

### 3.1 Route & Navigation

- **Route**: `/appointments`
- **Bottom nav**: "Schedule" tab
- **Deep link**: `hoofdirect://appointments`

### 3.2 Screen Layout

```
┌─────────────────────────────────────────────┐
│ [≡]        Appointments           [+] [📅]  │
├─────────────────────────────────────────────┤
│                                             │
│  Today                                      │
│  ───────────────────────────────────────    │
│  ┌─────────────────────────────────────┐    │
│  │ 8:00 AM          SCHEDULED          │    │
│  │ Johnson Ranch                       │    │
│  │ 🐴 Midnight, Dusty                  │    │
│  │ $225.00          📍 Austin, TX      │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 10:30 AM         CONFIRMED          │    │
│  │ Miller Farm                         │    │
│  │ 🐴 Thunder                          │    │
│  │ $180.00          📍 Round Rock, TX  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Tomorrow                                   │
│  ───────────────────────────────────────    │
│  ┌─────────────────────────────────────┐    │
│  │ 9:00 AM          SCHEDULED          │    │
│  │ Davis Stables                       │    │
│  │ 🐴 Spirit, Luna, Apollo             │    │
│  │ $495.00          📍 Cedar Park, TX  │    │
│  └─────────────────────────────────────┘    │
│                                             │
└─────────────────────────────────────────────┘
```

### 3.3 List Item Content

Each appointment card displays:
- **Time**: Start time in 12-hour format with AM/PM
- **Status**: Colored chip (see Status Display table)
- **Client name**: Primary identifier, tappable
- **Horses**: Comma-separated names, max 3 shown, "+N more" if exceeded
- **Total price**: Sum of all horse services, formatted as currency
- **Location**: City, State (from client or override)

### 3.4 Date Grouping

Appointments are grouped by date with section headers:
- "Today" for current date
- "Tomorrow" for next day
- Day name (e.g., "Wednesday") for next 5 days
- Full date (e.g., "Jan 25") for dates beyond 1 week

### 3.5 View Options

**Calendar Icon (📅) Button** opens view selector:
- "List View" (default): Chronological list grouped by date
- "Day View": Single day with time slots
- "Week View": 7-day calendar grid
- "Month View": Month calendar with dots for appointment days

### 3.6 Filtering

**Filter Button** reveals filter chips:
- Status: All / Scheduled / Confirmed / Completed / Cancelled / No Show
- Date Range: Today / This Week / This Month / Custom
- Client: Searchable client dropdown

### 3.7 Empty States

**No appointments at all:**
```
┌─────────────────────────────────────────────┐
│                                             │
│              📅                             │
│                                             │
│      No appointments scheduled              │
│                                             │
│   Create your first appointment to get      │
│   started with scheduling.                  │
│                                             │
│        [+ New Appointment]                  │
│                                             │
└─────────────────────────────────────────────┘
```

**No appointments matching filter:**
```
┌─────────────────────────────────────────────┐
│                                             │
│      No appointments match your filters     │
│                                             │
│           [Clear Filters]                   │
│                                             │
└─────────────────────────────────────────────┘
```

### 3.8 Pull-to-Refresh

- Pull gesture triggers sync with server
- Shows refresh indicator while syncing
- Updates list with any server changes

---

## 4. Create Appointment Screen

### 4.1 Route & Navigation

- **Route**: `/appointments/new`
- **Entry points**: FAB on appointments list, "Schedule" action from client/horse detail
- **Deep link**: `hoofdirect://appointment/new?clientId={clientId}&horseId={horseId}`

### 4.2 Screen Layout

```
┌─────────────────────────────────────────────┐
│ [×]     New Appointment            [Save]   │
├─────────────────────────────────────────────┤
│                                             │
│  Client *                                   │
│  ┌─────────────────────────────────────┐    │
│  │ 🔍 Search clients...                │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ✓ Johnson Ranch                     │    │
│  │   Austin, TX                        │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Date *                    Time *           │
│  ┌───────────────────┐    ┌──────────────┐  │
│  │ Jan 20, 2025  📅  │    │  8:00 AM  🕐 │  │
│  └───────────────────┘    └──────────────┘  │
│                                             │
│  Duration                                   │
│  ┌─────────────────────────────────────┐    │
│  │ 1h 30m (auto-calculated)        ▼   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ─────────────────────────────────────────  │
│  Horses                           Select All│
│  ─────────────────────────────────────────  │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ☑ Midnight                          │    │
│  │   ┌────────────────┐  ┌──────────┐  │    │
│  │   │ Full Set    ▼  │  │ $180.00  │  │    │
│  │   └────────────────┘  └──────────┘  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ☑ Dusty                             │    │
│  │   ┌────────────────┐  ┌──────────┐  │    │
│  │   │ Trim        ▼  │  │ $45.00   │  │    │
│  │   └────────────────┘  └──────────┘  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ☐ Spirit                            │    │
│  │   (not selected)                    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ─────────────────────────────────────────  │
│  Total                           $225.00    │
│  ─────────────────────────────────────────  │
│                                             │
│  ☐ Recurring appointment                    │
│                                             │
│  Location (optional)                        │
│  ┌─────────────────────────────────────┐    │
│  │ Uses client address by default      │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Notes (optional)                           │
│  ┌─────────────────────────────────────┐    │
│  │                                     │    │
│  │                                     │    │
│  └─────────────────────────────────────┘    │
│                                             │
└─────────────────────────────────────────────┘
```

### 4.3 Field Specifications

#### Client Selection (Required)

```kotlin
// ClientSelector.kt composable
@Composable
fun ClientSelector(
    selectedClient: Client?,
    onClientSelected: (Client) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val clients by viewModel.searchClients(searchQuery).collectAsState(emptyList())
    
    // Implementation: ExposedDropdownMenuBox with search
}
```

- **Input**: Searchable dropdown with autocomplete
- **Search**: Debounced 300ms, searches name, business name, city
- **Display**: Client name, business name (if different), city
- **Selection**: Tap to select, shows checkmark
- **Clearing**: X button to deselect and search again
- **Validation**: Required - "Please select a client"
- **Pre-fill**: If navigated from client detail, auto-select that client

#### Date Selection (Required)

- **Input**: Material 3 DatePicker
- **Default**: Today if before 6 PM, tomorrow if after
- **Constraints**: Cannot select past dates
- **Validation**: Required - "Please select a date"
- **Format**: Display as "Jan 20, 2025"

#### Time Selection (Required)

- **Input**: Material 3 TimePicker
- **Default**: Next available 30-minute slot based on working hours
- **Constraints**: Warn if outside working hours (don't block)
- **Validation**: Required - "Please select a time"
- **Format**: Display as "8:00 AM"

#### Duration

```kotlin
// DurationCalculator.kt
object DurationCalculator {
    private const val DEFAULT_DURATION_PER_HORSE = 30 // minutes
    
    fun calculate(horses: List<SelectedHorse>, userDefault: Int): Int {
        if (horses.isEmpty()) return userDefault
        
        val totalMinutes = horses.sumOf { horse ->
            when (horse.serviceType) {
                ServiceType.TRIM -> 20
                ServiceType.FRONT_SHOES -> 35
                ServiceType.FULL_SET -> 45
                ServiceType.CORRECTIVE -> 60
            }
        }
        
        // Round up to nearest 15 minutes
        return ((totalMinutes + 14) / 15) * 15
    }
}
```

- **Input**: Dropdown with preset options (30m, 45m, 1h, 1h 30m, 2h, 2h 30m, 3h)
- **Auto-calculation**: Recalculates when horses change
- **Display**: Shows "(auto-calculated)" when using auto value
- **Override**: User can select manual duration, clears auto flag

#### Horse Selection (At Least One Required)

When client is selected, shows all active horses for that client:

```kotlin
// HorseSelectionItem.kt
@Composable
fun HorseSelectionItem(
    horse: Horse,
    isSelected: Boolean,
    serviceType: ServiceType,
    price: BigDecimal,
    onToggle: () -> Unit,
    onServiceTypeChange: (ServiceType) -> Unit,
    onPriceChange: (BigDecimal) -> Unit
)
```

- **Checkbox**: Toggle to include/exclude horse
- **Service Type**: Dropdown pre-filled from horse's default service type
- **Price**: Text field pre-filled from service prices (or client custom price)
- **Per-horse notes**: Optional text field (expandable on demand)

**Price Resolution Order:**
1. Client custom price for service type (if set)
2. User's default service price for service type
3. Zero (with warning)

```kotlin
// PriceResolver.kt
class PriceResolver @Inject constructor(
    private val servicePriceDao: ServicePriceDao,
    private val clientDao: ClientDao
) {
    suspend fun resolvePrice(
        clientId: String,
        serviceType: ServiceType
    ): BigDecimal {
        // Check client custom price first
        val client = clientDao.getById(clientId)
        client?.customPrices?.get(serviceType.name)?.let {
            return BigDecimal(it)
        }
        
        // Fall back to user's default service price
        val servicePrice = servicePriceDao.getByType(serviceType)
        return servicePrice?.price ?: BigDecimal.ZERO
    }
}
```

#### Recurring Toggle

When enabled, shows additional fields:

```
┌─────────────────────────────────────────────┐
│  ☑ Recurring appointment                    │
│                                             │
│  Repeat every                               │
│  ┌──────────┐                               │
│  │ 6     ▼  │ weeks                         │
│  └──────────┘                               │
│                                             │
│  Create [12 ▼] appointments                 │
│                                             │
│  Preview:                                   │
│  • Jan 20, 2025                             │
│  • Mar 3, 2025                              │
│  • Apr 14, 2025                             │
│  • ... and 9 more                           │
└─────────────────────────────────────────────┘
```

- **Frequency**: Dropdown 1-12 weeks, default 6 (from user settings)
- **Count**: Dropdown 2-12 appointments, default 12
- **Preview**: Shows first 3 dates and count of remaining

#### Location Override

- **Default**: Uses client address automatically
- **Override**: Optional address autocomplete (Google Places)
- **Clear**: Can remove override to revert to client address

#### Notes

- **Input**: Multi-line text field
- **Max length**: 1000 characters
- **Character counter**: Shows "X/1000" when focused

### 4.4 Form Validation

```kotlin
// AppointmentValidator.kt
data class AppointmentValidation(
    val clientError: String? = null,
    val dateError: String? = null,
    val timeError: String? = null,
    val horsesError: String? = null,
    val isValid: Boolean = false
)

class AppointmentValidator {
    fun validate(form: AppointmentForm): AppointmentValidation {
        val clientError = if (form.clientId == null) {
            "Please select a client"
        } else null
        
        val dateError = if (form.date == null) {
            "Please select a date"
        } else if (form.date < LocalDate.now()) {
            "Cannot schedule appointments in the past"
        } else null
        
        val timeError = if (form.time == null) {
            "Please select a time"
        } else null
        
        val horsesError = if (form.selectedHorses.isEmpty()) {
            "Please select at least one horse"
        } else null
        
        return AppointmentValidation(
            clientError = clientError,
            dateError = dateError,
            timeError = timeError,
            horsesError = horsesError,
            isValid = listOf(clientError, dateError, timeError, horsesError).all { it == null }
        )
    }
}
```

### 4.5 Save Flow

```kotlin
// CreateAppointmentUseCase.kt
class CreateAppointmentUseCase @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val dueDateCalculator: DueDateCalculator
) {
    suspend operator fun invoke(form: AppointmentForm): Result<Appointment> {
        // Generate appointment ID
        val appointmentId = UUID.randomUUID().toString()
        
        // Create appointment entity
        val appointment = AppointmentEntity(
            id = appointmentId,
            userId = currentUserId,
            clientId = form.clientId!!,
            date = form.date!!,
            time = form.time!!,
            durationMinutes = form.durationMinutes,
            status = AppointmentStatus.SCHEDULED,
            locationOverride = form.locationOverride,
            latitudeOverride = form.latitudeOverride,
            longitudeOverride = form.longitudeOverride,
            notes = form.notes,
            syncStatus = EntitySyncStatus.PENDING_CREATE
        )
        
        // Create appointment-horse entries
        val appointmentHorses = form.selectedHorses.map { horse ->
            AppointmentHorseEntity(
                appointmentId = appointmentId,
                horseId = horse.horseId,
                serviceType = horse.serviceType,
                price = horse.price,
                notes = horse.notes
            )
        }
        
        return appointmentRepository.create(appointment, appointmentHorses)
    }
}
```

**On Save Success:**
1. Navigate back to appointment list
2. Show toast: "Appointment scheduled"
3. Appointment appears in list immediately (offline-first)
4. Sync queue entry created for background sync

**On Save Error:**
- Show inline error message
- Keep form data intact for retry

### 4.6 Recurring Appointment Creation

```kotlin
// CreateRecurringAppointmentsUseCase.kt
class CreateRecurringAppointmentsUseCase @Inject constructor(
    private val appointmentRepository: AppointmentRepository
) {
    suspend operator fun invoke(
        baseForm: AppointmentForm,
        frequencyWeeks: Int,
        count: Int
    ): Result<List<Appointment>> {
        val appointments = mutableListOf<AppointmentEntity>()
        var currentDate = baseForm.date!!
        
        repeat(minOf(count, 12)) { index ->
            val appointmentId = UUID.randomUUID().toString()
            
            appointments.add(
                AppointmentEntity(
                    id = appointmentId,
                    userId = currentUserId,
                    clientId = baseForm.clientId!!,
                    date = currentDate,
                    time = baseForm.time!!,
                    durationMinutes = baseForm.durationMinutes,
                    status = AppointmentStatus.SCHEDULED,
                    locationOverride = baseForm.locationOverride,
                    latitudeOverride = baseForm.latitudeOverride,
                    longitudeOverride = baseForm.longitudeOverride,
                    notes = baseForm.notes,
                    syncStatus = EntitySyncStatus.PENDING_CREATE
                )
            )
            
            currentDate = currentDate.plusWeeks(frequencyWeeks.toLong())
        }
        
        return appointmentRepository.createBatch(appointments, baseForm.selectedHorses)
    }
}
```

**Result:**
- Each recurring appointment is independent
- Can be edited/cancelled individually
- Toast: "12 appointments scheduled"

---

## 5. Appointment Detail Screen

### 5.1 Route & Navigation

- **Route**: `/appointments/{appointmentId}`
- **Entry points**: Tap appointment in list, deep link
- **Deep link**: `hoofdirect://appointment/{appointmentId}`

### 5.2 Screen Layout

```
┌─────────────────────────────────────────────┐
│ [←]     Appointment             [⋮ More]    │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │       January 20, 2025              │    │
│  │       8:00 AM - 9:30 AM             │    │
│  │       ━━━━━━━━━━━━━━━━━             │    │
│  │          SCHEDULED                  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Client                                     │
│  ─────────────────────────────────────────  │
│  ┌─────────────────────────────────────┐    │
│  │ Johnson Ranch                    >  │    │
│  │ 📍 123 Ranch Road, Austin, TX       │    │
│  │                                     │    │
│  │ [📞 Call] [💬 Text] [📍 Navigate]   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Horses & Services                          │
│  ─────────────────────────────────────────  │
│  ┌─────────────────────────────────────┐    │
│  │ 🐴 Midnight                         │    │
│  │    Full Set                $180.00  │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │ 🐴 Dusty                            │    │
│  │    Trim                    $45.00   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ─────────────────────────────────────────  │
│  Total                          $225.00     │
│  ─────────────────────────────────────────  │
│                                             │
│  Notes                                      │
│  ─────────────────────────────────────────  │
│  Gate code: 1234. Park by the barn.        │
│                                             │
│  ─────────────────────────────────────────  │
│                                             │
│    [✓ Complete]    [🗓 Reschedule]          │
│                                             │
└─────────────────────────────────────────────┘
```

### 5.3 Quick Actions Bar

Based on appointment status:

| Status | Available Actions |
|--------|-------------------|
| SCHEDULED | Complete, Reschedule, Confirm, Cancel, Mark No-Show |
| CONFIRMED | Complete, Reschedule, Cancel, Mark No-Show |
| COMPLETED | Create Invoice (if not invoiced) |
| CANCELLED | (none) |
| NO_SHOW | (none) |

### 5.4 More Menu (⋮)

- **Edit**: Opens edit form (SCHEDULED/CONFIRMED only)
- **Duplicate**: Creates new appointment with same details
- **Add to Route**: Opens route builder (if route optimization enabled)
- **Delete**: Deletes appointment (with confirmation)

### 5.5 Client Actions

- **📞 Call**: Opens phone dialer with client phone
- **💬 Text**: Opens SMS with client phone
- **📍 Navigate**: Opens maps to appointment location

---

## 6. Edit Appointment Screen

### 6.1 Route & Navigation

- **Route**: `/appointments/{appointmentId}/edit`
- **Entry**: "Edit" from detail screen more menu

### 6.2 Editable Fields

Same as create screen, with current values pre-filled:
- Client (can change, reloads horse list)
- Date
- Time
- Duration
- Selected horses and services
- Location override
- Notes

### 6.3 Non-Editable States

Cannot edit appointments with status:
- COMPLETED
- CANCELLED
- NO_SHOW

Attempting to navigate to edit for these shows toast: "Completed appointments cannot be edited"

---

## 7. Complete Appointment Flow

### 7.1 Entry Points

- "Complete" button on appointment detail
- Swipe action on appointment list item
- Route: `/appointments/{appointmentId}/complete`

### 7.2 Completion Sheet Layout

```
┌─────────────────────────────────────────────┐
│  Complete Appointment                   [×] │
│  Johnson Ranch • Jan 20                     │
├─────────────────────────────────────────────┤
│                                             │
│  Services Completed                         │
│  ─────────────────────────────────────────  │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ☑ Midnight                          │    │
│  │   Full Set                          │    │
│  │   Price: [$180.00    ] [Edit]       │    │
│  │   Notes: [                    ]     │    │
│  │   [📷 Add Photos]                   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ ☑ Dusty                             │    │
│  │   Trim                              │    │
│  │   Price: [$45.00     ] [Edit]       │    │
│  │   Notes: [                    ]     │    │
│  │   [📷 Add Photos]                   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ─────────────────────────────────────────  │
│  Total                          $225.00     │
│  ─────────────────────────────────────────  │
│                                             │
│  Completion Notes                           │
│  ┌─────────────────────────────────────┐    │
│  │ Added pads to Midnight. Dusty was   │    │
│  │ cooperative today.                  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │    Complete & Create Invoice        │    │
│  └─────────────────────────────────────┘    │
│                                             │
│        [Complete Without Invoice]           │
│                                             │
└─────────────────────────────────────────────┘
```

### 7.3 Completion Data

```kotlin
// AppointmentCompletionData.kt
data class AppointmentCompletionData(
    val horses: List<HorseServiceCompletion>,
    val completionNotes: String?,
    val completedAt: Instant = Instant.now()
)

data class HorseServiceCompletion(
    val horseId: String,
    val serviceType: ServiceType,
    val originalPrice: BigDecimal,
    val adjustedPrice: BigDecimal?, // null = use original
    val notes: String?,
    val photosBefore: List<String>, // URIs
    val photosAfter: List<String>
)
```

### 7.4 Completion Flow

```kotlin
// CompleteAppointmentUseCase.kt
class CompleteAppointmentUseCase @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val horseRepository: HorseRepository,
    private val dueDateCalculator: DueDateCalculator
) {
    @Transaction
    suspend operator fun invoke(
        appointmentId: String,
        completionData: AppointmentCompletionData,
        createInvoice: Boolean
    ): Result<CompleteAppointmentResult> {
        
        // 1. Update appointment status
        appointmentRepository.updateStatus(
            appointmentId,
            AppointmentStatus.COMPLETED,
            completedAt = completionData.completedAt
        )
        
        // 2. Update prices if adjusted
        completionData.horses.forEach { horseCompletion ->
            if (horseCompletion.adjustedPrice != null) {
                appointmentRepository.updateHorsePrice(
                    appointmentId,
                    horseCompletion.horseId,
                    horseCompletion.adjustedPrice
                )
            }
            
            // Save photos
            horseCompletion.photosBefore.forEach { uri ->
                // Save to horse_photos with appointmentId link
            }
            horseCompletion.photosAfter.forEach { uri ->
                // Save to horse_photos with appointmentId link
            }
        }
        
        // 3. Update horse service dates
        completionData.horses.forEach { horseCompletion ->
            val horse = horseRepository.getById(horseCompletion.horseId)
            val cycleWeeks = horse?.shoeingCycleWeeks ?: userDefaultCycleWeeks
            
            horseRepository.updateServiceDates(
                horseId = horseCompletion.horseId,
                lastServiceDate = LocalDate.now(),
                nextDueDate = dueDateCalculator.calculateNextDueDate(
                    LocalDate.now(),
                    cycleWeeks
                )
            )
        }
        
        // 4. Create invoice if requested
        val invoiceId = if (createInvoice) {
            createInvoiceForAppointment(appointmentId)
        } else null
        
        return Result.success(CompleteAppointmentResult(invoiceId))
    }
}
```

### 7.5 Post-Completion Actions

**Complete & Create Invoice:**
1. Updates appointment status to COMPLETED
2. Updates all horse service dates
3. Creates invoice with line items
4. Navigates to invoice detail
5. Toast: "Appointment completed. Invoice created."

**Complete Without Invoice:**
1. Updates appointment status to COMPLETED
2. Updates all horse service dates
3. Navigates back to appointment list
4. Toast: "Appointment completed"

---

## 8. Reschedule Flow

### 8.1 Entry Points

- "Reschedule" button on appointment detail
- Swipe action on list item
- Route: `/appointments/{appointmentId}/reschedule`

### 8.2 Reschedule Sheet

```
┌─────────────────────────────────────────────┐
│  Reschedule Appointment                 [×] │
│  Johnson Ranch                              │
├─────────────────────────────────────────────┤
│                                             │
│  Current: Jan 20, 2025 at 8:00 AM           │
│                                             │
│  New Date *                                 │
│  ┌─────────────────────────────────────┐    │
│  │ Jan 27, 2025                    📅  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  New Time *                                 │
│  ┌─────────────────────────────────────┐    │
│  │ 10:00 AM                        🕐  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ☑ Notify client of change                  │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │         Reschedule                  │    │
│  └─────────────────────────────────────┘    │
│                                             │
└─────────────────────────────────────────────┘
```

### 8.3 Reschedule Logic

```kotlin
// RescheduleAppointmentUseCase.kt
class RescheduleAppointmentUseCase @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val notificationService: NotificationService,
    private val calendarSyncManager: CalendarSyncManager
) {
    suspend operator fun invoke(
        appointmentId: String,
        newDate: LocalDate,
        newTime: LocalTime,
        notifyClient: Boolean
    ): Result<Unit> {
        // Update appointment
        appointmentRepository.reschedule(appointmentId, newDate, newTime)
        
        // Update calendar sync
        calendarSyncManager.updateAppointment(appointmentId)
        
        // Send notification if requested
        if (notifyClient) {
            val appointment = appointmentRepository.getById(appointmentId)
            notificationService.sendRescheduleNotification(appointment)
        }
        
        return Result.success(Unit)
    }
}
```

### 8.4 What Gets Preserved

- Client
- All horses and services
- Prices
- Duration
- Location override
- Notes

### 8.5 Post-Reschedule

- Navigate back to appointment detail
- Toast: "Appointment rescheduled to [date] at [time]"
- If notification sent: "Client notified"

---

## 9. Cancel Appointment Flow

### 9.1 Entry Points

- "Cancel" in appointment detail more menu
- Swipe action on list item (long swipe)

### 9.2 Cancel Dialog

```
┌─────────────────────────────────────────────┐
│                                             │
│  Cancel Appointment?                        │
│                                             │
│  Johnson Ranch                              │
│  Jan 20, 2025 at 8:00 AM                    │
│                                             │
│  Reason (optional)                          │
│  ┌─────────────────────────────────────┐    │
│  │ Client requested                    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ☐ Notify client                            │
│                                             │
│  ┌────────────────┐  ┌────────────────┐     │
│  │ Keep Scheduled │  │    Cancel      │     │
│  └────────────────┘  └────────────────┘     │
│                                             │
└─────────────────────────────────────────────┘
```

### 9.3 Cancel Logic

```kotlin
// CancelAppointmentUseCase.kt
class CancelAppointmentUseCase @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val notificationService: NotificationService,
    private val calendarSyncManager: CalendarSyncManager
) {
    suspend operator fun invoke(
        appointmentId: String,
        reason: String?,
        notifyClient: Boolean
    ): Result<Unit> {
        // Update status
        appointmentRepository.updateStatus(
            appointmentId,
            AppointmentStatus.CANCELLED,
            cancellationReason = reason
        )
        
        // Remove from calendar
        calendarSyncManager.removeAppointment(appointmentId)
        
        // Notify client if requested
        if (notifyClient) {
            val appointment = appointmentRepository.getById(appointmentId)
            notificationService.sendCancellationNotification(appointment, reason)
        }
        
        return Result.success(Unit)
    }
}
```

### 9.4 Post-Cancel

- Navigate back to appointment list
- Appointment shows with CANCELLED status
- Toast: "Appointment cancelled"

---

## 10. Mark No-Show Flow

### 10.1 Entry Points

- "Mark No-Show" in appointment detail more menu (for past appointments)

### 10.2 No-Show Dialog

```
┌─────────────────────────────────────────────┐
│                                             │
│  Mark as No-Show?                           │
│                                             │
│  Johnson Ranch                              │
│  Jan 20, 2025 at 8:00 AM                    │
│                                             │
│  The client did not show up for this        │
│  scheduled appointment.                     │
│                                             │
│  ┌────────────────┐  ┌────────────────┐     │
│  │     Back       │  │  Mark No-Show  │     │
│  └────────────────┘  └────────────────┘     │
│                                             │
└─────────────────────────────────────────────┘
```

### 10.3 No-Show Logic

- Updates status to NO_SHOW
- Does NOT update horse service dates
- Can optionally track no-show count per client (for reporting)

---

## 11. Data Models

### 11.1 AppointmentEntity

```kotlin
// core/database/entity/AppointmentEntity.kt
@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["client_id"]),
        Index(value = ["date"]),
        Index(value = ["status"]),
        Index(value = ["date", "time"]) // Composite for sorting
    ]
)
data class AppointmentEntity(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "user_id") 
    val userId: String,
    
    @ColumnInfo(name = "client_id") 
    val clientId: String,
    
    val date: LocalDate,
    
    val time: LocalTime,
    
    @ColumnInfo(name = "duration_minutes") 
    val durationMinutes: Int,
    
    val status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    
    @ColumnInfo(name = "location_override") 
    val locationOverride: String? = null,
    
    @ColumnInfo(name = "latitude_override") 
    val latitudeOverride: Double? = null,
    
    @ColumnInfo(name = "longitude_override") 
    val longitudeOverride: Double? = null,
    
    val notes: String? = null,
    
    @ColumnInfo(name = "completion_notes")
    val completionNotes: String? = null,
    
    @ColumnInfo(name = "cancellation_reason")
    val cancellationReason: String? = null,
    
    @ColumnInfo(name = "reminder_sent_at") 
    val reminderSentAt: Instant? = null,
    
    @ColumnInfo(name = "confirmation_received_at") 
    val confirmationReceivedAt: Instant? = null,
    
    @ColumnInfo(name = "route_order") 
    val routeOrder: Int? = null,
    
    @ColumnInfo(name = "estimated_arrival") 
    val estimatedArrival: LocalTime? = null,
    
    @ColumnInfo(name = "completed_at") 
    val completedAt: Instant? = null,
    
    @ColumnInfo(name = "calendar_event_id") 
    val calendarEventId: String? = null,
    
    @ColumnInfo(name = "created_at") 
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at") 
    val updatedAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "sync_status") 
    val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)
```

### 11.2 AppointmentHorseEntity

```kotlin
// core/database/entity/AppointmentHorseEntity.kt
@Entity(
    tableName = "appointment_horses",
    primaryKeys = ["appointment_id", "horse_id"],
    foreignKeys = [
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HorseEntity::class,
            parentColumns = ["id"],
            childColumns = ["horse_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["horse_id"])
    ]
)
data class AppointmentHorseEntity(
    @ColumnInfo(name = "appointment_id") 
    val appointmentId: String,
    
    @ColumnInfo(name = "horse_id") 
    val horseId: String,
    
    @ColumnInfo(name = "service_type") 
    val serviceType: ServiceType,
    
    @ColumnInfo(name = "service_description") 
    val serviceDescription: String? = null,
    
    val price: BigDecimal,
    
    val notes: String? = null,
    
    @ColumnInfo(name = "photos_before") 
    val photosBefore: String? = null, // JSON array of URIs
    
    @ColumnInfo(name = "photos_after") 
    val photosAfter: String? = null, // JSON array of URIs
    
    @ColumnInfo(name = "created_at") 
    val createdAt: Instant = Instant.now()
)
```

### 11.3 Relationship Models

```kotlin
// AppointmentWithDetails.kt
data class AppointmentWithDetails(
    @Embedded 
    val appointment: AppointmentEntity,
    
    @Relation(
        parentColumn = "client_id",
        entityColumn = "id"
    )
    val client: ClientEntity,
    
    @Relation(
        entity = AppointmentHorseEntity::class,
        parentColumn = "id",
        entityColumn = "appointment_id"
    )
    val horses: List<AppointmentHorseWithHorse>
)

data class AppointmentHorseWithHorse(
    @Embedded 
    val appointmentHorse: AppointmentHorseEntity,
    
    @Relation(
        parentColumn = "horse_id",
        entityColumn = "id"
    )
    val horse: HorseEntity
)

// For list display
data class AppointmentSummary(
    val id: String,
    val date: LocalDate,
    val time: LocalTime,
    val status: AppointmentStatus,
    val clientName: String,
    val clientCity: String?,
    val horseNames: List<String>,
    val totalPrice: BigDecimal
)
```

---

## 12. DAO Interface

```kotlin
// core/database/dao/AppointmentDao.kt
@Dao
interface AppointmentDao {
    
    // Insert
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: AppointmentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appointments: List<AppointmentEntity>)
    
    // Query single
    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: String): AppointmentEntity?
    
    @Transaction
    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getWithDetails(id: String): AppointmentWithDetails?
    
    // Query list
    @Transaction
    @Query("""
        SELECT * FROM appointments 
        WHERE user_id = :userId 
        AND date >= :startDate 
        AND date <= :endDate
        ORDER BY date ASC, time ASC
    """)
    fun getByDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<AppointmentWithDetails>>
    
    @Transaction
    @Query("""
        SELECT * FROM appointments 
        WHERE user_id = :userId 
        AND date = :date
        ORDER BY time ASC
    """)
    fun getByDate(userId: String, date: LocalDate): Flow<List<AppointmentWithDetails>>
    
    @Transaction
    @Query("""
        SELECT * FROM appointments 
        WHERE client_id = :clientId
        ORDER BY date DESC, time DESC
    """)
    fun getByClient(clientId: String): Flow<List<AppointmentWithDetails>>
    
    @Query("""
        SELECT * FROM appointments 
        WHERE user_id = :userId 
        AND status IN (:statuses)
        AND date >= :startDate
        ORDER BY date ASC, time ASC
    """)
    fun getUpcoming(
        userId: String,
        statuses: List<AppointmentStatus> = listOf(
            AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED
        ),
        startDate: LocalDate = LocalDate.now()
    ): Flow<List<AppointmentWithDetails>>
    
    // Update
    @Update
    suspend fun update(appointment: AppointmentEntity)
    
    @Query("""
        UPDATE appointments 
        SET status = :status, 
            completed_at = :completedAt,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun updateStatus(
        id: String,
        status: AppointmentStatus,
        completedAt: Instant? = null,
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    @Query("""
        UPDATE appointments 
        SET date = :date, 
            time = :time,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun reschedule(
        id: String,
        date: LocalDate,
        time: LocalTime,
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    // Delete
    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteById(id: String)
    
    // Counts
    @Query("""
        SELECT COUNT(*) FROM appointments 
        WHERE client_id = :clientId 
        AND status = 'NO_SHOW'
    """)
    suspend fun getNoShowCount(clientId: String): Int
}
```

---

## 13. Error Handling

### 13.1 Error Types

```kotlin
// AppointmentError.kt
sealed class AppointmentError : Exception() {
    object ClientNotFound : AppointmentError()
    object NoHorsesSelected : AppointmentError()
    object InvalidDate : AppointmentError()
    object InvalidTime : AppointmentError()
    object AppointmentNotFound : AppointmentError()
    data class InvalidStatusTransition(
        val from: AppointmentStatus,
        val to: AppointmentStatus
    ) : AppointmentError()
    object CannotEditCompletedAppointment : AppointmentError()
    object NetworkError : AppointmentError()
    data class Unknown(override val cause: Throwable?) : AppointmentError()
}
```

### 13.2 Error Messages

| Error | User Message |
|-------|--------------|
| ClientNotFound | "Client not found. Please select a different client." |
| NoHorsesSelected | "Please select at least one horse for this appointment." |
| InvalidDate | "Please select a valid date." |
| InvalidTime | "Please select a valid time." |
| AppointmentNotFound | "Appointment not found." |
| InvalidStatusTransition | "Cannot change appointment status from {from} to {to}." |
| CannotEditCompletedAppointment | "Completed appointments cannot be edited." |
| NetworkError | "Unable to sync appointment. Changes saved locally." |
| Unknown | "Something went wrong. Please try again." |

---

## 14. Acceptance Criteria

### 14.1 Create Appointment

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-005-01 | Create basic appointment | Form with client, date, time, 1 horse | Tap Save | Appointment created, appears in list, status = SCHEDULED |
| AC-005-02 | Prices auto-populate | Client with $50 trim custom price | Select horse with Trim default | Price shows $50 |
| AC-005-03 | Price uses default | Client without custom prices | Select horse | Price from user's service prices |
| AC-005-04 | Duration auto-calculates | 2 horses: Full Set + Trim | Select both horses | Duration shows ~1h 5m |
| AC-005-05 | Duration can be overridden | Auto-calculated 1h 5m | Select 2h from dropdown | Duration shows 2h, "auto" label removed |
| AC-005-06 | Create without horses | No horses selected | Tap Save | Error: "Please select at least one horse" |
| AC-005-07 | Create past date | Date before today selected | Tap Save | Error: "Cannot schedule appointments in the past" |

### 14.2 Recurring Appointments

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-005-08 | Create recurring | Recurring enabled, 6 weeks, 12 count | Tap Save | 12 appointments created, each 6 weeks apart |
| AC-005-09 | Max 12 appointments | Recurring with count > 12 | Attempt to save | Limited to 12 maximum |
| AC-005-10 | Individual editing | 12 recurring appointments created | Edit 3rd appointment | Only 3rd appointment modified |

### 14.3 Complete Appointment

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-005-11 | Complete updates status | SCHEDULED appointment | Complete appointment | Status = COMPLETED, completedAt set |
| AC-005-12 | Complete updates horse dates | Horse with 6-week cycle | Complete on Jan 20 | lastServiceDate = Jan 20, nextDueDate = Mar 3 |
| AC-005-13 | Price adjustment | Original $180 | Change to $200 in completion | appointmentHorse.price = $200 |
| AC-005-14 | Complete with invoice | Tap "Complete & Create Invoice" | Flow completes | Invoice created with line items matching services |
| AC-005-15 | Complete without invoice | Tap "Complete Without Invoice" | Flow completes | No invoice created, appointment COMPLETED |

### 14.4 Reschedule & Cancel

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-005-16 | Reschedule preserves details | Appointment with 2 horses, notes | Reschedule to new date/time | All details preserved, only date/time changed |
| AC-005-17 | Cancel sets status | SCHEDULED appointment | Cancel with reason | Status = CANCELLED, reason saved |
| AC-005-18 | No-show status | SCHEDULED past appointment | Mark as No-Show | Status = NO_SHOW, horse dates NOT updated |

### 14.5 Offline Support

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-005-19 | Create offline | No network | Create appointment | Saved locally with PENDING_CREATE, appears in list |
| AC-005-20 | Complete offline | No network | Complete appointment | Status = COMPLETED locally, horse dates updated, sync queued |
| AC-005-21 | Sync on reconnect | Pending changes, network restored | App detects connection | All pending appointments synced |

---

## 15. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Appointment list load | < 300ms | 50 appointments, cold start |
| Create appointment save | < 200ms | Local save time |
| Complete appointment | < 500ms | Including horse date updates |
| Date picker open | < 100ms | Material 3 DatePicker |
| Client search | < 200ms | 100 clients |
| Horse list load | < 100ms | 20 horses per client |

---

## 16. File References

### 16.1 UI Layer

| File | Purpose |
|------|---------|
| `feature/appointments/ui/AppointmentListScreen.kt` | Main list view |
| `feature/appointments/ui/AppointmentDetailScreen.kt` | Detail view |
| `feature/appointments/ui/CreateAppointmentScreen.kt` | Create/edit form |
| `feature/appointments/ui/CompleteAppointmentSheet.kt` | Completion bottom sheet |
| `feature/appointments/ui/RescheduleSheet.kt` | Reschedule bottom sheet |
| `feature/appointments/ui/AppointmentViewModel.kt` | State management |
| `feature/appointments/ui/component/AppointmentCard.kt` | List item component |
| `feature/appointments/ui/component/ClientSelector.kt` | Client dropdown |
| `feature/appointments/ui/component/HorseSelectionList.kt` | Horse multi-select |
| `feature/appointments/ui/component/ServiceTypeDropdown.kt` | Service type picker |

### 16.2 Domain Layer

| File | Purpose |
|------|---------|
| `feature/appointments/domain/CreateAppointmentUseCase.kt` | Create logic |
| `feature/appointments/domain/CreateRecurringAppointmentsUseCase.kt` | Recurring logic |
| `feature/appointments/domain/CompleteAppointmentUseCase.kt` | Completion logic |
| `feature/appointments/domain/RescheduleAppointmentUseCase.kt` | Reschedule logic |
| `feature/appointments/domain/CancelAppointmentUseCase.kt` | Cancel logic |
| `feature/appointments/domain/AppointmentValidator.kt` | Form validation |
| `feature/appointments/domain/DurationCalculator.kt` | Duration auto-calc |
| `feature/appointments/domain/PriceResolver.kt` | Price resolution |
| `feature/appointments/domain/AppointmentStatusTransition.kt` | Status state machine |

### 16.3 Data Layer

| File | Purpose |
|------|---------|
| `feature/appointments/data/AppointmentRepository.kt` | Data access |
| `core/database/entity/AppointmentEntity.kt` | Appointment table |
| `core/database/entity/AppointmentHorseEntity.kt` | Junction table |
| `core/database/dao/AppointmentDao.kt` | Database queries |
| `core/database/dao/AppointmentHorseDao.kt` | Horse junction queries |

---

## 17. Implementation Checklist

### Phase 1: Core Appointment CRUD
- [ ] AppointmentEntity and migrations
- [ ] AppointmentHorseEntity and migrations
- [ ] AppointmentDao with basic queries
- [ ] AppointmentRepository
- [ ] AppointmentListScreen with grouped display
- [ ] CreateAppointmentScreen with form
- [ ] Client selector component
- [ ] Horse selection list component
- [ ] Price resolution logic

### Phase 2: Status Workflow
- [ ] Status state machine
- [ ] AppointmentDetailScreen
- [ ] Complete appointment flow
- [ ] Horse service date updates on completion
- [ ] Reschedule flow
- [ ] Cancel flow
- [ ] No-show marking

### Phase 3: Advanced Features
- [ ] Recurring appointments
- [ ] Duration auto-calculation
- [ ] Photo capture on completion
- [ ] Calendar sync integration (FRD-007)
- [ ] Invoice creation integration (FRD-013)

### Phase 4: Polish
- [ ] Pull-to-refresh sync
- [ ] Empty states
- [ ] Error handling
- [ ] Loading states
- [ ] Performance optimization
- [ ] Accessibility

---

## Appendix A: Service Type Enum

```kotlin
// core/model/ServiceType.kt
enum class ServiceType(val displayName: String, val defaultDuration: Int) {
    TRIM("Trim", 20),
    FRONT_SHOES("Front Shoes", 35),
    FULL_SET("Full Set", 45),
    CORRECTIVE("Corrective", 60);
    
    companion object {
        fun fromName(name: String): ServiceType? {
            return values().find { it.name == name }
        }
    }
}
```

---

## Appendix B: Navigation Graph

```kotlin
// feature/appointments/navigation/AppointmentNavigation.kt
fun NavGraphBuilder.appointmentGraph(navController: NavController) {
    navigation(
        startDestination = "appointments/list",
        route = "appointments"
    ) {
        composable("appointments/list") {
            AppointmentListScreen(
                onAppointmentClick = { id ->
                    navController.navigate("appointments/$id")
                },
                onCreateClick = {
                    navController.navigate("appointments/new")
                }
            )
        }
        
        composable(
            "appointments/{appointmentId}",
            arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            AppointmentDetailScreen(
                appointmentId = backStackEntry.arguments?.getString("appointmentId") ?: "",
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate("appointments/$id/edit")
                }
            )
        }
        
        composable(
            "appointments/new?clientId={clientId}&horseId={horseId}",
            arguments = listOf(
                navArgument("clientId") { 
                    type = NavType.StringType
                    nullable = true 
                },
                navArgument("horseId") { 
                    type = NavType.StringType
                    nullable = true 
                }
            )
        ) { backStackEntry ->
            CreateAppointmentScreen(
                preselectedClientId = backStackEntry.arguments?.getString("clientId"),
                preselectedHorseId = backStackEntry.arguments?.getString("horseId"),
                onSave = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}
```
