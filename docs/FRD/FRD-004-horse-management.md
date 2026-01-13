# FRD-004: Horse Management

**PRD Reference:** PRD-004  
**Priority:** P0  
**Phase:** 1 - Foundation  
**Estimated Duration:** 1.5 weeks

---

## 1. Overview

### 1.1 Purpose

Enable farriers to track individual horses, their service schedules, and shoeing history. Horses are linked to clients and are the core unit of service delivery.

### 1.2 Key Features

- Horse profiles with breed, color, temperament
- Individual shoeing cycles (override user default)
- Due date tracking with visual status indicators
- Service history timeline
- Photo management (before/after shots)
- Global "Due Soon" list for proactive scheduling

---

## 2. Functional Specifications

### 2.1 Horse List (Within Client)

**Location:** Client detail screen, Horses section

#### 2.1.1 Horse Card Content

```
┌────────────────────────────────────────┐
│ [🟡] Thunder                        >  │
│      Quarter Horse • Bay               │
│      Trim • Due in 5 days              │
└────────────────────────────────────────┘
```

| Element | Source | Fallback |
|---------|--------|----------|
| Status dot | Calculated from next_due_date | Gray if never serviced |
| Name | `horse.name` | Required |
| Breed | `horse.breed` | Omit if empty |
| Color | `horse.color` | Omit if empty |
| Default service | `horse.defaultServiceType` | Omit if not set |
| Due status | Calculated (see 2.4) | "Never serviced" |

### 2.2 Horse Detail Screen

**Screen:** `HorseDetailScreen.kt`  
**Route:** `/horses/{horseId}`

#### 2.2.1 UI Layout

```
┌────────────────────────────────────────┐
│ [←]  Thunder                   [Edit]  │
├────────────────────────────────────────┤
│ ┌────────────────────────────────────┐ │
│ │          [Horse Photo]             │ │
│ │                                    │ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│ Owner: Sarah Anderson               >  │
│ Breed: Quarter Horse                   │
│ Color: Bay                             │
│ Age: 8 years                           │
│ Temperament: Calm                      │
├────────────────────────────────────────┤
│ 🔧 Service Settings                    │
│    Default: Trim                       │
│    Cycle: 6 weeks                      │
│    Last Service: Jan 5, 2025           │
│    Next Due: [🟡] Feb 16, 2025 (5 days)│
├────────────────────────────────────────┤
│ 📋 Service History                     │
│ ┌────────────────────────────────────┐ │
│ │ Jan 5, 2025 • Trim • $45           │ │
│ │ Notes: Slight sensitivity LF...    │ │
│ │ [Photo] [Photo]                    │ │
│ ├────────────────────────────────────┤ │
│ │ Dec 1, 2024 • Trim • $45           │ │
│ │ Notes: —                           │ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│ 📝 Medical Notes                       │
│ Previous founder 2022. Extra care...  │
└────────────────────────────────────────┘
```

#### 2.2.2 Quick Actions

| Action | Icon | Behavior |
|--------|------|----------|
| Schedule | Calendar+ | Create appointment pre-filled with this horse |
| Add Photo | Camera | Open camera/gallery for new photo |
| View Client | Person | Navigate to client detail |

### 2.3 Create/Edit Horse

**Screen:** `HorseFormScreen.kt`  
**Route:** `/horses/new?clientId={clientId}` or `/horses/{horseId}/edit`

#### 2.3.1 Form Fields

| Field | Type | Required | Validation | Max Length |
|-------|------|----------|------------|------------|
| Name | Text | Yes | Non-empty | 50 |
| Client | Selector | Yes | Must exist | — |
| Breed | Text | No | — | 50 |
| Color | Text | No | — | 30 |
| Age | Number | No | 0-50 years | — |
| Temperament | Dropdown | No | Predefined list | — |
| Default Service | Dropdown | No | From service types | — |
| Shoeing Cycle | Slider | No | 4-16 weeks | — |
| Medical Notes | TextArea | No | — | 2000 |

#### 2.3.2 Temperament Options

```kotlin
enum class HorseTemperament(val displayName: String) {
    CALM("Calm"),
    NERVOUS("Nervous"),
    DIFFICULT("Difficult"),
    AGGRESSIVE("Aggressive"),
    YOUNG("Young/Green"),
    SENIOR("Senior/Stiff")
}
```

#### 2.3.3 Shoeing Cycle Override

```
┌────────────────────────────────────────┐
│ Shoeing Cycle                          │
│                                        │
│ [ ] Use default (6 weeks)              │
│                                        │
│ If unchecked:                          │
│ [====|==========] 8 weeks              │
│   4              16                    │
│                                        │
│ Next due date will be calculated       │
│ from last service + this cycle         │
└────────────────────────────────────────┘
```

### 2.4 Due Date Calculation

#### 2.4.1 Calculation Logic

```kotlin
// DueDateCalculator.kt
class DueDateCalculator @Inject constructor(
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend fun calculateNextDueDate(horse: HorseEntity): LocalDate? {
        val lastServiceDate = horse.lastServiceDate ?: return null
        
        val cycleWeeks = horse.shoeingCycleWeeks 
            ?: userPreferencesManager.getDefaultCycleWeeks()
        
        return lastServiceDate.plusWeeks(cycleWeeks.toLong())
    }
    
    fun getDueStatus(nextDueDate: LocalDate?): DueStatus {
        if (nextDueDate == null) return DueStatus.NEVER_SERVICED
        
        val today = LocalDate.now()
        val daysUntilDue = ChronoUnit.DAYS.between(today, nextDueDate).toInt()
        
        return when {
            daysUntilDue < 0 -> DueStatus.OVERDUE(abs(daysUntilDue))
            daysUntilDue <= 7 -> DueStatus.DUE_SOON(daysUntilDue)
            else -> DueStatus.OK(daysUntilDue)
        }
    }
}

sealed class DueStatus {
    data class OK(val daysUntil: Int) : DueStatus()
    data class DUE_SOON(val daysUntil: Int) : DueStatus()
    data class OVERDUE(val daysOverdue: Int) : DueStatus()
    object NEVER_SERVICED : DueStatus()
}
```

#### 2.4.2 Visual Status

| Status | Color | Icon | Text |
|--------|-------|------|------|
| OK (>7 days) | Green | 🟢 | "Due in X days" |
| Due Soon (1-7 days) | Yellow | 🟡 | "Due in X days" |
| Due Today | Orange | 🟠 | "Due today" |
| Overdue | Red | 🔴 | "X days overdue" |
| Never Serviced | Gray | ⚪ | "Never serviced" |

### 2.5 Photo Management

#### 2.5.1 Photo Capture

**Trigger:** Add Photo button on horse detail

**Options Dialog:**
- Take Photo (opens camera)
- Choose from Gallery
- Cancel

**Camera Implementation:**

```kotlin
// PhotoCaptureManager.kt
class PhotoCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressor: ImageCompressor
) {
    private var currentPhotoUri: Uri? = null
    
    fun createTakePictureIntent(): Intent {
        val photoFile = createImageFile()
        currentPhotoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        }
    }
    
    suspend fun processCapture(): Result<ProcessedPhoto> {
        val uri = currentPhotoUri ?: return Result.failure(Exception("No photo"))
        
        return try {
            // Compress to max 1MB
            val compressed = imageCompressor.compress(
                sourceUri = uri,
                maxSizeKB = 1024,
                maxDimension = 2048
            )
            
            Result.success(ProcessedPhoto(
                localUri = compressed.uri,
                sizeBytes = compressed.sizeBytes
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### 2.5.2 Photo Compression

| Parameter | Value |
|-----------|-------|
| Max file size | 1 MB |
| Max dimension | 2048 px |
| Format | JPEG |
| Quality | 85% |

```kotlin
class ImageCompressor {
    suspend fun compress(
        sourceUri: Uri,
        maxSizeKB: Int,
        maxDimension: Int
    ): CompressedImage = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(sourceUri)
        val scaled = scaleBitmap(bitmap, maxDimension)
        
        var quality = 85
        var bytes: ByteArray
        
        do {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 5
        } while (bytes.size > maxSizeKB * 1024 && quality > 20)
        
        val file = saveToFile(bytes)
        CompressedImage(uri = file.toUri(), sizeBytes = bytes.size)
    }
}
```

#### 2.5.3 Photo Storage

**Local Storage:**
- Directory: `app_data/horse_photos/{horseId}/`
- Filename: `{timestamp}_{uuid}.jpg`

**Cloud Storage (Supabase):**
- Bucket: `horse-photos`
- Path: `{userId}/{horseId}/{filename}`
- Access: Private to user

**Sync Behavior:**
1. Photo saved locally immediately
2. Photo added to upload queue
3. Upload occurs in background when online
4. `cloudUrl` updated after successful upload
5. Local file retained as cache

#### 2.5.4 Photo Entity

```kotlin
@Entity(
    tableName = "horse_photos",
    foreignKeys = [
        ForeignKey(
            entity = HorseEntity::class,
            parentColumns = ["id"],
            childColumns = ["horse_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["horse_id"])]
)
data class HorsePhotoEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "horse_id")
    val horseId: String,
    
    @ColumnInfo(name = "local_uri")
    val localUri: String,
    
    @ColumnInfo(name = "cloud_url")
    val cloudUrl: String? = null,
    
    @ColumnInfo(name = "appointment_id")
    val appointmentId: String? = null, // Link to specific service
    
    val caption: String? = null,
    
    @ColumnInfo(name = "taken_at")
    val takenAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Instant? = null,
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING_UPLOAD"
)
```

### 2.6 Service History

#### 2.6.1 History Entry Content

Each entry from completed appointments:

| Element | Source |
|---------|--------|
| Date | `appointment.completedAt` |
| Service Type | `appointmentHorse.serviceType` |
| Price | `appointmentHorse.price` |
| Notes | `appointmentHorse.notes` |
| Photos | Photos linked to this appointment |

#### 2.6.2 History Query

```kotlin
@Query("""
    SELECT 
        a.completed_at as date,
        ah.service_type,
        ah.price,
        ah.notes
    FROM appointments a
    INNER JOIN appointment_horses ah ON a.id = ah.appointment_id
    WHERE ah.horse_id = :horseId
    AND a.status = 'COMPLETED'
    ORDER BY a.completed_at DESC
""")
fun getServiceHistory(horseId: String): Flow<List<ServiceHistoryEntry>>
```

### 2.7 Global Due Soon List

**Screen:** `DueSoonScreen.kt`  
**Route:** `/due-soon`  
**Access:** From home screen widget or navigation menu

#### 2.7.1 UI Layout

```
┌────────────────────────────────────────┐
│ Due Soon                    [Filter ▼] │
├────────────────────────────────────────┤
│ Showing: Next 7 days (12 horses)       │
├────────────────────────────────────────┤
│ 🔴 OVERDUE                             │
│ ┌────────────────────────────────────┐ │
│ │ Lightning (Sarah Anderson)         │ │
│ │ Full Set • 3 days overdue          │ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│ 🟠 TODAY                               │
│ ┌────────────────────────────────────┐ │
│ │ Storm (Sarah Anderson)             │ │
│ │ Trim • Due today                   │ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│ 🟡 THIS WEEK                           │
│ ┌────────────────────────────────────┐ │
│ │ Thunder (Sarah Anderson)           │ │
│ │ Trim • Due in 5 days               │ │
│ ├────────────────────────────────────┤ │
│ │ Spirit (Michael Baker)             │ │
│ │ Front Shoes • Due in 6 days        │ │
│ └────────────────────────────────────┘ │
└────────────────────────────────────────┘
```

#### 2.7.2 Filter Options

| Filter | Options | Default |
|--------|---------|---------|
| Time Range | 7 days, 14 days, 30 days | 7 days |
| Include Scheduled | Yes/No | No (hide horses with appointments) |

#### 2.7.3 Query

```kotlin
@Query("""
    SELECT h.*, c.name as client_name
    FROM horses h
    INNER JOIN clients c ON h.client_id = c.id
    WHERE h.user_id = :userId
    AND h.is_active = 1
    AND h.next_due_date IS NOT NULL
    AND h.next_due_date <= date('now', '+' || :days || ' days')
    AND (:includeScheduled = 1 OR h.id NOT IN (
        SELECT ah.horse_id FROM appointment_horses ah
        INNER JOIN appointments a ON ah.appointment_id = a.id
        WHERE a.status = 'SCHEDULED' OR a.status = 'CONFIRMED'
    ))
    ORDER BY h.next_due_date ASC
""")
fun getDueSoonHorses(
    userId: String,
    days: Int,
    includeScheduled: Boolean
): Flow<List<HorseWithClient>>
```

---

## 3. Data Models

### 3.1 Horse Entity

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
        Index(value = ["next_due_date"]),
        Index(value = ["is_active"])
    ]
)
data class HorseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "client_id")
    val clientId: String,
    
    val name: String,
    
    val breed: String? = null,
    
    val color: String? = null,
    
    val age: Int? = null, // Years
    
    val temperament: String? = null, // HorseTemperament enum
    
    @ColumnInfo(name = "default_service_type")
    val defaultServiceType: String? = null,
    
    @ColumnInfo(name = "shoeing_cycle_weeks")
    val shoeingCycleWeeks: Int? = null, // Null = use user default
    
    @ColumnInfo(name = "last_service_date")
    val lastServiceDate: LocalDate? = null,
    
    @ColumnInfo(name = "next_due_date")
    val nextDueDate: LocalDate? = null, // Computed on service completion
    
    @ColumnInfo(name = "medical_notes")
    val medicalNotes: String? = null,
    
    @ColumnInfo(name = "primary_photo_id")
    val primaryPhotoId: String? = null,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "SYNCED"
) : SyncableEntity {
    override val syncEntityType = "horse"
    override val syncEntityId = id
}
```

### 3.2 Horse With Photos

```kotlin
data class HorseWithPhotos(
    @Embedded val horse: HorseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "horse_id"
    )
    val photos: List<HorsePhotoEntity>
)
```

---

## 4. Acceptance Criteria

### 4.1 Horse CRUD

| ID | Given | When | Then |
|----|-------|------|------|
| AC-004-01 | User on client detail | User taps Add Horse, fills form, saves | Horse appears in client's horse list |
| AC-004-02 | Horse form open | Name field empty, user taps Save | Error "Name is required" shown |
| AC-004-03 | Horse exists | User edits breed and saves | Breed updated, shown in detail |
| AC-004-04 | Horse linked to client | User archives horse | Horse hidden but history preserved |

### 4.2 Due Dates

| ID | Given | When | Then |
|----|-------|------|------|
| AC-004-05 | Horse serviced on Jan 1, 6-week cycle | Jan 15 | Status shows "Due in 27 days" (green) |
| AC-004-06 | Horse due in 5 days | User views horse | Status shows "Due in 5 days" (yellow) |
| AC-004-07 | Horse was due yesterday | User views horse | Status shows "1 day overdue" (red) |
| AC-004-08 | Horse never serviced | User views horse | Status shows "Never serviced" (gray) |
| AC-004-09 | Appointment completed | — | `lastServiceDate` and `nextDueDate` automatically updated |

### 4.3 Photos

| ID | Given | When | Then |
|----|-------|------|------|
| AC-004-10 | User on horse detail | User taps Add Photo, takes picture | Photo appears in horse gallery immediately |
| AC-004-11 | Photo is 5MB | Photo captured | Compressed to <1MB before saving |
| AC-004-12 | Device offline | User takes photo | Photo saved locally, syncs when online |
| AC-004-13 | Photo linked to appointment | User views service history | Photo appears with that service entry |

### 4.4 Due Soon List

| ID | Given | When | Then |
|----|-------|------|------|
| AC-004-14 | 3 horses due within 7 days | User views Due Soon | All 3 horses listed, sorted by due date |
| AC-004-15 | Horse due in 3 days has appointment | Filter set to hide scheduled | Horse not shown in Due Soon |
| AC-004-16 | User taps horse in Due Soon | — | Navigates to horse detail |
| AC-004-17 | No horses due soon | User views Due Soon | Empty state "All horses are on schedule!" |

---

## 5. Performance Requirements

| Metric | Target |
|--------|--------|
| Horse list load | < 300ms for 20 horses |
| Photo capture to display | < 1 second |
| Photo compression | < 2 seconds |
| Due soon query | < 500ms |
| Service history load | < 500ms for 50 entries |

---

## 6. File References

| Purpose | File Path |
|---------|-----------|
| Horse Detail Screen | `feature/horses/ui/HorseDetailScreen.kt` |
| Horse Form Screen | `feature/horses/ui/HorseFormScreen.kt` |
| Due Soon Screen | `feature/horses/ui/DueSoonScreen.kt` |
| Horse Entity | `core/database/entity/HorseEntity.kt` |
| Horse Photo Entity | `core/database/entity/HorsePhotoEntity.kt` |
| Horse DAO | `core/database/dao/HorseDao.kt` |
| Due Date Calculator | `feature/horses/domain/DueDateCalculator.kt` |
| Photo Capture Manager | `feature/horses/data/PhotoCaptureManager.kt` |
| Image Compressor | `core/common/ImageCompressor.kt` |
