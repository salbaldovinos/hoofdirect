# FRD-007: Device Calendar Sync

**Source PRD**: PRD-007-calendar-sync.md  
**Priority**: P1  
**Phase**: 2 - Scheduling Core  
**Status**: Draft  
**Last Updated**: 2025-01-13

---

## 1. Overview

### 1.1 Purpose

Enable two-way synchronization between Hoof Direct appointments and device calendars (Google Calendar, Samsung Calendar, etc.) to prevent double-booking and share schedules with family members.

### 1.2 Scope

This document specifies:
- Export: Creating/updating appointments as device calendar events
- Import: Reading external calendar events as blocked time
- Hoof Direct calendar creation and management
- Deep linking from calendar events to app
- Sync settings and calendar selection
- Background sync scheduling
- Permission handling

### 1.3 Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| FRD-005 | Required | Appointment data source |
| FRD-006 | Required | Day view blocked time display |
| FRD-019 | Required | Settings screen integration |
| Android CalendarContract | System | Calendar access API |
| WorkManager | Library | Background sync scheduling |

---

## 2. Android Permissions

### 2.1 Required Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

### 2.2 Permission Request Flow

```kotlin
// CalendarPermissionHandler.kt
class CalendarPermissionHandler @Inject constructor(
    private val context: Context
) {
    sealed class PermissionState {
        object NotRequested : PermissionState()
        object Granted : PermissionState()
        object Denied : PermissionState()
        object PermanentlyDenied : PermissionState()
    }
    
    fun checkPermissions(): PermissionState {
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        val writeGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        return when {
            readGranted && writeGranted -> PermissionState.Granted
            else -> PermissionState.NotRequested
        }
    }
    
    fun shouldShowRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.READ_CALENDAR
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.WRITE_CALENDAR
        )
    }
}
```

### 2.3 Permission Request UI

```
┌─────────────────────────────────────────────┐
│                                             │
│              📅                             │
│                                             │
│       Calendar Access Required              │
│                                             │
│   Hoof Direct needs access to your          │
│   calendar to:                              │
│                                             │
│   • Add appointments to your calendar       │
│   • Show your busy times when scheduling    │
│   • Prevent double-booking                  │
│                                             │
│          [Allow Calendar Access]            │
│                                             │
│              [Not Now]                      │
│                                             │
└─────────────────────────────────────────────┘
```

### 2.4 Permission Denied Handling

**If denied once:**
- Show rationale and retry option

**If permanently denied:**
```
┌─────────────────────────────────────────────┐
│                                             │
│   Calendar sync requires permission         │
│                                             │
│   To enable calendar sync, please grant     │
│   calendar access in your device settings.  │
│                                             │
│           [Open Settings]                   │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 3. Hoof Direct Calendar Management

### 3.1 Calendar Creation

```kotlin
// HoofDirectCalendarManager.kt
@Singleton
class HoofDirectCalendarManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver = context.contentResolver
    
    companion object {
        const val CALENDAR_ACCOUNT_NAME = "Hoof Direct"
        const val CALENDAR_ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
        const val CALENDAR_DISPLAY_NAME = "Hoof Direct"
        const val CALENDAR_COLOR = 0xFF2E7D32.toInt() // Green 700
    }
    
    suspend fun getOrCreateCalendar(): Long? = withContext(Dispatchers.IO) {
        // Check for existing calendar
        val existingId = findExistingCalendar()
        if (existingId != null) return@withContext existingId
        
        // Create new calendar
        createCalendar()
    }
    
    private fun findExistingCalendar(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
                "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        
        val selectionArgs = arrayOf(CALENDAR_ACCOUNT_NAME, CALENDAR_ACCOUNT_TYPE)
        
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }
    
    private fun createCalendar(): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()
        
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, 
                CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        
        return try {
            val resultUri = contentResolver.insert(uri, values)
            resultUri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            Log.e("CalendarManager", "Failed to create calendar", e)
            null
        }
    }
    
    suspend fun deleteCalendar() = withContext(Dispatchers.IO) {
        val calendarId = findExistingCalendar() ?: return@withContext
        
        val uri = ContentUris.withAppendedId(
            CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                .build(),
            calendarId
        )
        
        contentResolver.delete(uri, null, null)
    }
}
```

---

## 4. Export to Device Calendar

### 4.1 Calendar Event Export

```kotlin
// CalendarExportManager.kt
@Singleton
class CalendarExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarManager: HoofDirectCalendarManager,
    private val appointmentDao: AppointmentDao,
    private val clientDao: ClientDao
) {
    private val contentResolver = context.contentResolver
    
    suspend fun exportAppointment(appointment: AppointmentWithDetails): Result<String> {
        return try {
            val calendarId = calendarManager.getOrCreateCalendar()
                ?: return Result.failure(CalendarError.CalendarNotAvailable)
            
            val eventId = if (appointment.appointment.calendarEventId != null) {
                // Update existing event
                updateEvent(appointment)
                appointment.appointment.calendarEventId
            } else {
                // Create new event
                createEvent(calendarId, appointment)
            }
            
            Result.success(eventId!!)
        } catch (e: Exception) {
            Result.failure(CalendarError.ExportFailed(e))
        }
    }
    
    private suspend fun createEvent(
        calendarId: Long,
        appointment: AppointmentWithDetails
    ): String = withContext(Dispatchers.IO) {
        val startDateTime = appointment.appointment.date
            .atTime(appointment.appointment.time)
            .atZone(ZoneId.systemDefault())
        
        val endDateTime = startDateTime
            .plusMinutes(appointment.appointment.durationMinutes.toLong())
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, buildEventTitle(appointment))
            put(CalendarContract.Events.DESCRIPTION, buildEventDescription(appointment))
            put(CalendarContract.Events.EVENT_LOCATION, buildEventLocation(appointment))
            put(CalendarContract.Events.DTSTART, startDateTime.toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, endDateTime.toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = ContentUris.parseId(uri!!).toString()
        
        // Save event ID to appointment
        appointmentDao.updateCalendarEventId(
            appointment.appointment.id,
            eventId
        )
        
        // Add reminder
        addReminder(eventId.toLong(), appointment)
        
        eventId
    }
    
    private suspend fun updateEvent(
        appointment: AppointmentWithDetails
    ) = withContext(Dispatchers.IO) {
        val eventId = appointment.appointment.calendarEventId?.toLong() ?: return@withContext
        
        val startDateTime = appointment.appointment.date
            .atTime(appointment.appointment.time)
            .atZone(ZoneId.systemDefault())
        
        val endDateTime = startDateTime
            .plusMinutes(appointment.appointment.durationMinutes.toLong())
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, buildEventTitle(appointment))
            put(CalendarContract.Events.DESCRIPTION, buildEventDescription(appointment))
            put(CalendarContract.Events.EVENT_LOCATION, buildEventLocation(appointment))
            put(CalendarContract.Events.DTSTART, startDateTime.toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, endDateTime.toInstant().toEpochMilli())
        }
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        contentResolver.update(uri, values, null, null)
    }
    
    suspend fun deleteEvent(appointmentId: String) = withContext(Dispatchers.IO) {
        val appointment = appointmentDao.getById(appointmentId) ?: return@withContext
        val eventId = appointment.calendarEventId?.toLong() ?: return@withContext
        
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        contentResolver.delete(uri, null, null)
        
        appointmentDao.updateCalendarEventId(appointmentId, null)
    }
    
    private fun addReminder(eventId: Long, appointment: AppointmentWithDetails) {
        val reminderMinutes = getReminderMinutes(appointment)
        
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, reminderMinutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        
        contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }
    
    private fun getReminderMinutes(appointment: AppointmentWithDetails): Int {
        // Use client's reminder preference, default to 24 hours
        val hours = appointment.client.reminderHours ?: 24
        return hours * 60
    }
}
```

### 4.2 Event Content Building

```kotlin
// CalendarEventBuilder.kt
object CalendarEventBuilder {
    
    fun buildEventTitle(appointment: AppointmentWithDetails): String {
        // Use client display name
        return appointment.client.displayName
    }
    
    fun buildEventLocation(appointment: AppointmentWithDetails): String {
        // Use location override if set, otherwise client address
        return appointment.appointment.locationOverride 
            ?: appointment.client.fullAddress
    }
    
    fun buildEventDescription(
        appointment: AppointmentWithDetails,
        includePrice: Boolean = true,
        includeNotes: Boolean = true
    ): String {
        return buildString {
            // Horses and services
            appendLine("Horses:")
            appointment.horses.forEach { horse ->
                appendLine("  • ${horse.horse.name} (${horse.appointmentHorse.serviceType.displayName})")
            }
            
            appendLine()
            
            // Services summary
            val serviceGroups = appointment.horses
                .groupingBy { it.appointmentHorse.serviceType }
                .eachCount()
            appendLine("Services: ${serviceGroups.entries.joinToString(", ") { 
                "${it.value} ${it.key.displayName}" 
            }}")
            
            // Price (optional based on settings)
            if (includePrice) {
                val total = appointment.horses.sumOf { it.appointmentHorse.price }
                appendLine("Total: ${formatCurrency(total)}")
            }
            
            // Notes (optional based on settings)
            if (includeNotes && !appointment.appointment.notes.isNullOrBlank()) {
                appendLine()
                appendLine("Notes: ${appointment.appointment.notes}")
            }
            
            // Access notes
            if (!appointment.client.accessNotes.isNullOrBlank()) {
                appendLine("Access: ${appointment.client.accessNotes}")
            }
            
            // Deep link
            appendLine()
            appendLine("─────────────────────")
            appendLine("Open in Hoof Direct:")
            appendLine("hoofdirect://appointment/${appointment.appointment.id}")
        }
    }
}
```

### 4.3 Export Triggers

Export is triggered in the following scenarios:

| Trigger | Sync Type | Delay |
|---------|-----------|-------|
| Appointment created | Immediate | None |
| Appointment updated | Immediate | None |
| Appointment completed | Immediate | None |
| Appointment cancelled | Delete event | None |
| App foreground | Batch sync | 5 seconds |
| Background worker | Batch sync | Every 15 min |

```kotlin
// AppointmentRepository.kt (extended)
class AppointmentRepository @Inject constructor(
    // ... existing dependencies
    private val calendarExportManager: CalendarExportManager,
    private val calendarSyncSettings: CalendarSyncSettings
) {
    @Transaction
    suspend fun create(
        appointment: AppointmentEntity,
        horses: List<AppointmentHorseEntity>
    ): Result<Appointment> {
        // ... existing create logic
        
        // Export to calendar if enabled
        if (calendarSyncSettings.exportEnabled) {
            val fullAppointment = getWithDetails(appointment.id)
            fullAppointment?.let { calendarExportManager.exportAppointment(it) }
        }
        
        return result
    }
    
    suspend fun updateStatus(
        appointmentId: String,
        status: AppointmentStatus
    ) {
        // ... existing status update
        
        // Handle calendar sync
        when (status) {
            AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW -> {
                if (calendarSyncSettings.exportEnabled) {
                    calendarExportManager.deleteEvent(appointmentId)
                }
            }
            else -> {
                if (calendarSyncSettings.exportEnabled) {
                    val fullAppointment = getWithDetails(appointmentId)
                    fullAppointment?.let { calendarExportManager.exportAppointment(it) }
                }
            }
        }
    }
}
```

---

## 5. Import from Device Calendar

### 5.1 Blocked Time Import

```kotlin
// CalendarImportManager.kt
@Singleton
class CalendarImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarManager: HoofDirectCalendarManager,
    private val calendarSyncSettings: CalendarSyncSettings
) {
    private val contentResolver = context.contentResolver
    
    suspend fun importBlockedTimes(date: LocalDate): List<BlockedTime> = withContext(Dispatchers.IO) {
        if (!calendarSyncSettings.importEnabled) return@withContext emptyList()
        
        val selectedCalendarIds = calendarSyncSettings.selectedImportCalendars
        if (selectedCalendarIds.isEmpty()) return@withContext emptyList()
        
        val hoofDirectCalendarId = calendarManager.getOrCreateCalendar()
        
        val startOfDay = date.atStartOfDay()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        
        val endOfDay = date.plusDays(1)
            .atStartOfDay()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        
        val blockedTimes = mutableListOf<BlockedTime>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.AVAILABILITY
        )
        
        // Build selection excluding Hoof Direct calendar
        val calendarIds = selectedCalendarIds
            .filter { it != hoofDirectCalendarId }
            .joinToString(",")
        
        if (calendarIds.isEmpty()) return@withContext emptyList()
        
        val selection = """
            ${CalendarContract.Events.DTSTART} < ? AND 
            ${CalendarContract.Events.DTEND} > ? AND 
            ${CalendarContract.Events.CALENDAR_ID} IN ($calendarIds) AND
            ${CalendarContract.Events.DELETED} = 0
        """.trimIndent()
        
        val selectionArgs = arrayOf(
            endOfDay.toString(),
            startOfDay.toString()
        )
        
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val calendarNameCache = mutableMapOf<Long, String>()
            
            while (cursor.moveToNext()) {
                val isAllDay = cursor.getInt(5) == 1
                if (isAllDay) continue // Skip all-day events
                
                val availability = cursor.getInt(6)
                // Skip FREE events (AVAILABILITY_FREE = 0)
                if (availability == CalendarContract.Events.AVAILABILITY_FREE) continue
                
                val title = cursor.getString(1) ?: "Busy"
                val eventStart = Instant.ofEpochMilli(cursor.getLong(2))
                val eventEnd = Instant.ofEpochMilli(cursor.getLong(3))
                val calendarId = cursor.getLong(4)
                
                // Clip to day boundaries
                val dayStart = date.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
                val dayEnd = date.plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
                
                val clippedStart = if (eventStart.isBefore(dayStart)) dayStart else eventStart
                val clippedEnd = if (eventEnd.isAfter(dayEnd)) dayEnd else eventEnd
                
                val startTime = clippedStart.atZone(ZoneId.systemDefault()).toLocalTime()
                val endTime = clippedEnd.atZone(ZoneId.systemDefault()).toLocalTime()
                
                // Get calendar name (cached)
                val calendarName = calendarNameCache.getOrPut(calendarId) {
                    getCalendarName(calendarId) ?: "Unknown"
                }
                
                blockedTimes.add(
                    BlockedTime(
                        id = cursor.getString(0),
                        title = title,
                        startTime = startTime,
                        endTime = endTime,
                        source = calendarName,
                        isExternal = true
                    )
                )
            }
        }
        
        // Merge overlapping blocked times
        mergeOverlappingBlocks(blockedTimes)
    }
    
    private fun getCalendarName(calendarId: Long): String? {
        val projection = arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }
    
    private fun mergeOverlappingBlocks(blocks: MutableList<BlockedTime>): List<BlockedTime> {
        if (blocks.size <= 1) return blocks
        
        blocks.sortBy { it.startTime }
        
        val merged = mutableListOf<BlockedTime>()
        var current = blocks[0]
        
        for (i in 1 until blocks.size) {
            val next = blocks[i]
            if (current.endTime >= next.startTime) {
                // Overlap - extend current
                current = current.copy(
                    endTime = maxOf(current.endTime, next.endTime),
                    title = if (current.source == next.source) current.title else "Busy"
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return merged
    }
}
```

### 5.2 Available Calendars List

```kotlin
// DeviceCalendarRepository.kt
@Singleton
class DeviceCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarManager: HoofDirectCalendarManager
) {
    private val contentResolver = context.contentResolver
    
    suspend fun getAvailableCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val hoofDirectCalendarId = calendarManager.getOrCreateCalendar()
        val calendars = mutableListOf<DeviceCalendar>()
        
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )
        
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        
        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                
                // Exclude Hoof Direct calendar
                if (id == hoofDirectCalendarId) continue
                
                calendars.add(
                    DeviceCalendar(
                        id = id,
                        displayName = cursor.getString(1) ?: "Unknown",
                        accountName = cursor.getString(2) ?: "",
                        color = cursor.getInt(3),
                        isVisible = cursor.getInt(4) == 1
                    )
                )
            }
        }
        
        calendars
    }
}

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isVisible: Boolean,
    val isSelected: Boolean = false
)
```

---

## 6. Calendar Sync Settings

### 6.1 Settings Data Model

```kotlin
// CalendarSyncSettings.kt
@Singleton
class CalendarSyncSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val EXPORT_ENABLED = booleanPreferencesKey("calendar_export_enabled")
        val IMPORT_ENABLED = booleanPreferencesKey("calendar_import_enabled")
        val SELECTED_CALENDARS = stringSetPreferencesKey("calendar_import_selected")
        val INCLUDE_PRICE_IN_EXPORT = booleanPreferencesKey("calendar_include_price")
        val INCLUDE_NOTES_IN_EXPORT = booleanPreferencesKey("calendar_include_notes")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("calendar_last_sync")
    }
    
    val exportEnabled: Flow<Boolean> = dataStore.data.map { 
        it[Keys.EXPORT_ENABLED] ?: true 
    }
    
    val importEnabled: Flow<Boolean> = dataStore.data.map { 
        it[Keys.IMPORT_ENABLED] ?: true 
    }
    
    val selectedImportCalendars: Flow<Set<Long>> = dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_CALENDARS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }
    
    val includePriceInExport: Flow<Boolean> = dataStore.data.map { 
        it[Keys.INCLUDE_PRICE_IN_EXPORT] ?: true 
    }
    
    val includeNotesInExport: Flow<Boolean> = dataStore.data.map { 
        it[Keys.INCLUDE_NOTES_IN_EXPORT] ?: true 
    }
    
    val lastSyncTimestamp: Flow<Instant?> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_TIMESTAMP]?.let { Instant.ofEpochMilli(it) }
    }
    
    suspend fun setExportEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EXPORT_ENABLED] = enabled }
    }
    
    suspend fun setImportEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.IMPORT_ENABLED] = enabled }
    }
    
    suspend fun setSelectedImportCalendars(calendarIds: Set<Long>) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CALENDARS] = calendarIds.map { it.toString() }.toSet()
        }
    }
    
    suspend fun setIncludePriceInExport(include: Boolean) {
        dataStore.edit { it[Keys.INCLUDE_PRICE_IN_EXPORT] = include }
    }
    
    suspend fun setIncludeNotesInExport(include: Boolean) {
        dataStore.edit { it[Keys.INCLUDE_NOTES_IN_EXPORT] = include }
    }
    
    suspend fun updateLastSyncTimestamp() {
        dataStore.edit { it[Keys.LAST_SYNC_TIMESTAMP] = Instant.now().toEpochMilli() }
    }
}
```

### 6.2 Settings Screen

```
┌─────────────────────────────────────────────┐
│ [←]     Calendar Sync                       │
├─────────────────────────────────────────────┤
│                                             │
│  EXPORT                                     │
│  ─────────────────────────────────────────  │
│                                             │
│  Sync to Device Calendar                    │
│  Add appointments to your calendar      🔘  │
│                                             │
│  Calendar: Hoof Direct                  ✓   │
│                                             │
│  Include in events:                         │
│  ┌─────────────────────────────────────┐    │
│  │ ☑ Total price                       │    │
│  │ ☑ Notes and access instructions     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  IMPORT                                     │
│  ─────────────────────────────────────────  │
│                                             │
│  Show External Events                       │
│  View busy times when scheduling        🔘  │
│                                             │
│  Calendars to Import                    >   │
│  Personal, Family (2 selected)              │
│                                             │
│  ─────────────────────────────────────────  │
│                                             │
│  SYNC STATUS                                │
│  ─────────────────────────────────────────  │
│                                             │
│  Last synced: 5 minutes ago                 │
│                                             │
│          [↻ Sync Now]                       │
│                                             │
└─────────────────────────────────────────────┘
```

### 6.3 Calendar Selection Sheet

```
┌─────────────────────────────────────────────┐
│  Select Calendars                       [×] │
├─────────────────────────────────────────────┤
│                                             │
│  Choose which calendars to show as          │
│  blocked time when scheduling.              │
│                                             │
│  ─────────────────────────────────────────  │
│                                             │
│  Google Account                             │
│  ┌─────────────────────────────────────┐    │
│  │ 🔵 ☑ Personal                       │    │
│  │    john@gmail.com                   │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │ 🟢 ☑ Family                         │    │
│  │    john@gmail.com                   │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │ 🟡 ☐ Birthdays                      │    │
│  │    john@gmail.com                   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Work Account                               │
│  ┌─────────────────────────────────────┐    │
│  │ 🔴 ☐ Work                           │    │
│  │    john@company.com                 │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Samsung Account                            │
│  ┌─────────────────────────────────────┐    │
│  │ 🟣 ☐ My Calendar                    │    │
│  │    samsung@samsung.com              │    │
│  └─────────────────────────────────────┘    │
│                                             │
│          [Done]                             │
│                                             │
└─────────────────────────────────────────────┘
```

### 6.4 Settings ViewModel

```kotlin
// CalendarSyncSettingsViewModel.kt
@HiltViewModel
class CalendarSyncSettingsViewModel @Inject constructor(
    private val calendarSyncSettings: CalendarSyncSettings,
    private val deviceCalendarRepository: DeviceCalendarRepository,
    private val calendarSyncManager: CalendarSyncManager
) : ViewModel() {
    
    val exportEnabled = calendarSyncSettings.exportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val importEnabled = calendarSyncSettings.importEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val includePriceInExport = calendarSyncSettings.includePriceInExport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val includeNotesInExport = calendarSyncSettings.includeNotesInExport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val lastSyncTimestamp = calendarSyncSettings.lastSyncTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    private val _availableCalendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val availableCalendars: StateFlow<List<DeviceCalendar>> = _availableCalendars.asStateFlow()
    
    private val _selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    
    val calendarsWithSelection: StateFlow<List<DeviceCalendar>> = combine(
        _availableCalendars,
        _selectedCalendarIds
    ) { calendars, selected ->
        calendars.map { it.copy(isSelected = it.id in selected) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    init {
        loadCalendars()
        loadSelectedCalendars()
    }
    
    private fun loadCalendars() {
        viewModelScope.launch {
            try {
                _availableCalendars.value = deviceCalendarRepository.getAvailableCalendars()
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }
    
    private fun loadSelectedCalendars() {
        viewModelScope.launch {
            calendarSyncSettings.selectedImportCalendars.collect {
                _selectedCalendarIds.value = it
            }
        }
    }
    
    fun setExportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            calendarSyncSettings.setExportEnabled(enabled)
        }
    }
    
    fun setImportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            calendarSyncSettings.setImportEnabled(enabled)
        }
    }
    
    fun toggleCalendarSelection(calendarId: Long) {
        viewModelScope.launch {
            val current = _selectedCalendarIds.value
            val updated = if (calendarId in current) {
                current - calendarId
            } else {
                current + calendarId
            }
            _selectedCalendarIds.value = updated
            calendarSyncSettings.setSelectedImportCalendars(updated)
        }
    }
    
    fun syncNow() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                calendarSyncManager.performFullSync()
                _syncState.value = SyncState.Success
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }
    
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }
}
```

---

## 7. Background Sync Worker

### 7.1 Worker Implementation

```kotlin
// CalendarSyncWorker.kt
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val calendarExportManager: CalendarExportManager,
    private val appointmentDao: AppointmentDao,
    private val calendarSyncSettings: CalendarSyncSettings
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Check if export is enabled
            val exportEnabled = calendarSyncSettings.exportEnabled.first()
            if (!exportEnabled) return Result.success()
            
            // Get appointments needing sync
            val unsyncedAppointments = appointmentDao.getAppointmentsNeedingCalendarSync()
            
            // Sync each appointment
            unsyncedAppointments.forEach { appointment ->
                try {
                    when (appointment.appointment.status) {
                        AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW -> {
                            calendarExportManager.deleteEvent(appointment.appointment.id)
                        }
                        else -> {
                            calendarExportManager.exportAppointment(appointment)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CalendarSync", "Failed to sync ${appointment.appointment.id}", e)
                }
            }
            
            // Update last sync timestamp
            calendarSyncSettings.updateLastSyncTimestamp()
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    companion object {
        const val WORK_NAME = "calendar_sync"
        
        fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            
            return PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .build()
        }
        
        fun buildOneTimeWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .build()
        }
    }
}
```

### 7.2 Worker Scheduling

```kotlin
// CalendarSyncScheduler.kt
@Singleton
class CalendarSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    
    fun schedulePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            CalendarSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            CalendarSyncWorker.buildPeriodicWorkRequest()
        )
    }
    
    fun triggerImmediateSync() {
        workManager.enqueue(CalendarSyncWorker.buildOneTimeWorkRequest())
    }
    
    fun cancelSync() {
        workManager.cancelUniqueWork(CalendarSyncWorker.WORK_NAME)
    }
}
```

---

## 8. Deep Link Handling

### 8.1 Calendar Event Deep Link

The deep link `hoofdirect://appointment/{id}` is included in calendar event descriptions.

```kotlin
// DeepLinkHandler.kt (extended)
object DeepLinkHandler {
    fun handleDeepLink(uri: Uri): DeepLinkDestination? {
        return when {
            uri.host == "appointment" && uri.pathSegments.size == 1 -> {
                DeepLinkDestination.AppointmentDetail(uri.pathSegments[0])
            }
            // ... other deep links
            else -> null
        }
    }
}

sealed class DeepLinkDestination {
    data class AppointmentDetail(val appointmentId: String) : DeepLinkDestination()
}
```

### 8.2 Intent Filter

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="hoofdirect"
            android:host="appointment" />
    </intent-filter>
</activity>
```

---

## 9. Data Models

### 9.1 BlockedTime

```kotlin
// core/model/BlockedTime.kt
data class BlockedTime(
    val id: String,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val source: String, // Calendar name
    val isExternal: Boolean = true, // From external calendar vs created in app
    val color: Int? = null
)
```

### 9.2 CalendarEvent (Internal)

```kotlin
// Internal representation for tracking sync status
data class CalendarEventSyncInfo(
    val appointmentId: String,
    val calendarEventId: String?,
    val lastSyncedAt: Instant?,
    val needsSync: Boolean
)
```

---

## 10. Error Handling

### 10.1 Error Types

```kotlin
// CalendarError.kt
sealed class CalendarError : Exception() {
    object PermissionNotGranted : CalendarError()
    object CalendarNotAvailable : CalendarError()
    data class ExportFailed(override val cause: Throwable?) : CalendarError()
    data class ImportFailed(override val cause: Throwable?) : CalendarError()
    object EventNotFound : CalendarError()
}
```

### 10.2 Error Messages

| Error | User Message |
|-------|--------------|
| PermissionNotGranted | "Calendar access required. Please grant permission in settings." |
| CalendarNotAvailable | "Unable to access device calendar." |
| ExportFailed | "Failed to add appointment to calendar. Will retry automatically." |
| ImportFailed | "Failed to load external calendar events." |
| EventNotFound | "Calendar event no longer exists." |

---

## 11. Acceptance Criteria

### 11.1 Export

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-007-01 | Export on create | Export enabled, new appointment | Appointment saved | Calendar event created with correct details |
| AC-007-02 | Event title | Appointment for Johnson Ranch | View calendar | Event title is "Johnson Ranch" |
| AC-007-03 | Event location | Client address set | View calendar | Event location shows full address |
| AC-007-04 | Event description | 2 horses, notes | View calendar | Description shows horses, services, total, notes |
| AC-007-05 | Event timing | 9 AM, 90 min duration | View calendar | Event spans 9:00 AM - 10:30 AM |
| AC-007-06 | Update on reschedule | Existing event, reschedule to new time | Save changes | Calendar event updated |
| AC-007-07 | Delete on cancel | Existing event | Cancel appointment | Calendar event deleted |
| AC-007-08 | Deep link works | Event with deep link | Tap link in description | Opens appointment in app |

### 11.2 Import

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-007-09 | Import blocked times | Import enabled, external events exist | View Day view | Blocked times shown as gray blocks |
| AC-007-10 | Exclude Hoof Direct | Hoof Direct and external events | Import blocked times | Only external events imported |
| AC-007-11 | Merge overlapping | Two overlapping events | Import | Single merged block shown |
| AC-007-12 | Skip all-day events | All-day and timed events | Import | Only timed events shown |
| AC-007-13 | Calendar selection | 3 calendars available | Select 2 | Only events from 2 calendars imported |

### 11.3 Settings

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-007-14 | Disable export | Export enabled | Toggle off | No new events created |
| AC-007-15 | Disable import | Import enabled | Toggle off | No blocked times shown |
| AC-007-16 | Select calendars | Available calendars | Toggle selections | Changes saved immediately |
| AC-007-17 | Manual sync | Sync button | Tap Sync Now | Sync runs, timestamp updates |
| AC-007-18 | Sync status | Last sync 5 min ago | View settings | Shows "Last synced: 5 minutes ago" |

### 11.4 Permissions

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-007-19 | Request permission | Not granted | Enable sync | Permission dialog shown with rationale |
| AC-007-20 | Permission denied | Deny once | Retry | Rationale shown, can retry |
| AC-007-21 | Permanently denied | "Don't ask again" | Enable sync | Directed to Settings |

---

## 12. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Export single appointment | < 500ms | Including DB update |
| Import blocked times (day) | < 1s | 20 external events |
| Calendar list load | < 500ms | 10 calendars |
| Background sync | < 30s | 50 appointments |
| Permission check | < 50ms | Cold start |

---

## 13. File References

### 13.1 Core Calendar

| File | Purpose |
|------|---------|
| `core/calendar/CalendarPermissionHandler.kt` | Permission management |
| `core/calendar/HoofDirectCalendarManager.kt` | HD calendar CRUD |
| `core/calendar/CalendarExportManager.kt` | Export to calendar |
| `core/calendar/CalendarImportManager.kt` | Import blocked times |
| `core/calendar/CalendarEventBuilder.kt` | Event content building |
| `core/calendar/DeviceCalendarRepository.kt` | List available calendars |
| `core/calendar/CalendarSyncSettings.kt` | Settings data store |
| `core/calendar/CalendarSyncScheduler.kt` | WorkManager scheduling |

### 13.2 Workers

| File | Purpose |
|------|---------|
| `core/worker/CalendarSyncWorker.kt` | Background sync |

### 13.3 UI

| File | Purpose |
|------|---------|
| `feature/settings/ui/CalendarSyncSettingsScreen.kt` | Settings screen |
| `feature/settings/ui/CalendarSyncSettingsViewModel.kt` | Settings state |
| `feature/settings/ui/CalendarSelectionSheet.kt` | Calendar picker |

---

## 14. Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Calendar permission handling
- [ ] HoofDirectCalendarManager
- [ ] CalendarSyncSettings data store

### Phase 2: Export
- [ ] CalendarExportManager
- [ ] CalendarEventBuilder
- [ ] Export trigger integration in AppointmentRepository
- [ ] CalendarSyncWorker

### Phase 3: Import
- [ ] CalendarImportManager
- [ ] DeviceCalendarRepository
- [ ] BlockedTime model
- [ ] Day view integration

### Phase 4: Settings UI
- [ ] CalendarSyncSettingsScreen
- [ ] CalendarSelectionSheet
- [ ] Permission request UI
- [ ] Sync status display

### Phase 5: Polish
- [ ] Deep link handling
- [ ] Error handling
- [ ] Background sync scheduling
- [ ] Last sync timestamp display
