# PRD-006: Calendar Views

**Priority**: P0  
**Phase**: 2 - Scheduling Core  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Provide intuitive calendar views (Agenda, Day, Week) for farriers to visualize and manage their schedule efficiently.

### Business Value
- Quick overview of daily/weekly workload
- Reduces scheduling conflicts
- Shows blocked time from external calendars
- Enables efficient day planning

### Success Metrics
| Metric | Target |
|--------|--------|
| View render time | < 100ms |
| Scroll performance | 60 FPS |
| View switch time | < 200ms |
| User preference retention | 100% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-006-01 | Farrier | See today's appointments | I know my schedule | P0 |
| US-006-02 | Farrier | Switch between views | I see schedule different ways | P0 |
| US-006-03 | Farrier | Navigate to any date | I plan ahead | P0 |
| US-006-04 | Farrier | See appointment details at glance | I prepare for visits | P0 |
| US-006-05 | Farrier | See blocked time | I know my availability | P1 |
| US-006-06 | Farrier | Jump to today quickly | I get back to current day | P0 |
| US-006-07 | Farrier | See route summary for day | I know driving time | P1 |

---

## Functional Requirements

### FR-006-01: Agenda View (Default)
- Chronological list of appointments
- Infinite scroll (past and future)
- Sticky date headers
- Appointment cards showing:
  - Time
  - Client name
  - Horse count and services
  - Status badge (color-coded)
  - Confirmation status
- Empty days collapsed or shown based on setting
- Pull-to-refresh

### FR-006-02: Day View
- Time-blocked visualization
- 30-minute slot grid
- Appointments as blocks spanning duration
- Blocked time shown (grayed)
- Current time indicator (red line)
- Left/right swipe for day navigation
- Day stats header:
  - Appointment count
  - Total horses
  - Estimated miles
  - Estimated drive time

### FR-006-03: Week View
- 7-day horizontal layout
- Each day shows:
  - Date
  - Appointment count
  - Color intensity by load
- Tap day → navigate to Day view
- Swipe for week navigation
- Current day highlighted

### FR-006-04: View Controls
- SegmentedButton: Agenda | Day | Week
- Preference persisted
- Quick date picker
- "Today" button always visible
- Smooth transitions between views

### FR-006-05: Status Color Coding
```kotlin
SCHEDULED   → Blue (Primary)
CONFIRMED   → Green (Success)
COMPLETED   → Gray
CANCELLED   → Orange (Warning)
NO_SHOW     → Red (Error)
```

---

## Technical Implementation

```kotlin
// CalendarViewModel.kt
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val routeRepository: RouteRepository,
    private val userPrefs: UserPreferencesManager
) : ViewModel() {
    
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    
    private val _viewType = MutableStateFlow(CalendarViewType.AGENDA)
    val viewType: StateFlow<CalendarViewType> = _viewType.asStateFlow()
    
    val dayAppointments: StateFlow<List<AppointmentWithDetails>> = _selectedDate
        .flatMapLatest { date ->
            appointmentRepository.getAppointmentsForDate(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    
    val dayStats: StateFlow<DayStats> = combine(
        dayAppointments,
        routeRepository.getRouteForDate(_selectedDate.value)
    ) { appointments, route ->
        DayStats(
            appointmentCount = appointments.size,
            horseCount = appointments.sumOf { it.horses.size },
            totalMiles = route?.totalDistanceMiles ?: 0.0,
            totalDriveMinutes = route?.totalDriveMinutes ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), DayStats())
    
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }
    
    fun setViewType(type: CalendarViewType) {
        _viewType.value = type
        viewModelScope.launch {
            userPrefs.setCalendarViewType(type)
        }
    }
    
    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }
}

// AgendaView.kt
@Composable
fun AgendaView(
    appointments: LazyPagingItems<AppointmentWithDetails>,
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(
            items = appointments,
            key = { it.appointment.id }
        ) { appointment ->
            if (appointment != null) {
                val showDateHeader = shouldShowDateHeader(appointment, appointments)
                if (showDateHeader) {
                    DateHeader(date = appointment.appointment.date)
                }
                AppointmentCard(
                    appointment = appointment,
                    onClick = { onAppointmentClick(appointment.appointment.id) }
                )
            }
        }
    }
}

// DayView.kt
@Composable
fun DayView(
    date: LocalDate,
    appointments: List<AppointmentWithDetails>,
    blockedTimes: List<BlockedTime>,
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val currentTimePosition = remember { calculateCurrentTimePosition() }
    
    Column(modifier = modifier) {
        DayStatsHeader(date = date, appointments = appointments)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Time slot grid
            TimeSlotGrid()
            
            // Blocked times (gray)
            blockedTimes.forEach { blocked ->
                BlockedTimeBlock(blocked)
            }
            
            // Appointments
            appointments.forEach { apt ->
                AppointmentBlock(
                    appointment = apt,
                    onClick = { onAppointmentClick(apt.appointment.id) }
                )
            }
            
            // Current time indicator
            if (date == LocalDate.now()) {
                CurrentTimeIndicator(position = currentTimePosition)
            }
        }
    }
}

// WeekView.kt
@Composable
fun WeekView(
    startDate: LocalDate,
    appointmentCounts: Map<LocalDate, Int>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        (0..6).forEach { offset ->
            val date = startDate.plusDays(offset.toLong())
            DayCell(
                date = date,
                appointmentCount = appointmentCounts[date] ?: 0,
                isToday = date == LocalDate.now(),
                onClick = { onDayClick(date) }
            )
        }
    }
}
```

---

## Data Model

```kotlin
data class DayStats(
    val appointmentCount: Int = 0,
    val horseCount: Int = 0,
    val totalMiles: Double = 0.0,
    val totalDriveMinutes: Int = 0
)

enum class CalendarViewType {
    AGENDA, DAY, WEEK
}

data class BlockedTime(
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val source: String // Calendar name
)

data class AppointmentDisplayData(
    val id: String,
    val time: LocalTime,
    val duration: Int,
    val clientName: String,
    val horseCount: Int,
    val servicesSummary: String,
    val status: AppointmentStatus,
    val isConfirmed: Boolean,
    val driveMinutesFromPrevious: Int?
)
```

---

## UI Specifications

### Day View Layout
```
┌─────────────────────────────────────────┐
│ Today                     🔍  🔄        │
│ Monday, January 13                      │
├─────────────────────────────────────────┤
│ [Agenda] [Day] [Week]                   │
├─────────────────────────────────────────┤
│ 6 appointments · 12 horses · 47 mi      │
├─────────────────────────────────────────┤
│                                         │
│ 6:00 ──────────────────────────────     │
│                                         │
│ 7:00 ──────────────────────────────     │
│                                         │
│ 8:00 ┌─────────────────────────┐        │
│      │ 🟢 Johnson Ranch        │        │
│      │ 3 horses · Full, Trim  │        │
│ 9:00 │                        │        │
│      └─────────────────────────┘        │
│      │← 15 min drive                    │
│ 10:00┌─────────────────────────┐        │
│      │ 🔵 Williams Farm        │        │
│      │ 2 horses · Trims       │        │
│ 11:00└─────────────────────────┘        │
│ ─────────── 🔴 NOW ─────────────        │
│ 12:00┌─────────────────────────┐        │
│      │ ░░ Lunch (blocked) ░░░ │        │
│ 1:00 └─────────────────────────┘        │
│                                         │
└─────────────────────────────────────────┘
```

### Week View Layout
```
┌─────────────────────────────────────────┐
│      ← Week of Jan 13 - 19, 2024 →     │
├─────────────────────────────────────────┤
│ [Agenda] [Day] [Week]                   │
├─────────────────────────────────────────┤
│                                         │
│  Mon   Tue   Wed   Thu   Fri   Sat  Sun │
│ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐┌───┐│
│ │13 │ │14 │ │15 │ │16 │ │17 │ │18 ││19 ││
│ │   │ │   │ │   │ │   │ │   │ │   ││   ││
│ │ 6 │ │ 4 │ │ 5 │ │ 7 │ │ 3 │ │ - ││ - ││
│ │███│ │██ │ │██ │ │███│ │█  │ │   ││   ││
│ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘└───┘│
│  ◉                                      │
│                                         │
│ ◉ = Today                               │
│ Bar height = relative appointment load  │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class CalendarViewModelTest {
    @Test
    fun `selectDate updates appointments flow`() = runTest {
        viewModel.selectDate(LocalDate.of(2024, 1, 15))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2024, 1, 15), viewModel.selectedDate.value)
    }
    
    @Test
    fun `dayStats calculates correctly`() = runTest {
        val stats = viewModel.dayStats.first()
        assertEquals(3, stats.appointmentCount)
        assertEquals(7, stats.horseCount)
    }
}

@Composable
fun DayViewPreview() {
    DayView(
        date = LocalDate.now(),
        appointments = previewAppointments,
        blockedTimes = previewBlockedTimes,
        onAppointmentClick = {}
    )
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-006-01 | Agenda view shows appointments chronologically | UI test |
| AC-006-02 | Day view shows time blocks | UI test |
| AC-006-03 | Week view shows appointment counts | UI test |
| AC-006-04 | View preference persists | Integration test |
| AC-006-05 | Status colors display correctly | UI test |
| AC-006-06 | Blocked time appears in Day view | Integration test |
| AC-006-07 | Navigation smooth between dates | Manual test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | Required |
| PRD-007 (Calendar Sync) | Internal | For blocked time |
| Kizitonwose Calendar | Library | Available |
