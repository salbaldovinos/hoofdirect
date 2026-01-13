# PRD-005: Appointment Creation & Management

**Priority**: P0  
**Phase**: 2 - Scheduling Core  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Enable farriers to create, manage, and complete appointments with full horse and service tracking, forming the core scheduling workflow.

### Business Value
- Central feature for daily operations
- Tracks services and pricing for invoicing
- Updates horse due dates automatically
- Foundation for route optimization

### Success Metrics
| Metric | Target |
|--------|--------|
| Appointment creation time | < 30 seconds |
| Completion flow time | < 60 seconds |
| Status update latency | < 1 second |
| Data accuracy | 100% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-005-01 | Farrier | Create appointment for client | I schedule a visit | P0 |
| US-005-02 | Farrier | Select horses for appointment | I know which to service | P0 |
| US-005-03 | Farrier | Set service type per horse | Pricing is accurate | P0 |
| US-005-04 | Farrier | Create recurring appointments | Regular clients auto-schedule | P1 |
| US-005-05 | Farrier | Mark appointment complete | I track completed work | P0 |
| US-005-06 | Farrier | Cancel appointment | Plans change | P0 |
| US-005-07 | Farrier | Mark no-show | I track unreliable clients | P1 |
| US-005-08 | Farrier | Add notes to appointment | I remember special instructions | P0 |
| US-005-09 | Farrier | Quick reschedule | I move appointments easily | P1 |
| US-005-10 | Farrier | See appointment history | I review past visits | P0 |

---

## Functional Requirements

### FR-005-01: Create Appointment
- Select client (required)
- Select date (required, date picker)
- Select time (required, time picker)
- Duration (auto-calculated from horses, or manual)
- Select horses (multi-select from client's horses)
- Service type per horse (pre-filled from horse default)
- Price per horse (pre-filled from service prices)
- Location override (optional, default = client address)
- Notes (optional)

### FR-005-02: Appointment Status Workflow
```
SCHEDULED → CONFIRMED → COMPLETED
    │           │
    └─→ CANCELLED
    │
    └─→ NO_SHOW
```

### FR-005-03: Horse Selection & Services
- Show all active horses for selected client
- Checkbox multi-select
- Per-horse fields:
  - Service type dropdown
  - Price (editable, pre-filled)
  - Notes (optional)
- Total price calculated automatically

### FR-005-04: Complete Appointment
- Confirm horses serviced
- Adjust prices if needed
- Add completion notes
- Take before/after photos (optional)
- Updates:
  - Appointment status → COMPLETED
  - Horse last_service_date → today
  - Horse next_due_date → calculated
- Prompt to create invoice

### FR-005-05: Recurring Appointments
- Enable recurring toggle
- Frequency: every X weeks (1-12)
- Max future appointments: 12
- Creates individual appointment records
- Each can be edited/cancelled independently

### FR-005-06: Reschedule
- Change date/time only
- Preserves all other details
- Updates calendar sync
- Sends notification to client (if enabled)

### FR-005-07: Cancel Appointment
- Require reason (optional)
- Status → CANCELLED
- Updates calendar sync
- Option to notify client

---

## Technical Implementation

```kotlin
// AppointmentRepository.kt
class AppointmentRepository @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val appointmentHorseDao: AppointmentHorseDao,
    private val horseDao: HorseDao,
    private val syncManager: SyncManager,
    private val calendarSync: CalendarSyncManager
) {
    @Transaction
    suspend fun create(appointment: Appointment, horses: List<AppointmentHorse>): Result<Appointment> {
        return try {
            val entity = appointment.toEntity().copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE
            )
            appointmentDao.insert(entity)
            
            horses.forEach { horse ->
                appointmentHorseDao.insert(horse.toEntity())
            }
            
            syncManager.queueChange(
                entityType = "appointments",
                entityId = entity.id,
                operation = SyncOperation.INSERT,
                payload = Json.encodeToString(CreateAppointmentPayload(entity, horses))
            )
            
            calendarSync.syncAppointment(appointment)
            
            Result.success(appointment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    @Transaction
    suspend fun complete(
        appointmentId: String,
        completionData: AppointmentCompletionData
    ): Result<Unit> {
        return try {
            val now = Instant.now()
            
            // Update appointment
            appointmentDao.updateStatus(
                id = appointmentId,
                status = AppointmentStatus.COMPLETED,
                completedAt = now
            )
            
            // Update each horse's service dates
            completionData.horses.forEach { horseService ->
                horseDao.updateServiceDate(
                    id = horseService.horseId,
                    lastServiceDate = LocalDate.now(),
                    nextDueDate = DueDateCalculator.calculateNextDueDate(
                        LocalDate.now(),
                        horseService.cycleWeeks
                    )
                )
                
                // Update price if adjusted
                if (horseService.adjustedPrice != null) {
                    appointmentHorseDao.updatePrice(
                        appointmentId = appointmentId,
                        horseId = horseService.horseId,
                        price = horseService.adjustedPrice
                    )
                }
            }
            
            syncManager.queueChange(
                entityType = "appointments",
                entityId = appointmentId,
                operation = SyncOperation.UPDATE,
                payload = Json.encodeToString(completionData)
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createRecurring(
        baseAppointment: Appointment,
        horses: List<AppointmentHorse>,
        frequencyWeeks: Int,
        count: Int = 12
    ): Result<List<Appointment>> {
        val appointments = mutableListOf<Appointment>()
        var currentDate = baseAppointment.date
        
        repeat(minOf(count, 12)) { i ->
            val apt = baseAppointment.copy(
                id = UUID.randomUUID().toString(),
                date = currentDate
            )
            create(apt, horses)
            appointments.add(apt)
            currentDate = currentDate.plusWeeks(frequencyWeeks.toLong())
        }
        
        return Result.success(appointments)
    }
}
```

---

## Data Model

```kotlin
@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"]
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["client_id"]),
        Index(value = ["date"]),
        Index(value = ["status"])
    ]
)
data class AppointmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    val date: LocalDate,
    val time: LocalTime,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    @ColumnInfo(name = "location_override") val locationOverride: String?,
    @ColumnInfo(name = "latitude_override") val latitudeOverride: Double?,
    @ColumnInfo(name = "longitude_override") val longitudeOverride: Double?,
    val notes: String?,
    @ColumnInfo(name = "reminder_sent_at") val reminderSentAt: Instant?,
    @ColumnInfo(name = "confirmation_received_at") val confirmationReceivedAt: Instant?,
    @ColumnInfo(name = "route_order") val routeOrder: Int?,
    @ColumnInfo(name = "estimated_arrival") val estimatedArrival: LocalTime?,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "calendar_event_id") val calendarEventId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)

@Entity(
    tableName = "appointment_horses",
    primaryKeys = ["appointment_id", "horse_id"],
    foreignKeys = [
        ForeignKey(entity = AppointmentEntity::class, parentColumns = ["id"], childColumns = ["appointment_id"]),
        ForeignKey(entity = HorseEntity::class, parentColumns = ["id"], childColumns = ["horse_id"])
    ]
)
data class AppointmentHorseEntity(
    @ColumnInfo(name = "appointment_id") val appointmentId: String,
    @ColumnInfo(name = "horse_id") val horseId: String,
    @ColumnInfo(name = "service_type") val serviceType: ServiceType,
    @ColumnInfo(name = "service_description") val serviceDescription: String?,
    val price: BigDecimal,
    val notes: String?,
    @ColumnInfo(name = "photos_before") val photosBefore: String?, // JSON
    @ColumnInfo(name = "photos_after") val photosAfter: String?, // JSON
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now()
)

enum class AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}

data class AppointmentWithDetails(
    @Embedded val appointment: AppointmentEntity,
    @Relation(parentColumn = "client_id", entityColumn = "id")
    val client: ClientEntity,
    @Relation(
        entity = AppointmentHorseEntity::class,
        parentColumn = "id",
        entityColumn = "appointment_id"
    )
    val horses: List<AppointmentHorseWithHorse>
)
```

---

## UI Specifications

### Create Appointment Screen
```
┌─────────────────────────────────────────┐
│ [×]  New Appointment            [Save]  │
├─────────────────────────────────────────┤
│                                         │
│  Client *                               │
│  ┌─────────────────────────────────┐   │
│  │ 🔍 Search clients...            │   │
│  └─────────────────────────────────┘   │
│  Selected: Johnson Ranch               │
│                                         │
│  Date *                Time *           │
│  ┌──────────────┐  ┌──────────────┐    │
│  │ Jan 20, 2024 │  │   8:00 AM    │    │
│  └──────────────┘  └──────────────┘    │
│                                         │
│  Duration                               │
│  ┌─────────────────────────────────┐   │
│  │ 1 hour 30 minutes (auto)        │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Horses                                 │
│  ─────────────────────────────────────  │
│  ☑ Midnight                            │
│     Full Set         $180.00           │
│  ☑ Dusty                               │
│     Trim             $45.00            │
│  ☐ Spirit                              │
│                                         │
│  Total: $225.00                        │
│                                         │
│  [ ] Recurring every [6] weeks         │
│                                         │
│  Notes                                  │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

### Complete Appointment Sheet
```
┌─────────────────────────────────────────┐
│  Complete Appointment                   │
│  Johnson Ranch - Jan 20                 │
├─────────────────────────────────────────┤
│                                         │
│  Services Completed                     │
│  ─────────────────────────────────────  │
│  ☑ Midnight - Full Set                 │
│    Price: $180.00  [Edit]              │
│    [📷 Add Photos]                     │
│                                         │
│  ☑ Dusty - Trim                        │
│    Price: $45.00   [Edit]              │
│    [📷 Add Photos]                     │
│                                         │
│  Total: $225.00                        │
│                                         │
│  Completion Notes                       │
│  ┌─────────────────────────────────┐   │
│  │ Added pads to Midnight          │   │
│  └─────────────────────────────────┘   │
│                                         │
│  [Complete & Create Invoice]           │
│  [Complete Without Invoice]            │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class AppointmentRepositoryTest {
    @Test
    fun `complete updates horse service dates`() = runTest {
        repository.complete(appointmentId, completionData)
        
        val horse = horseDao.getById(horseId)
        assertEquals(LocalDate.now(), horse.lastServiceDate)
        assertNotNull(horse.nextDueDate)
    }
    
    @Test
    fun `createRecurring creates correct number of appointments`() = runTest {
        val result = repository.createRecurring(baseApt, horses, 6, 12)
        assertEquals(12, result.getOrThrow().size)
    }
    
    @Test
    fun `total price calculates correctly`() {
        val horses = listOf(
            AppointmentHorse(price = BigDecimal("180.00")),
            AppointmentHorse(price = BigDecimal("45.00"))
        )
        assertEquals(BigDecimal("225.00"), calculateTotal(horses))
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-005-01 | Create appointment with client and horses | E2E test |
| AC-005-02 | Prices auto-populate from defaults | Integration test |
| AC-005-03 | Complete updates horse due dates | Integration test |
| AC-005-04 | Recurring creates multiple appointments | Integration test |
| AC-005-05 | Cancel updates status correctly | Unit test |
| AC-005-06 | Reschedule preserves details | Integration test |
| AC-005-07 | Works offline | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-003 (Clients) | Internal | Required |
| PRD-004 (Horses) | Internal | Required |
| PRD-007 (Calendar Sync) | Internal | Optional |
