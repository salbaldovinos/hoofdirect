# PRD-004: Horse Management

**Priority**: P0  
**Phase**: 1 - Foundation  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Enable farriers to manage detailed records for each horse, including service history, medical notes, and shoeing cycle tracking with automatic due date calculations.

### Business Value
- Tracks individual horse needs and preferences
- Automates "due soon" reminders for proactive scheduling
- Stores medical/temperament info for safer service
- Photo documentation for hoof condition tracking

### Success Metrics
| Metric | Target |
|--------|--------|
| Due date calculation accuracy | > 99% |
| Horse creation time | < 20 seconds |
| Photo upload success rate | > 95% |
| Service history query | < 100ms |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-004-01 | Farrier | Add horses to a client | I track each horse individually | P0 |
| US-004-02 | Farrier | Record horse details (breed, color, age) | I identify horses correctly | P0 |
| US-004-03 | Farrier | Note temperament/handling tips | I'm prepared for difficult horses | P0 |
| US-004-04 | Farrier | Add medical notes | I know about lameness/conditions | P0 |
| US-004-05 | Farrier | Set default service type | Appointment creation is faster | P1 |
| US-004-06 | Farrier | Set custom shoeing cycle | Due dates are accurate | P0 |
| US-004-07 | Farrier | Take before/after photos | I document hoof condition | P1 |
| US-004-08 | Farrier | See when horse is due | I schedule proactively | P0 |
| US-004-09 | Farrier | View service history | I see past work | P0 |
| US-004-10 | Farrier | See all horses due soon | I plan my week | P0 |

---

## Functional Requirements

### FR-004-01: Create Horse
- Required: name, linked client
- Optional: breed, color, age, temperament notes, medical notes
- Default service type selection (trim, front, full, corrective)
- Custom shoeing cycle (override user default)
- Save creates local record + queues sync

### FR-004-02: Horse Details
- Name (max 50 chars)
- Breed (dropdown with common breeds + custom)
- Color (text, max 30 chars)
- Age (years, 0-40)
- Temperament notes (multi-line, max 1000 chars)
- Medical notes (multi-line, max 2000 chars)
- Default service type (enum)
- Shoeing cycle weeks (4-16, null = use user default)

### FR-004-03: Due Date Calculation
```
next_due_date = last_service_date + shoeing_cycle_weeks

Due Status:
- Green: > 7 days until due
- Yellow: 1-7 days until due  
- Red: Overdue (past due date)
- Gray: Never serviced
```

### FR-004-04: Photo Management
- Camera capture (direct)
- Gallery selection
- Compression to max 1MB
- Local storage + cloud sync
- Photo metadata: date, caption
- Gallery view per horse
- Delete photos

### FR-004-05: Service History
- Timeline of completed appointments
- Service type per visit
- Price charged
- Notes from appointment
- Before/after photos
- Tap to view appointment details

### FR-004-06: Due Soon List
- Global view across all horses
- Filter by days threshold (7, 14, 30)
- Sort by due date (soonest first)
- Group by client
- Quick action: Schedule appointment

### FR-004-07: Horse List (per Client)
- Show all horses for client
- Due status indicator per horse
- Service type badge
- Last service date
- Tap to view details
- Add horse button

---

## Technical Implementation

```kotlin
// HorseRepository.kt
class HorseRepository @Inject constructor(
    private val horseDao: HorseDao,
    private val photoStorage: PhotoStorageManager,
    private val syncManager: SyncManager,
    private val userPrefs: UserPreferencesManager
) {
    fun getHorsesForClient(clientId: String): Flow<List<Horse>> {
        return horseDao.getHorsesForClient(clientId)
            .map { entities -> entities.map { it.toDomain(userPrefs) } }
    }
    
    fun getDueSoonHorses(daysThreshold: Int = 14): Flow<List<HorseWithClient>> {
        val thresholdDate = LocalDate.now().plusDays(daysThreshold.toLong())
        return horseDao.getDueSoonHorses(thresholdDate)
            .map { list ->
                list.map { it.toDomain() }
                    .sortedBy { it.horse.nextDueDate }
            }
    }
    
    suspend fun create(horse: Horse): Result<Horse> {
        return try {
            val entity = horse.toEntity().copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE
            )
            horseDao.insert(entity)
            syncManager.queueChange(
                entityType = "horses",
                entityId = entity.id,
                operation = SyncOperation.INSERT,
                payload = Json.encodeToString(entity)
            )
            Result.success(horse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun addPhoto(
        horseId: String,
        imageUri: Uri,
        caption: String?
    ): Result<HorsePhoto> {
        return try {
            val compressed = photoStorage.compressImage(imageUri, maxSizeKb = 1024)
            val localPath = photoStorage.saveLocally(compressed, horseId)
            val photo = HorsePhoto(
                id = UUID.randomUUID().toString(),
                horseId = horseId,
                localPath = localPath,
                caption = caption,
                takenAt = Instant.now()
            )
            horseDao.insertPhoto(photo.toEntity())
            syncManager.queuePhotoUpload(horseId, photo)
            Result.success(photo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// DueDateCalculator.kt
object DueDateCalculator {
    fun calculateNextDueDate(
        lastServiceDate: LocalDate?,
        cycleWeeks: Int
    ): LocalDate? {
        return lastServiceDate?.plusWeeks(cycleWeeks.toLong())
    }
    
    fun getDueStatus(nextDueDate: LocalDate?): DueStatus {
        if (nextDueDate == null) return DueStatus.NEVER_SERVICED
        val daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), nextDueDate)
        return when {
            daysUntilDue < 0 -> DueStatus.OVERDUE
            daysUntilDue <= 7 -> DueStatus.DUE_SOON
            else -> DueStatus.OK
        }
    }
}
```

---

## Data Model

```kotlin
@Entity(
    tableName = "horses",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["client_id"]),
        Index(value = ["user_id"]),
        Index(value = ["next_due_date"])
    ]
)
data class HorseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    val name: String,
    val breed: String?,
    val color: String?,
    @ColumnInfo(name = "age_years") val ageYears: Int?,
    @ColumnInfo(name = "temperament_notes") val temperamentNotes: String?,
    @ColumnInfo(name = "medical_notes") val medicalNotes: String?,
    @ColumnInfo(name = "default_service_type") val defaultServiceType: String = "trim",
    @ColumnInfo(name = "shoeing_cycle_weeks") val shoeingCycleWeeks: Int?,
    @ColumnInfo(name = "last_service_date") val lastServiceDate: LocalDate?,
    @ColumnInfo(name = "next_due_date") val nextDueDate: LocalDate?,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)

@Entity(tableName = "horse_photos")
data class HorsePhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "horse_id") val horseId: String,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "remote_url") val remoteUrl: String?,
    val caption: String?,
    @ColumnInfo(name = "taken_at") val takenAt: Instant,
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus
)

enum class ServiceType {
    TRIM,
    FRONT_SHOES,
    FULL_SET,
    CORRECTIVE,
    OTHER
}

enum class DueStatus {
    OK,           // > 7 days
    DUE_SOON,     // 1-7 days
    OVERDUE,      // Past due
    NEVER_SERVICED
}

data class HorseWithClient(
    val horse: Horse,
    val client: Client
)
```

---

## UI Specifications

### Horse Detail Screen
```
┌─────────────────────────────────────────┐
│ [←]  Midnight                    [Edit] │
│      Johnson Ranch                      │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  Due Status Card                │   │
│  │  🟡 Due in 5 days (Jan 18)     │   │
│  │  [Schedule Appointment]         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Details                                │
│  ─────────────────────────────────────  │
│  Breed: Quarter Horse                   │
│  Color: Bay                             │
│  Age: 12 years                          │
│  Service: Full Set                      │
│  Cycle: 6 weeks                         │
│                                         │
│  Temperament                            │
│  ─────────────────────────────────────  │
│  Stands well, sensitive on left front   │
│                                         │
│  Medical Notes                          │
│  ─────────────────────────────────────  │
│  History of navicular, needs pads       │
│                                         │
│  Photos (4)                      [Add]  │
│  ─────────────────────────────────────  │
│  [📷] [📷] [📷] [📷]                   │
│                                         │
│  Service History                        │
│  ─────────────────────────────────────  │
│  Dec 15, 2024 - Full Set ($180)        │
│  Nov 3, 2024 - Full Set ($180)         │
│  Sep 22, 2024 - Full Set ($180)        │
│                                         │
└─────────────────────────────────────────┘
```

### Due Soon Screen
```
┌─────────────────────────────────────────┐
│  Due Soon                               │
│  SegmentedButton: [7d] [14d] [30d]     │
├─────────────────────────────────────────┤
│                                         │
│  OVERDUE                                │
│  ┌─────────────────────────────────┐   │
│  │ 🔴 Dusty - Williams Farm        │   │
│  │    2 days overdue               │   │
│  └─────────────────────────────────┘   │
│                                         │
│  THIS WEEK                              │
│  ┌─────────────────────────────────┐   │
│  │ 🟡 Midnight - Johnson Ranch     │   │
│  │    Due Jan 18 (5 days)          │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ 🟡 Spirit - Martinez Stables    │   │
│  │    Due Jan 20 (7 days)          │   │
│  └─────────────────────────────────┘   │
│                                         │
│  NEXT WEEK                              │
│  ┌─────────────────────────────────┐   │
│  │ 🟢 Bella - Oak Hill             │   │
│  │    Due Jan 25 (12 days)         │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class DueDateCalculatorTest {
    @Test
    fun `calculateNextDueDate returns correct date`() {
        val lastService = LocalDate.of(2024, 1, 1)
        val result = DueDateCalculator.calculateNextDueDate(lastService, 6)
        assertEquals(LocalDate.of(2024, 2, 12), result)
    }
    
    @Test
    fun `getDueStatus returns OVERDUE when past due`() {
        val pastDate = LocalDate.now().minusDays(1)
        assertEquals(DueStatus.OVERDUE, DueDateCalculator.getDueStatus(pastDate))
    }
    
    @Test
    fun `getDueStatus returns DUE_SOON within 7 days`() {
        val soonDate = LocalDate.now().plusDays(5)
        assertEquals(DueStatus.DUE_SOON, DueDateCalculator.getDueStatus(soonDate))
    }
}

class HorseRepositoryTest {
    @Test
    fun `getDueSoonHorses returns sorted by due date`() = runTest {
        val horses = repository.getDueSoonHorses(14).first()
        assertTrue(horses.zipWithNext().all { (a, b) ->
            a.horse.nextDueDate!! <= b.horse.nextDueDate!!
        })
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-004-01 | Create horse linked to client | E2E test |
| AC-004-02 | Due date calculates correctly | Unit test |
| AC-004-03 | Due status colors display correctly | UI test |
| AC-004-04 | Photo capture and storage works | Manual test |
| AC-004-05 | Service history shows past appointments | Integration test |
| AC-004-06 | Due soon list filters by threshold | Integration test |
| AC-004-07 | Horse data available offline | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-003 (Clients) | Internal | Required |
| PRD-002 (Offline) | Internal | Required |
| Camera permission | System | Required |
| Supabase Storage | External | Required |
