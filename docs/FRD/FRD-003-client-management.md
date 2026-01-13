# FRD-003: Client Management

**PRD Reference:** PRD-003  
**Priority:** P0  
**Phase:** 1 - Foundation  
**Estimated Duration:** 1.5 weeks

---

## 1. Overview

### 1.1 Purpose

Enable farriers to manage their client base with full offline support. Clients represent the horse owners/barn managers who schedule appointments. Each client can have multiple horses and custom pricing arrangements.

### 1.2 Scope

| In Scope | Out of Scope |
|----------|--------------|
| Client CRUD operations | Client self-service portal |
| Address geocoding | Multiple contacts per client |
| Access notes (gate codes) | Client messaging/chat |
| Custom pricing per client | Payment history tracking |
| Reminder preferences | Contract management |
| Archive/unarchive | Bulk import from file |
| Search and filter | |

---

## 2. Functional Specifications

### 2.1 Client List Screen

**Screen:** `ClientListScreen.kt`  
**ViewModel:** `ClientListViewModel.kt`  
**Route:** `/clients`

#### 2.1.1 UI Layout

```
┌────────────────────────────────────────┐
│ Clients                          [+]   │ ← App bar with add button
├────────────────────────────────────────┤
│ 🔍 Search clients...                   │ ← Search bar
├────────────────────────────────────────┤
│ Active (47) ▼  │  Sort: Name A-Z ▼     │ ← Filters
├────────────────────────────────────────┤
│ ┌────────────────────────────────────┐ │
│ │ 👤 Anderson, Sarah               > │ │
│ │    Lone Oak Farm • 3 horses        │ │
│ │    📍 Austin, TX                   │ │
│ └────────────────────────────────────┘ │
│ ┌────────────────────────────────────┐ │
│ │ 👤 Baker, Michael                > │ │
│ │    2 horses                        │ │
│ │    📍 Round Rock, TX               │ │
│ └────────────────────────────────────┘ │
│ ...                                    │
└────────────────────────────────────────┘
```

#### 2.1.2 List Item Content

| Element | Source | Fallback |
|---------|--------|----------|
| Client name | `{lastName}, {firstName}` | `{name}` if single name |
| Barn/farm name | `client.businessName` | Omit if empty |
| Horse count | Count of active horses | "No horses" if 0 |
| City, State | Parsed from address | Omit if no address |
| Avatar | First letter of last name | "?" if no name |

#### 2.1.3 Search Behavior

**Searchable Fields:**
- First name
- Last name
- Business name (farm/barn)
- Phone number
- City

**Search Implementation:**

```kotlin
@Query("""
    SELECT * FROM clients 
    WHERE user_id = :userId 
    AND is_active = :isActive
    AND (
        name LIKE '%' || :query || '%' 
        OR business_name LIKE '%' || :query || '%'
        OR phone LIKE '%' || :query || '%'
        OR city LIKE '%' || :query || '%'
    )
    ORDER BY 
        CASE WHEN :sortBy = 'name' THEN name END ASC,
        CASE WHEN :sortBy = 'city' THEN city END ASC,
        CASE WHEN :sortBy = 'recent' THEN updated_at END DESC
""")
fun searchClients(
    userId: String,
    query: String,
    isActive: Boolean = true,
    sortBy: String = "name"
): Flow<List<ClientEntity>>
```

**Debounce:** 300ms after last keystroke before executing search

#### 2.1.4 Filter Options

| Filter | Options | Default |
|--------|---------|---------|
| Status | Active, Archived, All | Active |
| Sort | Name A-Z, Name Z-A, City, Recently Updated | Name A-Z |

#### 2.1.5 Empty States

| State | Message | Action |
|-------|---------|--------|
| No clients | "No clients yet. Add your first client to get started." | "Add Client" button |
| No search results | "No clients match '{query}'" | "Clear search" link |
| No archived clients | "No archived clients" | — |

### 2.2 Client Detail Screen

**Screen:** `ClientDetailScreen.kt`  
**Route:** `/clients/{clientId}`

#### 2.2.1 UI Sections

**Header Section:**
```
┌────────────────────────────────────────┐
│ [←]  Sarah Anderson            [Edit]  │
│                                        │
│ 👤  Lone Oak Farm                      │
│ 📞  (512) 555-0123                     │
│ 📧  sarah@loneoakfarm.com              │
│ 📍  1234 Ranch Road, Austin, TX 78701  │
└────────────────────────────────────────┘
```

**Quick Actions:**

| Action | Icon | Behavior |
|--------|------|----------|
| Call | Phone icon | Opens dialer with client phone |
| Text | Message icon | Opens SMS with client phone |
| Email | Email icon | Opens email client (if email exists) |
| Navigate | Map icon | Opens navigation to client address |
| Schedule | Calendar+ icon | Opens appointment creation pre-filled |

**Horses Section:**
```
┌────────────────────────────────────────┐
│ Horses (3)                    [Add]    │
├────────────────────────────────────────┤
│ 🐴 Thunder                             │
│    Quarter Horse • Trim • Due in 5 days│
│ 🐴 Lightning                           │
│    Thoroughbred • Full Set • Due today │
│ 🐴 Storm                               │
│    Arabian • Trim • Due in 3 weeks     │
└────────────────────────────────────────┘
```

**Upcoming Appointments Section:**
```
┌────────────────────────────────────────┐
│ Upcoming (2)                           │
├────────────────────────────────────────┤
│ 📅 Thu, Jan 16 at 9:00 AM              │
│    Thunder, Lightning • $300           │
│ 📅 Thu, Feb 13 at 9:00 AM              │
│    All 3 horses • $420                 │
└────────────────────────────────────────┘
```

**Access Notes Section (if exists):**
```
┌────────────────────────────────────────┐
│ 🔑 Access Notes                        │
├────────────────────────────────────────┤
│ Gate code: 1234                        │
│ Park by the red barn                   │
└────────────────────────────────────────┘
```

**Notes Section (if exists):**
```
┌────────────────────────────────────────┐
│ 📝 Notes                               │
├────────────────────────────────────────┤
│ Prefers early morning appointments.    │
│ Cash payment on completion.            │
└────────────────────────────────────────┘
```

#### 2.2.2 Navigation Actions

| Tap Target | Navigation |
|------------|------------|
| Horse card | `/horses/{horseId}` |
| Appointment card | `/appointments/{appointmentId}` |
| Edit button | `/clients/{clientId}/edit` |
| Add Horse | `/clients/{clientId}/add-horse` |

### 2.3 Create/Edit Client

**Screen:** `ClientFormScreen.kt`  
**Route:** `/clients/new` or `/clients/{clientId}/edit`

#### 2.3.1 Form Fields

| Field | Type | Required | Validation | Max Length |
|-------|------|----------|------------|------------|
| First Name | Text | Yes | Non-empty | 50 |
| Last Name | Text | No | — | 50 |
| Business Name | Text | No | — | 100 |
| Phone | Phone | Yes | Valid US phone | 15 |
| Email | Email | No | Valid email format | 100 |
| Address | Autocomplete | No | — | 200 |
| Access Notes | TextArea | No | — | 500 |
| Notes | TextArea | No | — | 2000 |

#### 2.3.2 Address Autocomplete

**Implementation:**

```kotlin
@Composable
fun AddressAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    onAddressSelected: (PlaceDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    var suggestions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) {
        if (value.length >= 3) {
            delay(300) // Debounce
            suggestions = placesClient.findAutocompletePredictions(value)
            isExpanded = suggestions.isNotEmpty()
        } else {
            suggestions = emptyList()
            isExpanded = false
        }
    }
    
    ExposedDropdownMenuBox(expanded = isExpanded, onExpandedChange = { }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Address") },
            modifier = modifier.menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            suggestions.forEach { prediction ->
                DropdownMenuItem(
                    text = { Text(prediction.getFullText(null).toString()) },
                    onClick = {
                        // Fetch place details and geocode
                        val place = placesClient.fetchPlace(prediction.placeId)
                        onAddressSelected(place)
                        isExpanded = false
                    }
                )
            }
        }
    }
}
```

**Geocoding on Selection:**

```kotlin
data class PlaceDetails(
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val city: String?,
    val state: String?,
    val zipCode: String?
)

// When address selected:
val client = client.copy(
    address = place.formattedAddress,
    latitude = place.latitude,
    longitude = place.longitude,
    city = place.city,
    state = place.state,
    zipCode = place.zipCode
)
```

#### 2.3.3 Form Validation

| Validation | Trigger | Error Message |
|------------|---------|---------------|
| First name empty | On blur, on submit | "First name is required" |
| Phone empty | On blur, on submit | "Phone number is required" |
| Phone invalid | On blur | "Please enter a valid phone number" |
| Email invalid | On blur | "Please enter a valid email address" |
| Access notes too long | On input | Character counter turns red |

**Validation Implementation:**

```kotlin
data class ClientFormState(
    val firstName: String = "",
    val lastName: String = "",
    val businessName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val accessNotes: String = "",
    val notes: String = "",
    // Validation state
    val firstNameError: String? = null,
    val phoneError: String? = null,
    val emailError: String? = null
) {
    val isValid: Boolean
        get() = firstName.isNotBlank() 
            && phone.isNotBlank() 
            && PhoneValidator.validate(phone)
            && (email.isBlank() || EmailValidator.validate(email))
}

fun validateField(field: String, value: String): String? {
    return when (field) {
        "firstName" -> if (value.isBlank()) "First name is required" else null
        "phone" -> when {
            value.isBlank() -> "Phone number is required"
            !PhoneValidator.validate(value) -> "Please enter a valid phone number"
            else -> null
        }
        "email" -> when {
            value.isBlank() -> null // Optional
            !EmailValidator.validate(value) -> "Please enter a valid email address"
            else -> null
        }
        else -> null
    }
}
```

#### 2.3.4 Save Behavior

**Create Flow:**

1. User fills form
2. User taps "Save"
3. Validate all fields
4. If invalid: Show field errors, scroll to first error
5. If valid:
   - Generate UUID for client
   - Save to Room with `sync_status = PENDING_CREATE`
   - Add to sync queue
   - Navigate back to client list
   - Show toast: "Client added"
   - Trigger background sync

**Edit Flow:**

1. User modifies fields
2. User taps "Save"
3. Validate all fields
4. If valid:
   - Update Room with `sync_status = PENDING_UPDATE`
   - Add to sync queue
   - Navigate back to client detail
   - Show toast: "Client updated"

### 2.4 Custom Pricing

#### 2.4.1 Price Override UI

Within client edit screen:

```
┌────────────────────────────────────────┐
│ 💰 Custom Pricing                      │
│                                        │
│ [ ] Use custom prices for this client  │
│                                        │
│ If enabled:                            │
│ ┌────────────────────────────────────┐ │
│ │ Trim           $45 → [$__50___]   │ │
│ │ Front Shoes    $120 → [$________] │ │
│ │ Full Set       $180 → [$________] │ │
│ │ Corrective     $220 → [$________] │ │
│ └────────────────────────────────────┘ │
│                                        │
│ Leave blank to use default price       │
└────────────────────────────────────────┘
```

#### 2.4.2 Price Resolution Logic

```kotlin
// PriceResolver.kt
class PriceResolver @Inject constructor(
    private val servicePriceDao: ServicePriceDao,
    private val clientDao: ClientDao
) {
    suspend fun getPrice(
        clientId: String,
        serviceType: String
    ): BigDecimal {
        // Check client custom price first
        val client = clientDao.getById(clientId)
        val customPrices = client?.customPrices?.let { 
            Json.decodeFromString<Map<String, BigDecimal>>(it) 
        } ?: emptyMap()
        
        customPrices[serviceType]?.let { return it }
        
        // Fall back to default service price
        val defaultPrice = servicePriceDao.getByType(serviceType)
        return defaultPrice?.price ?: BigDecimal.ZERO
    }
}
```

### 2.5 Reminder Preferences

#### 2.5.1 UI Section

Within client edit screen:

```
┌────────────────────────────────────────┐
│ 🔔 Reminder Preferences                │
│                                        │
│ Send reminders via:                    │
│ ○ SMS                                  │
│ ○ Email                                │
│ ○ Both                                 │
│ ● None                                 │
│                                        │
│ Reminder timing:                       │
│ [ 24 hours before          ▼]          │
│                                        │
│ Options: 12, 24, 48, 72 hours          │
└────────────────────────────────────────┘
```

#### 2.5.2 Reminder Preference Logic

| Preference | SMS Sent | Email Sent |
|------------|----------|------------|
| SMS | Yes | No |
| Email | No | Yes |
| Both | Yes | Yes |
| None | No | No |

If client has no email and preference is "Email" or "Both", system falls back to SMS only.

### 2.6 Archive/Delete

#### 2.6.1 Archive Flow

**Trigger:** Long-press on client or overflow menu

**Confirmation Dialog:**
```
Archive "Sarah Anderson"?

This client and their horses will be hidden from your active list. 
You can restore them anytime from the Archived section.

Their appointment history will be preserved.

[Cancel]  [Archive]
```

**Behavior:**
- Set `is_active = false`
- Hide from default client list
- Visible in "Archived" filter
- All horses also archived
- Existing appointments unchanged
- Cannot create new appointments for archived clients

#### 2.6.2 Restore Flow

**Trigger:** From archived client detail, tap "Restore"

**Behavior:**
- Set `is_active = true`
- All horses also restored
- Toast: "Client restored"

#### 2.6.3 Delete Flow

**Trigger:** Only available for archived clients

**Confirmation Dialog:**
```
Permanently delete "Sarah Anderson"?

This will permanently delete:
• This client profile
• 3 horses
• 12 appointment records
• 8 invoices

This action cannot be undone.

[Cancel]  [Delete Forever]
```

**Behavior:**
- Hard delete from local database
- Cascade delete all related records
- Add DELETE operations to sync queue

---

## 3. Data Models

### 3.1 Client Entity

```kotlin
@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["name"]),
        Index(value = ["is_active"]),
        Index(value = ["city"]),
        Index(value = ["user_id", "is_active"])
    ]
)
data class ClientEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    val name: String, // "FirstName LastName"
    
    @ColumnInfo(name = "first_name")
    val firstName: String,
    
    @ColumnInfo(name = "last_name")
    val lastName: String? = null,
    
    @ColumnInfo(name = "business_name")
    val businessName: String? = null,
    
    val phone: String,
    
    val email: String? = null,
    
    val address: String? = null,
    
    val latitude: Double? = null,
    
    val longitude: Double? = null,
    
    val city: String? = null,
    
    val state: String? = null,
    
    @ColumnInfo(name = "zip_code")
    val zipCode: String? = null,
    
    @ColumnInfo(name = "access_notes")
    val accessNotes: String? = null, // Gate codes, max 500 chars
    
    val notes: String? = null, // General notes, max 2000 chars
    
    @ColumnInfo(name = "custom_prices")
    val customPrices: String? = null, // JSON map of serviceType -> price
    
    @ColumnInfo(name = "reminder_preference")
    val reminderPreference: String = "SMS", // SMS, EMAIL, BOTH, NONE
    
    @ColumnInfo(name = "reminder_hours")
    val reminderHours: Int = 24, // 12, 24, 48, 72
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "SYNCED"
) : SyncableEntity {
    override val syncEntityType = "client"
    override val syncEntityId = id
}
```

### 3.2 Client With Horses

```kotlin
data class ClientWithHorses(
    @Embedded val client: ClientEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "client_id"
    )
    val horses: List<HorseEntity>
)
```

### 3.3 Client Summary (for lists)

```kotlin
data class ClientSummary(
    val id: String,
    val name: String,
    val businessName: String?,
    val city: String?,
    val horseCount: Int,
    val hasOverdueHorses: Boolean,
    val nextAppointmentDate: LocalDate?
)
```

---

## 4. Error Handling

### 4.1 Form Errors

| Error | Message | Recovery |
|-------|---------|----------|
| Required field empty | "{Field} is required" | Focus field, show error |
| Invalid phone | "Please enter a valid phone number" | Focus field, show format hint |
| Invalid email | "Please enter a valid email address" | Focus field |
| Character limit exceeded | Counter turns red | Truncate not allowed |
| Geocoding failed | "Couldn't find this address" | Allow save without coordinates |

### 4.2 Operation Errors

| Error | Message | Recovery |
|-------|---------|----------|
| Save failed (offline) | — (optimistic, works offline) | — |
| Sync conflict | "This client was updated elsewhere" | Show conflict resolution |
| Delete failed (has appointments) | "Cannot delete: has future appointments" | Cancel upcoming first |

---

## 5. Acceptance Criteria

### 5.1 Client List

| ID | Given | When | Then |
|----|-------|------|------|
| AC-003-01 | User has 50 clients | User opens client list | All 50 clients load within 500ms, sorted A-Z |
| AC-003-02 | User has clients | User types "Sarah" in search | Only clients matching "Sarah" shown within 300ms of typing |
| AC-003-03 | Search has no results | — | "No clients match 'xyz'" message shown |
| AC-003-04 | User views client list | User selects "Archived" filter | Only archived clients shown |
| AC-003-05 | Client has 3 horses | User views client card | Card shows "3 horses" |

### 5.2 Create Client

| ID | Given | When | Then |
|----|-------|------|------|
| AC-003-06 | User on create screen | User enters valid data and taps Save | Client created, appears in list, toast shown |
| AC-003-07 | User on create screen | User leaves first name empty and taps Save | Error "First name is required" shown |
| AC-003-08 | User on create screen | User enters "555-123" as phone | Error "Please enter a valid phone number" shown |
| AC-003-09 | Device is offline | User creates client | Client saved locally with PENDING_CREATE status |
| AC-003-10 | User types address | 3+ characters entered | Address suggestions appear within 500ms |

### 5.3 Edit Client

| ID | Given | When | Then |
|----|-------|------|------|
| AC-003-11 | User viewing client | User taps Edit, changes name, taps Save | Name updated, shown in detail and list |
| AC-003-12 | User editing client | User clears phone field | Cannot save, error shown |
| AC-003-13 | User changes address | New address selected from autocomplete | Map pin updates, coordinates saved |

### 5.4 Archive/Delete

| ID | Given | When | Then |
|----|-------|------|------|
| AC-003-14 | Client has 2 horses | User archives client | Client and both horses hidden from active lists |
| AC-003-15 | Client is archived | User taps Restore | Client and horses appear in active lists |
| AC-003-16 | Client has future appointments | User tries to delete | Error: "Cannot delete: has future appointments" |
| AC-003-17 | Archived client with no appointments | User deletes | Confirmation shows count, client permanently removed |

### 5.5 Custom Pricing

| ID | Given | When | Then |
|----|-------|------|------|
| AC-003-18 | Client has custom price $50 for Trim | User creates appointment with Trim | $50 shown as default price |
| AC-003-19 | Client has no custom price for Full Set | User creates appointment with Full Set | Default price ($180) shown |

---

## 6. Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Client list load | < 500ms | From navigation to rendered list (100 clients) |
| Search response | < 300ms | From debounce complete to results |
| Address autocomplete | < 500ms | From query to suggestions |
| Save client | < 100ms | To local database |
| Client detail load | < 300ms | With horses and appointments |

---

## 7. Test Specifications

### 7.1 Unit Tests

```kotlin
class ClientFormValidationTest {
    @Test
    fun `empty first name shows error`() {
        val state = ClientFormState(firstName = "", phone = "5551234567")
        val error = validateField("firstName", state.firstName)
        assertEquals("First name is required", error)
    }
    
    @Test
    fun `valid phone passes validation`() {
        assertTrue(PhoneValidator.validate("(555) 123-4567"))
        assertTrue(PhoneValidator.validate("5551234567"))
        assertTrue(PhoneValidator.validate("+1 555-123-4567"))
    }
    
    @Test
    fun `invalid phone fails validation`() {
        assertFalse(PhoneValidator.validate("555-1234"))
        assertFalse(PhoneValidator.validate("abcdefghij"))
    }
}

class PriceResolverTest {
    @Test
    fun `custom price overrides default`() = runTest {
        val client = ClientEntity(
            id = "client1",
            customPrices = """{"trim": 50.00}"""
        )
        coEvery { clientDao.getById("client1") } returns client
        
        val price = priceResolver.getPrice("client1", "trim")
        
        assertEquals(BigDecimal("50.00"), price)
    }
    
    @Test
    fun `missing custom price uses default`() = runTest {
        val client = ClientEntity(id = "client1", customPrices = null)
        coEvery { clientDao.getById("client1") } returns client
        coEvery { servicePriceDao.getByType("trim") } returns 
            ServicePriceEntity(price = BigDecimal("45.00"))
        
        val price = priceResolver.getPrice("client1", "trim")
        
        assertEquals(BigDecimal("45.00"), price)
    }
}
```

### 7.2 Integration Tests

```kotlin
@HiltAndroidTest
class ClientRepositoryTest {
    @Test
    fun createClient_savesToLocalAndQueuesSync() = runTest {
        val client = ClientEntity(
            firstName = "Test",
            phone = "5551234567",
            userId = testUserId
        )
        
        repository.create(client)
        
        val saved = clientDao.getById(client.id)
        assertNotNull(saved)
        assertEquals("PENDING_CREATE", saved?.syncStatus)
        
        val queueEntry = syncQueueDao.getLatestForEntity("client", client.id)
        assertNotNull(queueEntry)
        assertEquals("CREATE", queueEntry?.operation)
    }
    
    @Test
    fun archiveClient_archivesAllHorses() = runTest {
        val client = createTestClient()
        val horse1 = createTestHorse(clientId = client.id)
        val horse2 = createTestHorse(clientId = client.id)
        
        repository.archive(client.id)
        
        assertFalse(clientDao.getById(client.id)!!.isActive)
        assertFalse(horseDao.getById(horse1.id)!!.isActive)
        assertFalse(horseDao.getById(horse2.id)!!.isActive)
    }
}
```

---

## 8. File References

| Purpose | File Path |
|---------|-----------|
| Client List Screen | `feature/clients/ui/ClientListScreen.kt` |
| Client Detail Screen | `feature/clients/ui/ClientDetailScreen.kt` |
| Client Form Screen | `feature/clients/ui/ClientFormScreen.kt` |
| Client List ViewModel | `feature/clients/ui/ClientListViewModel.kt` |
| Client Entity | `core/database/entity/ClientEntity.kt` |
| Client DAO | `core/database/dao/ClientDao.kt` |
| Client Repository | `feature/clients/data/ClientRepository.kt` |
| Address Autocomplete | `feature/clients/ui/component/AddressAutocomplete.kt` |
| Price Resolver | `feature/clients/domain/PriceResolver.kt` |
| Phone Validator | `core/common/PhoneValidator.kt` |

---

## 9. Dependencies

| Dependency | Purpose | Notes |
|------------|---------|-------|
| Google Places SDK | Address autocomplete | Requires API key |
| Google Geocoding API | Address → coordinates | Via Places SDK |
| libphonenumber | Phone validation | Optional, can use regex |
