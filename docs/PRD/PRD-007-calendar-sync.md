# PRD-007: Device Calendar Sync

**Priority**: P1  
**Phase**: 2 - Scheduling Core  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Enable two-way calendar synchronization between Hoof Direct and device calendars (Google Calendar, Samsung Calendar, etc.) to prevent double-booking and integrate with family/personal schedules.

### Business Value
- Prevents double-booking with personal events
- Family members see farrier's schedule
- Appointments accessible from any calendar app
- Professional appearance with calendar integration

### Success Metrics
| Metric | Target |
|--------|--------|
| Sync latency | < 60 seconds |
| Export accuracy | 100% |
| Import completeness | 100% |
| User satisfaction | > 4.5/5 |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-007-01 | Farrier | Export appointments to device calendar | Family sees my schedule | P0 |
| US-007-02 | Farrier | See personal events in Hoof Direct | I don't double-book | P1 |
| US-007-03 | Farrier | Navigate to appointment from calendar | I get directions easily | P1 |
| US-007-04 | Farrier | Choose which calendars to import | I control what I see | P1 |
| US-007-05 | Farrier | Disable sync if I want | I have control | P1 |
| US-007-06 | Farrier | Changes sync automatically | I don't have to remember | P0 |

---

## Functional Requirements

### FR-007-01: Export to Device Calendar
- Create "Hoof Direct" calendar on device
- Calendar color matches app theme
- Event details:
  - Title: Client name
  - Location: Client address
  - Description: Horses, services, notes
  - Duration: Appointment duration
  - Reminder: Based on client preference
- Sync triggers:
  - Appointment create
  - Appointment update (date/time/status)
  - Appointment delete/cancel
  - App foreground
  - Background sync (WorkManager)

### FR-007-02: Import from Device Calendar
- User selects which calendars to import
- Import as "blocked time" (not full appointments)
- Show in Day view as grayed blocks
- Show in scheduling UI to prevent conflicts
- Exclude "Hoof Direct" calendar from import
- Sync on:
  - App foreground
  - Background sync (every 15 min)

### FR-007-03: Calendar Event Content
```
Title:      Johnson Ranch
Location:   1234 Ranch Road, Austin, TX 78701
Description:
  Horses: Midnight (Full Set), Dusty (Trim)
  Services: 1 Full Set, 1 Trim
  Total: $225.00
  Notes: Gate code 1234#
  
  Tap to open in Hoof Direct
Start:      Jan 20, 2024 8:00 AM
End:        Jan 20, 2024 9:30 AM
Reminder:   24 hours before
```

### FR-007-04: Deep Link from Calendar
- Description includes deep link
- `hoofdirect://appointment/{id}`
- Opens appointment detail in app
- Falls back to app store if not installed

### FR-007-05: Sync Settings
- Enable/disable export
- Enable/disable import
- Select calendars to import from
- Show sync status
- Manual sync button
- Last sync timestamp

---

## Technical Implementation

```kotlin
// CalendarSyncManager.kt
@Singleton
class CalendarSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appointmentDao: AppointmentDao,
    private val userPrefs: UserPreferencesManager
) {
    private val contentResolver = context.contentResolver
    
    suspend fun syncAppointment(appointment: Appointment) {
        if (!userPrefs.isCalendarSyncEnabled()) return
        
        val calendarId = getOrCreateHoofDirectCalendar()
        
        if (appointment.calendarEventId != null) {
            updateCalendarEvent(appointment)
        } else {
            createCalendarEvent(calendarId, appointment)
        }
    }
    
    private suspend fun getOrCreateHoofDirectCalendar(): Long {
        val existingId = findHoofDirectCalendar()
        if (existingId != null) return existingId
        
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "Hoof Direct")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Hoof Direct")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Hoof Direct")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF2E7D32.toInt()) // Green
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, 
                CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        
        val uri = contentResolver.insert(
            CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Hoof Direct")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, 
                    CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build(),
            values
        )
        return ContentUris.parseId(uri!!)
    }
    
    private suspend fun createCalendarEvent(
        calendarId: Long,
        appointment: Appointment
    ) {
        val startMillis = appointment.dateTime.toEpochMilliseconds()
        val endMillis = startMillis + (appointment.durationMinutes * 60 * 1000)
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, appointment.clientName)
            put(CalendarContract.Events.DESCRIPTION, buildEventDescription(appointment))
            put(CalendarContract.Events.EVENT_LOCATION, appointment.address)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = ContentUris.parseId(uri!!)
        
        // Save event ID to appointment
        appointmentDao.updateCalendarEventId(appointment.id, eventId.toString())
        
        // Add reminder
        addReminder(eventId, appointment.reminderHoursBefore * 60)
    }
    
    private fun buildEventDescription(appointment: Appointment): String {
        return buildString {
            appendLine("Horses: ${appointment.horseSummary}")
            appendLine("Services: ${appointment.servicesSummary}")
            appendLine("Total: ${appointment.formattedTotal}")
            if (appointment.notes.isNotEmpty()) {
                appendLine("Notes: ${appointment.notes}")
            }
            appendLine()
            appendLine("Open in Hoof Direct: hoofdirect://appointment/${appointment.id}")
        }
    }
    
    suspend fun importBlockedTimes(date: LocalDate): List<BlockedTime> {
        if (!userPrefs.isCalendarImportEnabled()) return emptyList()
        
        val selectedCalendarIds = userPrefs.getSelectedImportCalendars()
        val hoofDirectCalendarId = findHoofDirectCalendar()
        
        val startOfDay = date.atStartOfDay().toEpochMilliseconds()
        val endOfDay = date.plusDays(1).atStartOfDay().toEpochMilliseconds()
        
        val blockedTimes = mutableListOf<BlockedTime>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY
        )
        
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND " +
                "${CalendarContract.Events.DTSTART} < ? AND " +
                "${CalendarContract.Events.CALENDAR_ID} IN (${selectedCalendarIds.joinToString()}) AND " +
                "${CalendarContract.Events.CALENDAR_ID} != ?"
        
        val selectionArgs = arrayOf(
            startOfDay.toString(),
            endOfDay.toString(),
            hoofDirectCalendarId?.toString() ?: "-1"
        )
        
        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(1) ?: "Busy"
                val start = Instant.ofEpochMilli(cursor.getLong(2))
                val end = Instant.ofEpochMilli(cursor.getLong(3))
                val isAllDay = cursor.getInt(5) == 1
                
                if (!isAllDay) {
                    blockedTimes.add(BlockedTime(
                        title = title,
                        startTime = start.atZone(ZoneId.systemDefault()).toLocalTime(),
                        endTime = end.atZone(ZoneId.systemDefault()).toLocalTime(),
                        source = getCalendarName(cursor.getLong(4))
                    ))
                }
            }
        }
        
        return blockedTimes
    }
}

// CalendarSyncWorker.kt
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val calendarSyncManager: CalendarSyncManager,
    private val appointmentDao: AppointmentDao
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Sync all unsycned appointments
            val unsynced = appointmentDao.getUnsyncedAppointments()
            unsynced.forEach { appointment ->
                calendarSyncManager.syncAppointment(appointment.toDomain())
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
```

---

## Data Model

```kotlin
data class BlockedTime(
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val source: String
)

data class CalendarSyncSettings(
    val exportEnabled: Boolean = true,
    val importEnabled: Boolean = true,
    val selectedImportCalendars: List<Long> = emptyList(),
    val lastSyncTimestamp: Instant? = null
)

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isSelected: Boolean = false
)
```

---

## UI Specifications

### Calendar Sync Settings
```
┌─────────────────────────────────────────┐
│ [←]  Calendar Sync                      │
├─────────────────────────────────────────┤
│                                         │
│  Export to Calendar                     │
│  ─────────────────────────────────────  │
│  Add appointments to device calendar    │
│                               [  ON  ]  │
│                                         │
│  Calendar: Hoof Direct                  │
│  Last sync: 5 minutes ago              │
│                                         │
│  Import from Calendars                  │
│  ─────────────────────────────────────  │
│  Show external events as blocked time   │
│                               [  ON  ]  │
│                                         │
│  Calendars to Import                    │
│  ☑ Personal (Google)                   │
│  ☑ Family (Google)                     │
│  ☐ Work (Exchange)                     │
│  ☐ Holidays                            │
│                                         │
│  [Sync Now]                             │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class CalendarSyncManagerTest {
    @Test
    fun `syncAppointment creates event with correct details`() = runTest {
        calendarSyncManager.syncAppointment(testAppointment)
        
        val events = getCalendarEvents(testDate)
        assertEquals(1, events.size)
        assertEquals("Johnson Ranch", events[0].title)
    }
    
    @Test
    fun `importBlockedTimes excludes Hoof Direct calendar`() = runTest {
        val blocked = calendarSyncManager.importBlockedTimes(testDate)
        assertTrue(blocked.none { it.source == "Hoof Direct" })
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-007-01 | Appointments export to device calendar | Manual test |
| AC-007-02 | Event details correct | Integration test |
| AC-007-03 | External events show as blocked | Integration test |
| AC-007-04 | Deep link opens appointment | Manual test |
| AC-007-05 | User can select import calendars | UI test |
| AC-007-06 | Sync works in background | WorkManager test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | Required |
| Calendar permission | System | Required |
| CalendarContract API | Android | Available |

---

## Security Considerations

- Calendar permission requested with clear rationale
- User explicitly enables sync
- No sensitive data in calendar events (no pricing by default)
- Option to exclude notes from export
