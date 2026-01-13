# PRD-003: Client Management

**Priority**: P0  
**Phase**: 1 - Foundation  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Enable farriers to manage their client base with comprehensive contact information, location data for routing, and service preferences.

### Business Value
- Foundation for scheduling and routing features
- Enables personalized client communication
- Stores location data essential for route optimization
- Supports custom pricing per client

### Success Metrics
| Metric | Target |
|--------|--------|
| Client creation time | < 30 seconds |
| Address geocoding success | > 95% |
| Client search response | < 100ms |
| Data loss incidents | Zero |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-003-01 | Farrier | Add a new client with basic info | I can start scheduling | P0 |
| US-003-02 | Farrier | Enter client address with autocomplete | Location is accurate | P0 |
| US-003-03 | Farrier | Add access notes (gate codes) | I can find the barn | P0 |
| US-003-04 | Farrier | Search clients by name | I find clients quickly | P0 |
| US-003-05 | Farrier | View all horses for a client | I see the full picture | P0 |
| US-003-06 | Farrier | Set client's preferred days/times | I schedule appropriately | P1 |
| US-003-07 | Farrier | Configure reminder preferences | Some clients prefer different notice | P1 |
| US-003-08 | Farrier | Set custom pricing | Regular clients get special rates | P1 |
| US-003-09 | Farrier | Archive inactive clients | My list stays manageable | P1 |
| US-003-10 | Farrier | Call or text a client | I contact them quickly | P0 |
| US-003-11 | Farrier | Navigate to client location | I get directions easily | P0 |

---

## Functional Requirements

### FR-003-01: Create Client
- Required fields: name, phone
- Optional: email, address, notes
- Phone validation (US format)
- Email validation (if provided)
- Address autocomplete (Google Places)
- Automatic geocoding on address entry
- Access notes (multi-line, max 500 chars)
- General notes (multi-line, max 2000 chars)
- Save creates local record + queues sync

### FR-003-02: Address Handling
- Google Places Autocomplete
- Structured parsing (street, city, state, zip)
- Geocoding to lat/long (automatic)
- Manual coordinate entry (fallback)
- "Use Current Location" option
- Address validation before save

### FR-003-03: Client List
- Infinite scroll (50 per page)
- Search by name (instant, local)
- Filter: Active/Archived/All
- Sort: Name A-Z, Z-A, Recently Added, Recently Visited
- Pull-to-refresh
- Empty state with "Add Client" CTA

### FR-003-04: Client Detail View
- Full contact information
- Tap phone → call/text options
- Tap email → compose email
- Tap address → open navigation
- List of client's horses with status
- Upcoming appointments
- Past appointments history
- Outstanding invoices

### FR-003-05: Client Preferences
- Reminder preference: SMS, Email, Both, None
- Reminder timing: 12hr, 24hr, 48hr, 72hr
- Requires confirmation toggle
- Preferred days (Mon-Sun checkboxes)
- Preferred time range (start/end)

### FR-003-06: Custom Pricing
- Override default service prices
- Price per service type
- Percentage discount option
- Indicator when custom pricing active

### FR-003-07: Archive/Restore
- Soft delete (is_active = false)
- Archived hidden from main list
- Filter to show archived
- Restore option available

---

## Technical Implementation

```kotlin
// ClientRepository.kt
class ClientRepository @Inject constructor(
    private val clientDao: ClientDao,
    private val syncManager: SyncManager,
    private val geocoder: GeocodingService
) {
    fun getClients(
        query: String = "",
        showArchived: Boolean = false,
        sortBy: ClientSort = ClientSort.NAME_ASC
    ): Flow<List<Client>> {
        return clientDao.getClients(
            query = "%$query%",
            isActive = if (showArchived) null else true
        ).map { entities ->
            entities.map { it.toDomain() }
                .sortedWith(sortBy.comparator)
        }
    }
    
    suspend fun create(client: Client): Result<Client> {
        return try {
            val geocodedClient = if (client.address.isNotEmpty()) {
                val coords = geocoder.geocode(client.fullAddress)
                client.copy(latitude = coords?.lat, longitude = coords?.lng)
            } else client
            
            val entity = geocodedClient.toEntity().copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE
            )
            clientDao.insert(entity)
            
            syncManager.queueChange(
                entityType = "clients",
                entityId = entity.id,
                operation = SyncOperation.INSERT,
                payload = Json.encodeToString(entity)
            )
            Result.success(geocodedClient)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ClientDao.kt
@Dao
interface ClientDao {
    @Query("""
        SELECT * FROM clients 
        WHERE (:isActive IS NULL OR is_active = :isActive)
        AND (name LIKE :query OR phone LIKE :query OR city LIKE :query)
        ORDER BY name ASC
    """)
    fun getClients(query: String, isActive: Boolean?): Flow<List<ClientEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity)
    
    @Update
    suspend fun update(client: ClientEntity)
    
    @Query("UPDATE clients SET is_active = :isActive WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean)
}
```

---

## Data Model

```kotlin
@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["name"]),
        Index(value = ["is_active"]),
        Index(value = ["city"])
    ]
)
data class ClientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val email: String?,
    val phone: String,
    val address: String,
    val city: String,
    val state: String,
    val zip: String,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "access_notes") val accessNotes: String?,
    @ColumnInfo(name = "general_notes") val generalNotes: String?,
    @ColumnInfo(name = "custom_pricing") val customPricing: String?, // JSON
    @ColumnInfo(name = "reminder_preference") val reminderPreference: String = "sms",
    @ColumnInfo(name = "reminder_hours_before") val reminderHoursBefore: Int = 24,
    @ColumnInfo(name = "requires_confirmation") val requiresConfirmation: Boolean = false,
    @ColumnInfo(name = "preferred_days") val preferredDays: String?, // JSON
    @ColumnInfo(name = "preferred_time_start") val preferredTimeStart: String?,
    @ColumnInfo(name = "preferred_time_end") val preferredTimeEnd: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)

data class Client(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accessNotes: String? = null,
    val reminderPreference: ReminderPreference = ReminderPreference.SMS,
    val isActive: Boolean = true
) {
    val fullAddress: String
        get() = listOfNotNull(address, city, state, zip)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}

enum class ReminderPreference { SMS, EMAIL, BOTH, NONE }

enum class ClientSort(val comparator: Comparator<Client>) {
    NAME_ASC(compareBy { it.name }),
    NAME_DESC(compareByDescending { it.name }),
    RECENTLY_ADDED(compareByDescending { it.createdAt })
}
```

---

## Testing Requirements

```kotlin
class ClientViewModelTest {
    @Test
    fun `search filters clients locally`() = runTest {
        val clients = listOf(
            Client(name = "Johnson Ranch"),
            Client(name = "Williams Farm")
        )
        viewModel.search("john")
        assertEquals(1, viewModel.clients.first().size)
        assertEquals("Johnson Ranch", viewModel.clients.first()[0].name)
    }
}

class ClientRepositoryTest {
    @Test
    fun `create geocodes address before saving`() = runTest {
        coEvery { geocoder.geocode(any()) } returns Coordinates(30.2672, -97.7431)
        repository.create(client)
        coVerify {
            clientDao.insert(match { it.latitude == 30.2672 })
        }
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-003-01 | Create client with name and phone | E2E test |
| AC-003-02 | Address autocomplete works | Manual test |
| AC-003-03 | Client geocoded on save | Integration test |
| AC-003-04 | Search finds clients by name | Unit test |
| AC-003-05 | Can call client from detail | Manual test |
| AC-003-06 | Can navigate to address | Manual test |
| AC-003-07 | Archive hides from list | Integration test |
| AC-003-08 | Data available offline | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-001 (Auth) | Internal | Required |
| PRD-002 (Offline) | Internal | Required |
| Google Places API | External | Required |
