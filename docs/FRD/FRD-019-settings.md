# FRD-019: Settings & Preferences

**Source PRD**: PRD-019-settings.md  
**Priority**: P1  
**Phase**: 6 - Polish & Launch  
**Estimated Duration**: 3 days

---

## 1. Overview

### 1.1 Purpose
Provide a centralized location for app configuration, user preferences, account management, and data portability that allows farriers to customize their Hoof Direct experience.

### 1.2 Scope
This FRD covers:
- Profile settings (business info, address, service radius)
- Scheduling defaults (duration, cycle, working hours)
- Notification preferences (push, SMS, email, digest)
- Calendar sync configuration
- Data export and account deletion
- App information and support access

### 1.3 Dependencies
| Dependency | FRD | Purpose |
|------------|-----|---------|
| Authentication | FRD-001 | User account context |
| Calendar Sync | FRD-007 | Calendar settings |
| Reminders | FRD-008 | Notification settings |
| Subscriptions | FRD-016 | Subscription management |
| Usage Limits | FRD-017 | Usage display |

---

## 2. Data Models

### 2.1 User Profile

```kotlin
// core/domain/model/UserProfile.kt
data class UserProfile(
    val id: String,
    val email: String,
    val businessName: String,
    val phone: String?,
    val homeAddress: Address?,
    val serviceRadiusMiles: Int,
    val photoUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val latitude: Double?,
    val longitude: Double?
)
```

### 2.2 User Preferences

```kotlin
// core/domain/model/UserPreferences.kt
data class UserPreferences(
    // Scheduling defaults
    val defaultAppointmentDuration: Int = 45,      // minutes
    val defaultShoeingCycle: Int = 6,              // weeks
    val defaultReminderDays: Int = 3,              // days before
    val workingHours: WorkingHours = WorkingHours.default(),
    
    // Notifications
    val pushNotificationsEnabled: Boolean = true,
    val smsRemindersEnabled: Boolean = true,
    val emailRemindersEnabled: Boolean = false,
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestTime: LocalTime = LocalTime.of(6, 0),  // 6:00 AM
    
    // Calendar
    val calendarExportEnabled: Boolean = false,
    val calendarImportEnabled: Boolean = false,
    val importCalendarIds: List<String> = emptyList()
)

data class WorkingHours(
    val monday: DaySchedule,
    val tuesday: DaySchedule,
    val wednesday: DaySchedule,
    val thursday: DaySchedule,
    val friday: DaySchedule,
    val saturday: DaySchedule,
    val sunday: DaySchedule
) {
    companion object {
        fun default() = WorkingHours(
            monday = DaySchedule(enabled = true, start = LocalTime.of(8, 0), end = LocalTime.of(17, 0)),
            tuesday = DaySchedule(enabled = true, start = LocalTime.of(8, 0), end = LocalTime.of(17, 0)),
            wednesday = DaySchedule(enabled = true, start = LocalTime.of(8, 0), end = LocalTime.of(17, 0)),
            thursday = DaySchedule(enabled = true, start = LocalTime.of(8, 0), end = LocalTime.of(17, 0)),
            friday = DaySchedule(enabled = true, start = LocalTime.of(8, 0), end = LocalTime.of(17, 0)),
            saturday = DaySchedule(enabled = false, start = LocalTime.of(8, 0), end = LocalTime.of(12, 0)),
            sunday = DaySchedule(enabled = false, start = LocalTime.of(8, 0), end = LocalTime.of(12, 0))
        )
    }
}

data class DaySchedule(
    val enabled: Boolean,
    val start: LocalTime,
    val end: LocalTime
)
```

### 2.3 DataStore Keys

```kotlin
// core/data/preferences/PreferenceKeys.kt
object PreferenceKeys {
    // Scheduling
    val DEFAULT_DURATION = intPreferencesKey("default_duration")
    val DEFAULT_CYCLE = intPreferencesKey("default_cycle")
    val DEFAULT_REMINDER_DAYS = intPreferencesKey("default_reminder_days")
    val WORKING_HOURS = stringPreferencesKey("working_hours_json")
    
    // Notifications
    val PUSH_ENABLED = booleanPreferencesKey("push_enabled")
    val SMS_REMINDERS_ENABLED = booleanPreferencesKey("sms_reminders_enabled")
    val EMAIL_REMINDERS_ENABLED = booleanPreferencesKey("email_reminders_enabled")
    val DAILY_DIGEST_ENABLED = booleanPreferencesKey("daily_digest_enabled")
    val DAILY_DIGEST_TIME = stringPreferencesKey("daily_digest_time")
    
    // Calendar
    val CALENDAR_EXPORT_ENABLED = booleanPreferencesKey("calendar_export_enabled")
    val CALENDAR_IMPORT_ENABLED = booleanPreferencesKey("calendar_import_enabled")
    val IMPORT_CALENDAR_IDS = stringSetPreferencesKey("import_calendar_ids")
    
    // Profile (stored in Supabase, cached locally)
    val CACHED_PROFILE_JSON = stringPreferencesKey("cached_profile_json")
}
```

---

## 3. User Preferences Manager

### 3.1 Implementation

```kotlin
// core/data/preferences/UserPreferencesManager.kt
@Singleton
class UserPreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    // Scheduling flows
    val defaultDuration: Flow<Int> = dataStore.data
        .map { it[PreferenceKeys.DEFAULT_DURATION] ?: 45 }
    
    val defaultCycle: Flow<Int> = dataStore.data
        .map { it[PreferenceKeys.DEFAULT_CYCLE] ?: 6 }
    
    val defaultReminderDays: Flow<Int> = dataStore.data
        .map { it[PreferenceKeys.DEFAULT_REMINDER_DAYS] ?: 3 }
    
    val workingHours: Flow<WorkingHours> = dataStore.data
        .map { prefs ->
            prefs[PreferenceKeys.WORKING_HOURS]?.let { 
                json.decodeFromString<WorkingHours>(it) 
            } ?: WorkingHours.default()
        }
    
    // Notification flows
    val pushEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.PUSH_ENABLED] ?: true }
    
    val smsRemindersEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.SMS_REMINDERS_ENABLED] ?: true }
    
    val emailRemindersEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.EMAIL_REMINDERS_ENABLED] ?: false }
    
    val dailyDigestEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.DAILY_DIGEST_ENABLED] ?: true }
    
    val dailyDigestTime: Flow<LocalTime> = dataStore.data
        .map { prefs ->
            prefs[PreferenceKeys.DAILY_DIGEST_TIME]?.let { LocalTime.parse(it) }
                ?: LocalTime.of(6, 0)
        }
    
    // Calendar flows
    val calendarExportEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.CALENDAR_EXPORT_ENABLED] ?: false }
    
    val calendarImportEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.CALENDAR_IMPORT_ENABLED] ?: false }
    
    val importCalendarIds: Flow<Set<String>> = dataStore.data
        .map { it[PreferenceKeys.IMPORT_CALENDAR_IDS] ?: emptySet() }
    
    // Combined preferences
    val preferences: Flow<UserPreferences> = combine(
        defaultDuration, defaultCycle, defaultReminderDays, workingHours,
        pushEnabled, smsRemindersEnabled, emailRemindersEnabled, 
        dailyDigestEnabled, dailyDigestTime,
        calendarExportEnabled, calendarImportEnabled, importCalendarIds
    ) { values ->
        UserPreferences(
            defaultAppointmentDuration = values[0] as Int,
            defaultShoeingCycle = values[1] as Int,
            defaultReminderDays = values[2] as Int,
            workingHours = values[3] as WorkingHours,
            pushNotificationsEnabled = values[4] as Boolean,
            smsRemindersEnabled = values[5] as Boolean,
            emailRemindersEnabled = values[6] as Boolean,
            dailyDigestEnabled = values[7] as Boolean,
            dailyDigestTime = values[8] as LocalTime,
            calendarExportEnabled = values[9] as Boolean,
            calendarImportEnabled = values[10] as Boolean,
            importCalendarIds = (values[11] as Set<String>).toList()
        )
    }
    
    // Setters
    suspend fun setDefaultDuration(minutes: Int) {
        require(minutes in 15..480) { "Duration must be 15-480 minutes" }
        dataStore.edit { it[PreferenceKeys.DEFAULT_DURATION] = minutes }
    }
    
    suspend fun setDefaultCycle(weeks: Int) {
        require(weeks in 1..52) { "Cycle must be 1-52 weeks" }
        dataStore.edit { it[PreferenceKeys.DEFAULT_CYCLE] = weeks }
    }
    
    suspend fun setDefaultReminderDays(days: Int) {
        require(days in 1..30) { "Reminder days must be 1-30" }
        dataStore.edit { it[PreferenceKeys.DEFAULT_REMINDER_DAYS] = days }
    }
    
    suspend fun setWorkingHours(hours: WorkingHours) {
        dataStore.edit { 
            it[PreferenceKeys.WORKING_HOURS] = json.encodeToString(hours) 
        }
    }
    
    suspend fun setPushEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.PUSH_ENABLED] = enabled }
    }
    
    suspend fun setSmsRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.SMS_REMINDERS_ENABLED] = enabled }
    }
    
    suspend fun setEmailRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.EMAIL_REMINDERS_ENABLED] = enabled }
    }
    
    suspend fun setDailyDigestEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DAILY_DIGEST_ENABLED] = enabled }
    }
    
    suspend fun setDailyDigestTime(time: LocalTime) {
        dataStore.edit { it[PreferenceKeys.DAILY_DIGEST_TIME] = time.toString() }
    }
    
    suspend fun setCalendarExportEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.CALENDAR_EXPORT_ENABLED] = enabled }
    }
    
    suspend fun setCalendarImportEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.CALENDAR_IMPORT_ENABLED] = enabled }
    }
    
    suspend fun setImportCalendarIds(ids: Set<String>) {
        dataStore.edit { it[PreferenceKeys.IMPORT_CALENDAR_IDS] = ids }
    }
}
```

### 3.2 Validation Rules

| Preference | Valid Range | Default |
|------------|-------------|---------|
| Default Duration | 15-480 minutes | 45 minutes |
| Default Cycle | 1-52 weeks | 6 weeks |
| Default Reminder Days | 1-30 days | 3 days |
| Working Hours Start | 00:00-23:59 | 08:00 |
| Working Hours End | 00:00-23:59 | 17:00 |
| Daily Digest Time | 00:00-23:59 | 06:00 |
| Service Radius | 1-500 miles | 50 miles |

---

## 4. Settings Screen

### 4.1 Main Settings Screen

```
┌─────────────────────────────────────────┐
│ [←] Settings                            │
├─────────────────────────────────────────┤
│                                         │
│  ACCOUNT                                │
│  ─────────────────────────────────────  │
│  [👤] Profile                       >   │
│  [💳] Subscription                  >   │
│  [📊] Usage                         >   │
│  [💰] Payment Methods               >   │
│                                         │
│  SCHEDULING                             │
│  ─────────────────────────────────────  │
│  Default Duration          45 min   >   │
│  Default Shoeing Cycle     6 weeks  >   │
│  Default Reminder          3 days   >   │
│  Working Hours                      >   │
│                                         │
│  NOTIFICATIONS                          │
│  ─────────────────────────────────────  │
│  Push Notifications            [ON]     │
│  SMS Reminders                 [ON]     │
│  Email Reminders              [OFF]     │
│  Daily Digest                  [ON]     │
│  Digest Time               6:00 AM  >   │
│                                         │
│  CALENDAR                               │
│  ─────────────────────────────────────  │
│  Calendar Sync                      >   │
│                                         │
│  DATA                                   │
│  ─────────────────────────────────────  │
│  [↓] Export My Data                 >   │
│  [🗑] Delete Account                >   │
│                                         │
│  ABOUT                                  │
│  ─────────────────────────────────────  │
│  Version 1.0.0 (Build 42)              │
│  Privacy Policy                     >   │
│  Terms of Service                   >   │
│  Send Feedback                      >   │
│  Contact Support                    >   │
│                                         │
│  [Sign Out]                             │
│                                         │
└─────────────────────────────────────────┘
```

### 4.2 Navigation Structure

```kotlin
// feature/settings/navigation/SettingsNavigation.kt
sealed class SettingsDestination(val route: String) {
    object Main : SettingsDestination("settings")
    object Profile : SettingsDestination("settings/profile")
    object Subscription : SettingsDestination("settings/subscription")
    object Usage : SettingsDestination("settings/usage")
    object PaymentMethods : SettingsDestination("settings/payment-methods")
    object WorkingHours : SettingsDestination("settings/working-hours")
    object CalendarSync : SettingsDestination("settings/calendar-sync")
    object ExportData : SettingsDestination("settings/export")
    object DeleteAccount : SettingsDestination("settings/delete-account")
}
```

### 4.3 Settings Section Components

```kotlin
// feature/settings/ui/components/SettingsSection.kt
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Divider()
        content()
    }
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector? = null,
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, modifier = Modifier.padding(end = 12.dp))
            }
            Text(title)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            value?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (enabled) 
                MaterialTheme.colorScheme.onSurface 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
```

---

## 5. Profile Settings

### 5.1 Profile Screen

```
┌─────────────────────────────────────────┐
│ [←] Profile                    [Save]   │
├─────────────────────────────────────────┤
│                                         │
│           ┌─────────────┐               │
│           │             │               │
│           │   [Photo]   │               │
│           │             │               │
│           └─────────────┘               │
│           Change Photo                  │
│                                         │
│  Business Name                          │
│  ┌─────────────────────────────────┐   │
│  │ Smith Farrier Services          │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Phone                                  │
│  ┌─────────────────────────────────┐   │
│  │ (555) 123-4567                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Email (read-only)                      │
│  ┌─────────────────────────────────┐   │
│  │ john@example.com                │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Home Address                           │
│  ┌─────────────────────────────────┐   │
│  │ 123 Main St                     │   │
│  │ Anytown, TX 75001               │   │
│  └─────────────────────────────────┘   │
│  [Change Address]                       │
│                                         │
│  Service Radius                         │
│  ──●────────────────────────  50 mi    │
│  How far you're willing to travel       │
│                                         │
└─────────────────────────────────────────┘
```

### 5.2 Profile Repository

```kotlin
// core/data/repository/ProfileRepository.kt
interface ProfileRepository {
    fun getProfile(): Flow<UserProfile?>
    suspend fun updateProfile(profile: UserProfile): Result<UserProfile>
    suspend fun updatePhoto(imageUri: Uri): Result<String>
    suspend fun deletePhoto(): Result<Unit>
    suspend fun geocodeAddress(address: Address): Result<Address>
}

// Implementation
class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val storageManager: StorageManager,
    private val geocoder: Geocoder,
    private val preferencesManager: UserPreferencesManager
) : ProfileRepository {
    
    override fun getProfile(): Flow<UserProfile?> = flow {
        // Try cached first
        preferencesManager.cachedProfile.firstOrNull()?.let { emit(it) }
        
        // Fetch from server
        try {
            val profile = supabase.from("profiles")
                .select()
                .single()
                .decodeAs<UserProfile>()
            preferencesManager.cacheProfile(profile)
            emit(profile)
        } catch (e: Exception) {
            // Use cached if network fails
        }
    }
    
    override suspend fun updateProfile(profile: UserProfile): Result<UserProfile> {
        return try {
            val updated = supabase.from("profiles")
                .update(profile)
                .eq("id", profile.id)
                .single()
                .decodeAs<UserProfile>()
            preferencesManager.cacheProfile(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updatePhoto(imageUri: Uri): Result<String> {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id 
                ?: throw Exception("Not authenticated")
            
            val path = "profiles/$userId/avatar.jpg"
            val bytes = storageManager.compressImage(imageUri, maxSize = 512)
            
            supabase.storage.from("avatars")
                .upload(path, bytes, upsert = true)
            
            val url = supabase.storage.from("avatars")
                .publicUrl(path)
            
            updateProfile(getProfile().first()!!.copy(photoUrl = url))
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun geocodeAddress(address: Address): Result<Address> {
        return try {
            val results = geocoder.getFromLocationName(
                "${address.street}, ${address.city}, ${address.state} ${address.zipCode}",
                1
            )
            if (results.isNullOrEmpty()) {
                Result.failure(Exception("Address not found"))
            } else {
                Result.success(address.copy(
                    latitude = results[0].latitude,
                    longitude = results[0].longitude
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 6. Working Hours Settings

### 6.1 Working Hours Screen

```
┌─────────────────────────────────────────┐
│ [←] Working Hours              [Save]   │
├─────────────────────────────────────────┤
│                                         │
│  Set your typical working schedule.     │
│  This helps with scheduling             │
│  suggestions and daily digest timing.   │
│                                         │
│  Monday                        [ON]     │
│  ┌─────────────┐  ┌─────────────┐      │
│  │  8:00 AM    │  │  5:00 PM    │      │
│  └─────────────┘  └─────────────┘      │
│       Start            End              │
│                                         │
│  Tuesday                       [ON]     │
│  ┌─────────────┐  ┌─────────────┐      │
│  │  8:00 AM    │  │  5:00 PM    │      │
│  └─────────────┘  └─────────────┘      │
│                                         │
│  Wednesday                     [ON]     │
│  ┌─────────────┐  ┌─────────────┐      │
│  │  8:00 AM    │  │  5:00 PM    │      │
│  └─────────────┘  └─────────────┘      │
│                                         │
│  Thursday                      [ON]     │
│  ┌─────────────┐  ┌─────────────┐      │
│  │  8:00 AM    │  │  5:00 PM    │      │
│  └─────────────┘  └─────────────┘      │
│                                         │
│  Friday                        [ON]     │
│  ┌─────────────┐  ┌─────────────┐      │
│  │  8:00 AM    │  │  5:00 PM    │      │
│  └─────────────┘  └─────────────┘      │
│                                         │
│  Saturday                     [OFF]     │
│                                         │
│  Sunday                       [OFF]     │
│                                         │
│  [Reset to Defaults]                    │
│                                         │
└─────────────────────────────────────────┘
```

### 6.2 Validation Rules

| Rule | Validation |
|------|------------|
| End after Start | end time > start time |
| At least 1 hour | (end - start) >= 60 minutes |
| Max 16 hours | (end - start) <= 16 hours |
| At least 1 day enabled | sum(enabled days) >= 1 |

---

## 7. Data Export

### 7.1 Export Data Screen

```
┌─────────────────────────────────────────┐
│ [←] Export My Data                      │
├─────────────────────────────────────────┤
│                                         │
│  Download all your data in JSON format. │
│  This includes:                         │
│                                         │
│  • Clients and contact info             │
│  • Horses and service history           │
│  • Appointments                         │
│  • Invoices                             │
│  • Mileage records                      │
│  • Settings and preferences             │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  │      [Download Icon]            │   │
│  │                                 │   │
│  │   Your export is ready!         │   │
│  │   125 KB • 3 clients            │   │
│  │   12 horses • 47 appointments   │   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │       Download JSON File        │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Last exported: Never                   │
│                                         │
└─────────────────────────────────────────┘
```

### 7.2 Export Data Format

```kotlin
// core/data/export/DataExporter.kt
@Serializable
data class ExportedData(
    val exportDate: String,             // ISO 8601
    val appVersion: String,
    val profile: ExportedProfile,
    val clients: List<ExportedClient>,
    val horses: List<ExportedHorse>,
    val appointments: List<ExportedAppointment>,
    val invoices: List<ExportedInvoice>,
    val mileageTrips: List<ExportedMileageTrip>,
    val services: List<ExportedService>,
    val preferences: ExportedPreferences
)

@Serializable
data class ExportedClient(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val notes: String?,
    val createdAt: String,
    val horses: List<String>  // Horse IDs
)

@Serializable
data class ExportedHorse(
    val id: String,
    val clientId: String,
    val name: String,
    val breed: String?,
    val color: String?,
    val notes: String?,
    val shoeingCycleWeeks: Int,
    val lastServiceDate: String?,
    val nextDueDate: String?
)

// ... similar for appointments, invoices, mileage
```

### 7.3 Export Repository

```kotlin
// core/data/export/ExportRepository.kt
interface ExportRepository {
    suspend fun generateExport(): Result<ExportedData>
    suspend fun saveExportToFile(data: ExportedData): Result<Uri>
    fun getLastExportDate(): Flow<Instant?>
}

class ExportRepositoryImpl @Inject constructor(
    private val clientDao: ClientDao,
    private val horseDao: HorseDao,
    private val appointmentDao: AppointmentDao,
    private val invoiceDao: InvoiceDao,
    private val mileageDao: MileageDao,
    private val serviceDao: ServiceDao,
    private val preferencesManager: UserPreferencesManager,
    private val profileRepository: ProfileRepository,
    private val context: Context,
    private val json: Json
) : ExportRepository {
    
    override suspend fun generateExport(): Result<ExportedData> {
        return try {
            val data = ExportedData(
                exportDate = Clock.System.now().toString(),
                appVersion = BuildConfig.VERSION_NAME,
                profile = profileRepository.getProfile().first()!!.toExported(),
                clients = clientDao.getAllClients().first().map { it.toExported() },
                horses = horseDao.getAllHorses().first().map { it.toExported() },
                appointments = appointmentDao.getAllAppointments().first().map { it.toExported() },
                invoices = invoiceDao.getAllInvoices().first().map { it.toExported() },
                mileageTrips = mileageDao.getAllTrips().first().map { it.toExported() },
                services = serviceDao.getAllServices().first().map { it.toExported() },
                preferences = preferencesManager.preferences.first().toExported()
            )
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun saveExportToFile(data: ExportedData): Result<Uri> {
        return try {
            val fileName = "hoof_direct_export_${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
            }.json"
            
            val file = File(context.cacheDir, fileName)
            file.writeText(json.encodeToString(data))
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            preferencesManager.setLastExportDate(Clock.System.now())
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 8. Account Deletion

### 8.1 Delete Account Flow

```
State Machine:
┌──────────┐     Tap Delete      ┌─────────────┐
│  IDLE    │ ─────────────────> │  CONFIRM_1  │
└──────────┘                     └─────────────┘
                                       │
                                 Tap "Continue"
                                       │
                                       ▼
                                ┌─────────────┐
                                │  CONFIRM_2  │
                                └─────────────┘
                                       │
                              Type "DELETE" + Tap
                                       │
                                       ▼
                                ┌─────────────┐
                                │  DELETING   │
                                └─────────────┘
                                       │
                               30-day scheduled
                                       │
                                       ▼
                                ┌─────────────┐
                                │  SCHEDULED  │
                                └─────────────┘
```

### 8.2 Delete Account Screen

```
┌─────────────────────────────────────────┐
│ [←] Delete Account                      │
├─────────────────────────────────────────┤
│                                         │
│  ⚠️ This action cannot be undone        │
│                                         │
│  Deleting your account will:            │
│                                         │
│  • Remove all your data after 30 days   │
│  • Cancel any active subscription       │
│  • Sign you out of all devices          │
│  • Delete all synced calendar events    │
│                                         │
│  Your data will be retained for 30      │
│  days in case you change your mind.     │
│  After that, it will be permanently     │
│  deleted.                               │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Type "DELETE" to confirm               │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │     Delete My Account           │   │
│  └─────────────────────────────────┘   │
│  Button disabled until "DELETE" typed   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Need help? Contact support@            │
│  hoofdirect.com instead                 │
│                                         │
└─────────────────────────────────────────┘
```

### 8.3 Account Deletion Implementation

```kotlin
// core/data/repository/AccountRepository.kt
interface AccountRepository {
    suspend fun requestAccountDeletion(): Result<Instant>  // Returns scheduled deletion date
    suspend fun cancelDeletionRequest(): Result<Unit>
    fun getDeletionScheduledDate(): Flow<Instant?>
}

class AccountRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository
) : AccountRepository {
    
    override suspend fun requestAccountDeletion(): Result<Instant> {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id 
                ?: throw Exception("Not authenticated")
            
            val scheduledDate = Clock.System.now().plus(30.days)
            
            // Mark account for deletion
            supabase.from("profiles")
                .update(mapOf(
                    "deletion_scheduled_at" to scheduledDate.toString(),
                    "deletion_requested_at" to Clock.System.now().toString()
                ))
                .eq("id", userId)
            
            // Cancel any active subscription
            supabase.functions.invoke("cancel-subscription")
            
            // Send confirmation email
            supabase.functions.invoke("send-deletion-confirmation")
            
            // Sign out
            authRepository.signOut()
            
            Result.success(scheduledDate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun cancelDeletionRequest(): Result<Unit> {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id 
                ?: throw Exception("Not authenticated")
            
            supabase.from("profiles")
                .update(mapOf(
                    "deletion_scheduled_at" to null,
                    "deletion_requested_at" to null
                ))
                .eq("id", userId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 9. About & Support

### 9.1 App Information

```kotlin
// core/util/AppInfo.kt
object AppInfo {
    val versionName: String get() = BuildConfig.VERSION_NAME
    val versionCode: Int get() = BuildConfig.VERSION_CODE
    val buildType: String get() = BuildConfig.BUILD_TYPE
    
    val displayVersion: String get() = "$versionName (Build $versionCode)"
}
```

### 9.2 External Links

| Item | URL | Action |
|------|-----|--------|
| Privacy Policy | https://hoofdirect.com/privacy | Open in browser |
| Terms of Service | https://hoofdirect.com/terms | Open in browser |
| Send Feedback | https://hoofdirect.canny.io | Open in browser |
| Contact Support | mailto:support@hoofdirect.com | Open email client |

### 9.3 Support Actions

```kotlin
// feature/settings/ui/SettingsViewModel.kt
fun openPrivacyPolicy() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://hoofdirect.com/privacy"))
    context.startActivity(intent)
}

fun openTermsOfService() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://hoofdirect.com/terms"))
    context.startActivity(intent)
}

fun openFeedback() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://hoofdirect.canny.io"))
    context.startActivity(intent)
}

fun contactSupport() {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:support@hoofdirect.com")
        putExtra(Intent.EXTRA_SUBJECT, "Hoof Direct Support Request")
        putExtra(Intent.EXTRA_TEXT, buildSupportEmailBody())
    }
    context.startActivity(intent)
}

private fun buildSupportEmailBody(): String {
    return """
        
        ---
        App Version: ${AppInfo.displayVersion}
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android Version: ${Build.VERSION.RELEASE}
        User ID: ${authRepository.currentUserId ?: "Not signed in"}
    """.trimIndent()
}
```

---

## 10. Sign Out

### 10.1 Sign Out Flow

```
┌──────────────────────────────────────────┐
│                                          │
│         Sign out of Hoof Direct?         │
│                                          │
│    Your data will remain on this         │
│    device until you sign back in         │
│    with a different account.             │
│                                          │
│    ┌────────────┐  ┌────────────┐       │
│    │   Cancel   │  │  Sign Out  │       │
│    └────────────┘  └────────────┘       │
│                                          │
└──────────────────────────────────────────┘
```

### 10.2 Sign Out Behavior

| Action | Behavior |
|--------|----------|
| Local data | Retained until different user signs in |
| Pending syncs | Remain queued, sync on re-login |
| Preferences | Cleared from DataStore |
| Navigation | Return to sign-in screen |
| Push token | Unregistered from server |

---

## 11. Acceptance Criteria

### 11.1 Profile Settings
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-01 | Business name updates and persists after app restart | Integration test |
| AC-019-02 | Phone number formats automatically (e.g., "5551234567" → "(555) 123-4567") | Unit test |
| AC-019-03 | Profile photo uploads and displays within 5 seconds | Manual test |
| AC-019-04 | Service radius slider ranges from 1 to 500 miles | UI test |
| AC-019-05 | Home address geocodes and saves coordinates | Integration test |
| AC-019-06 | Email field is read-only and cannot be edited | UI test |

### 11.2 Scheduling Defaults
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-07 | Default duration changes reflect in new appointment creation | Integration test |
| AC-019-08 | Default cycle changes reflect in new horse creation | Integration test |
| AC-019-09 | Duration picker shows options: 15, 30, 45, 60, 90, 120 minutes | UI test |
| AC-019-10 | Cycle picker shows options: 4, 5, 6, 7, 8 weeks | UI test |
| AC-019-11 | Default reminder days range from 1-30 | UI test |

### 11.3 Working Hours
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-12 | Each day can be enabled/disabled independently | UI test |
| AC-019-13 | End time must be after start time (validation error shown) | Unit test |
| AC-019-14 | "Reset to Defaults" restores Mon-Fri 8AM-5PM | UI test |
| AC-019-15 | At least one day must be enabled (prevents saving if all disabled) | Unit test |

### 11.4 Notification Settings
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-16 | Push notification toggle reflects system permission state | Integration test |
| AC-019-17 | SMS reminders toggle disabled for Free tier with upgrade prompt | UI test |
| AC-019-18 | Daily digest time picker shows 30-minute increments | UI test |
| AC-019-19 | Disabling daily digest cancels scheduled notification | Integration test |

### 11.5 Data Export
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-20 | Export generates valid JSON file | Unit test |
| AC-019-21 | Export includes all clients, horses, appointments, invoices | Integration test |
| AC-019-22 | Export file can be shared via Android share sheet | Manual test |
| AC-019-23 | Export shows accurate record counts before download | UI test |
| AC-019-24 | Export works offline using local data | Integration test |

### 11.6 Account Deletion
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-25 | Delete button disabled until "DELETE" typed exactly | UI test |
| AC-019-26 | Deletion schedules for 30 days in future | Integration test |
| AC-019-27 | Active subscription is cancelled on deletion request | Integration test |
| AC-019-28 | User is signed out after deletion request | Integration test |
| AC-019-29 | Confirmation email is sent to user | Manual test |

### 11.7 About & Support
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-30 | Version displays format "X.Y.Z (Build N)" | UI test |
| AC-019-31 | Privacy Policy opens https://hoofdirect.com/privacy in browser | Manual test |
| AC-019-32 | Terms of Service opens https://hoofdirect.com/terms in browser | Manual test |
| AC-019-33 | Contact Support opens email client with pre-filled device info | Manual test |
| AC-019-34 | Send Feedback opens Canny in browser | Manual test |

### 11.8 Sign Out
| ID | Criteria | Test Method |
|----|----------|-------------|
| AC-019-35 | Sign out shows confirmation dialog | UI test |
| AC-019-36 | Sign out clears preferences but retains local data | Integration test |
| AC-019-37 | Sign out navigates to sign-in screen | UI test |
| AC-019-38 | Push token is unregistered on sign out | Integration test |

---

## 12. File References

### 12.1 Feature Module Structure
```
feature/settings/
├── ui/
│   ├── SettingsScreen.kt
│   ├── ProfileScreen.kt
│   ├── WorkingHoursScreen.kt
│   ├── ExportDataScreen.kt
│   ├── DeleteAccountScreen.kt
│   └── components/
│       ├── SettingsSection.kt
│       ├── SettingsClickableItem.kt
│       ├── SettingsSwitchItem.kt
│       └── SignOutDialog.kt
├── viewmodel/
│   ├── SettingsViewModel.kt
│   ├── ProfileViewModel.kt
│   └── ExportViewModel.kt
└── navigation/
    └── SettingsNavigation.kt
```

### 12.2 Core Module Files
```
core/
├── data/
│   ├── preferences/
│   │   ├── PreferenceKeys.kt
│   │   └── UserPreferencesManager.kt
│   ├── repository/
│   │   ├── ProfileRepository.kt
│   │   ├── AccountRepository.kt
│   │   └── ExportRepository.kt
│   └── export/
│       ├── DataExporter.kt
│       └── ExportedData.kt
└── domain/
    └── model/
        ├── UserProfile.kt
        ├── UserPreferences.kt
        └── WorkingHours.kt
```

---

## 13. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Settings screen load | < 200ms | Time to interactive |
| Preference save | < 100ms | DataStore write complete |
| Profile update | < 2s | Server response |
| Photo upload | < 5s | Including compression |
| Data export generation | < 10s | For up to 1000 records |
| Export file write | < 2s | JSON serialization |

---

## 14. Error Handling

| Scenario | User Message | Action |
|----------|--------------|--------|
| Profile update fails | "Couldn't save changes. Please try again." | Retry button |
| Photo upload fails | "Photo upload failed. Check your connection." | Retry option |
| Export fails | "Export failed. Please try again." | Retry button |
| Address geocode fails | "Couldn't find address. Please check and try again." | Manual coordinate entry option |
| Deletion request fails | "Couldn't process request. Please contact support." | Support link |
