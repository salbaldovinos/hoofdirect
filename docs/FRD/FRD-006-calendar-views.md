# FRD-006: Calendar Views

**Source PRD**: PRD-006-calendar-views.md  
**Priority**: P0  
**Phase**: 2 - Scheduling Core  
**Status**: Draft  
**Last Updated**: 2025-01-13

---

## 1. Overview

### 1.1 Purpose

Provide intuitive calendar views for farriers to visualize and manage their schedule. Three views serve different planning needs: Agenda for quick overview, Day for detailed time blocking, and Week for workload planning.

### 1.2 Scope

This document specifies:
- Agenda view (chronological list)
- Day view (time-blocked visualization)
- Week view (7-day overview)
- View switching and preference persistence
- Date navigation controls
- Current time indicator
- Day statistics display
- Blocked time visualization

### 1.3 Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| FRD-005 | Required | Appointment data source |
| FRD-002 | Required | Offline data access |
| FRD-007 | Optional | External calendar blocked times |
| FRD-010 | Optional | Route data for day stats |

---

## 2. Calendar View Type

### 2.1 Enum Definition

```kotlin
// core/model/CalendarViewType.kt
enum class CalendarViewType {
    AGENDA,  // Chronological list
    DAY,     // Time-blocked single day
    WEEK     // 7-day overview
}
```

### 2.2 View Preference Persistence

```kotlin
// core/data/UserPreferencesManager.kt
class UserPreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val CALENDAR_VIEW_TYPE = stringPreferencesKey("calendar_view_type")
    }
    
    val calendarViewType: Flow<CalendarViewType> = dataStore.data.map { prefs ->
        prefs[Keys.CALENDAR_VIEW_TYPE]?.let {
            CalendarViewType.valueOf(it)
        } ?: CalendarViewType.AGENDA
    }
    
    suspend fun setCalendarViewType(type: CalendarViewType) {
        dataStore.edit { prefs ->
            prefs[Keys.CALENDAR_VIEW_TYPE] = type.name
        }
    }
}
```

---

## 3. Calendar Screen (Container)

### 3.1 Route & Navigation

- **Route**: `/calendar` (same as `/appointments`)
- **Bottom nav**: "Schedule" tab
- **Deep links**:
  - `hoofdirect://calendar` → Opens with persisted view type
  - `hoofdirect://calendar/day/{date}` → Opens day view for specific date
  - `hoofdirect://calendar/week/{date}` → Opens week view starting at date

### 3.2 Screen Structure

```
┌─────────────────────────────────────────────┐
│ [≡]           Schedule           [+] [🔍]   │
├─────────────────────────────────────────────┤
│                                             │
│     ◀  January 2025  ▶       [Today]        │
│                                             │
├─────────────────────────────────────────────┤
│  ┌─────────┬─────────┬─────────┐            │
│  │ Agenda  │   Day   │  Week   │            │
│  └─────────┴─────────┴─────────┘            │
├─────────────────────────────────────────────┤
│                                             │
│           (View Content Area)               │
│                                             │
│                                             │
└─────────────────────────────────────────────┘
```

### 3.3 Header Components

#### Navigation Header

```kotlin
// CalendarHeader.kt
@Composable
fun CalendarHeader(
    currentDate: LocalDate,
    viewType: CalendarViewType,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onTodayClick: () -> Unit,
    onDatePickerClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

- **Title**: Dynamic based on view type
  - Agenda: "January 2025" (current month)
  - Day: "Monday, January 13" (selected date)
  - Week: "Jan 13 - 19, 2025" (week range)
- **Navigation arrows**: Previous/next based on view type
  - Agenda: Previous/next month
  - Day: Previous/next day
  - Week: Previous/next week
- **Today button**: Always visible, jumps to today
- **Date picker**: Tap month/date opens Material 3 DatePicker

#### View Type Selector

```kotlin
// ViewTypeSelector.kt
@Composable
fun ViewTypeSelector(
    selectedType: CalendarViewType,
    onTypeSelected: (CalendarViewType) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedButton(
        items = CalendarViewType.values().toList(),
        selectedItem = selectedType,
        onItemSelected = onTypeSelected,
        itemLabel = { type ->
            when (type) {
                CalendarViewType.AGENDA -> "Agenda"
                CalendarViewType.DAY -> "Day"
                CalendarViewType.WEEK -> "Week"
            }
        }
    )
}
```

### 3.4 ViewModel

```kotlin
// feature/calendar/ui/CalendarViewModel.kt
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val routeRepository: RouteRepository,
    private val calendarSyncRepository: CalendarSyncRepository,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {
    
    // Selected date (single source of truth)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    
    // View type with preference loading
    private val _viewType = MutableStateFlow(CalendarViewType.AGENDA)
    val viewType: StateFlow<CalendarViewType> = _viewType.asStateFlow()
    
    init {
        // Load persisted preference
        viewModelScope.launch {
            userPreferencesManager.calendarViewType.collect { type ->
                _viewType.value = type
            }
        }
    }
    
    // Day appointments (for Day view)
    val dayAppointments: StateFlow<List<AppointmentWithDetails>> = _selectedDate
        .flatMapLatest { date ->
            appointmentRepository.getByDate(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Blocked times from external calendars
    val blockedTimes: StateFlow<List<BlockedTime>> = _selectedDate
        .flatMapLatest { date ->
            calendarSyncRepository.getBlockedTimesForDate(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Day stats
    val dayStats: StateFlow<DayStats> = combine(
        dayAppointments,
        _selectedDate.flatMapLatest { routeRepository.getRouteForDate(it) }
    ) { appointments, route ->
        DayStats(
            appointmentCount = appointments.count { 
                it.appointment.status in listOf(
                    AppointmentStatus.SCHEDULED,
                    AppointmentStatus.CONFIRMED
                )
            },
            horseCount = appointments.sumOf { it.horses.size },
            totalMiles = route?.totalDistanceMiles ?: 0.0,
            totalDriveMinutes = route?.totalDriveMinutes ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayStats())
    
    // Week data (appointment counts per day)
    val weekAppointmentCounts: StateFlow<Map<LocalDate, Int>> = _selectedDate
        .map { date ->
            val weekStart = date.with(DayOfWeek.MONDAY)
            weekStart to weekStart.plusDays(6)
        }
        .flatMapLatest { (start, end) ->
            appointmentRepository.getAppointmentCountsByDateRange(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    // Agenda appointments (paginated)
    val agendaAppointments: Flow<PagingData<AppointmentWithDetails>> = Pager(
        PagingConfig(pageSize = 20, enablePlaceholders = false)
    ) {
        AppointmentPagingSource(appointmentRepository, LocalDate.now())
    }.flow.cachedIn(viewModelScope)
    
    // Actions
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }
    
    fun setViewType(type: CalendarViewType) {
        _viewType.value = type
        viewModelScope.launch {
            userPreferencesManager.setCalendarViewType(type)
        }
    }
    
    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }
    
    fun navigatePrevious() {
        when (_viewType.value) {
            CalendarViewType.AGENDA -> {
                _selectedDate.value = _selectedDate.value.minusMonths(1)
            }
            CalendarViewType.DAY -> {
                _selectedDate.value = _selectedDate.value.minusDays(1)
            }
            CalendarViewType.WEEK -> {
                _selectedDate.value = _selectedDate.value.minusWeeks(1)
            }
        }
    }
    
    fun navigateNext() {
        when (_viewType.value) {
            CalendarViewType.AGENDA -> {
                _selectedDate.value = _selectedDate.value.plusMonths(1)
            }
            CalendarViewType.DAY -> {
                _selectedDate.value = _selectedDate.value.plusDays(1)
            }
            CalendarViewType.WEEK -> {
                _selectedDate.value = _selectedDate.value.plusWeeks(1)
            }
        }
    }
}
```

---

## 4. Agenda View

### 4.1 Purpose

Chronological list of appointments with infinite scroll, optimized for quick scanning of schedule.

### 4.2 Layout

```
┌─────────────────────────────────────────────┐
│           (pull to refresh)                 │
├─────────────────────────────────────────────┤
│                                             │
│  Today - Monday, Jan 13                     │
│  ─────────────────────────────────────────  │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 8:00 AM                  SCHEDULED  │    │
│  │ Johnson Ranch                       │    │
│  │ 🐴 Midnight, Dusty (2)              │    │
│  │ $225.00                 Austin, TX  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 10:30 AM                 CONFIRMED  │    │
│  │ Miller Farm                         │    │
│  │ 🐴 Thunder (1)                      │    │
│  │ $180.00              Round Rock, TX │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Tomorrow - Tuesday, Jan 14                 │
│  ─────────────────────────────────────────  │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 9:00 AM                  SCHEDULED  │    │
│  │ Davis Stables                       │    │
│  │ 🐴 Spirit, Luna, Apollo (3)         │    │
│  │ $495.00             Cedar Park, TX  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Wednesday, Jan 15                          │
│  ─────────────────────────────────────────  │
│  (No appointments)                          │
│                                             │
└─────────────────────────────────────────────┘
```

### 4.3 Implementation

```kotlin
// AgendaView.kt
@Composable
fun AgendaView(
    appointments: LazyPagingItems<AppointmentWithDetails>,
    onAppointmentClick: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshState = rememberPullRefreshState(
        refreshing = appointments.loadState.refresh is LoadState.Loading,
        onRefresh = onRefresh
    )
    
    Box(modifier = modifier.pullRefresh(refreshState)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val groupedAppointments = appointments.itemSnapshotList.items
                .groupBy { it.appointment.date }
                .toSortedMap()
            
            groupedAppointments.forEach { (date, dayAppointments) ->
                // Sticky date header
                stickyHeader(key = "header_$date") {
                    AgendaDateHeader(date = date)
                }
                
                // Appointments for this date
                items(
                    items = dayAppointments,
                    key = { it.appointment.id }
                ) { appointment ->
                    AppointmentCard(
                        appointment = appointment,
                        onClick = { onAppointmentClick(appointment.appointment.id) }
                    )
                }
                
                // Empty day placeholder (optional based on settings)
                if (dayAppointments.isEmpty()) {
                    item {
                        EmptyDayIndicator()
                    }
                }
            }
            
            // Loading indicator at bottom
            if (appointments.loadState.append is LoadState.Loading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .wrapContentWidth()
                    )
                }
            }
        }
        
        PullRefreshIndicator(
            refreshing = appointments.loadState.refresh is LoadState.Loading,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
```

### 4.4 Date Header

```kotlin
// AgendaDateHeader.kt
@Composable
fun AgendaDateHeader(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    
    val displayText = when {
        date == today -> "Today - ${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        date == tomorrow -> "Tomorrow - ${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}"
        date < today.plusDays(7) -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}
```

### 4.5 Appointment Card

```kotlin
// AppointmentCard.kt
@Composable
fun AppointmentCard(
    appointment: AppointmentWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Time
                Text(
                    text = appointment.appointment.time.format(
                        DateTimeFormatter.ofPattern("h:mm a")
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Client name
                Text(
                    text = appointment.client.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Horses
                val horseText = buildHorseDisplayText(appointment.horses)
                Text(
                    text = "🐴 $horseText",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Price and location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatCurrency(appointment.totalPrice),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = appointment.client.city?.let { "$it, ${appointment.client.state}" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Status badge
            StatusBadge(status = appointment.appointment.status)
        }
    }
}

private fun buildHorseDisplayText(horses: List<AppointmentHorseWithHorse>): String {
    val names = horses.map { it.horse.name }
    return when {
        names.size <= 3 -> "${names.joinToString(", ")} (${names.size})"
        else -> "${names.take(2).joinToString(", ")} +${names.size - 2} more (${names.size})"
    }
}
```

### 4.6 Status Badge

```kotlin
// StatusBadge.kt
@Composable
fun StatusBadge(
    status: AppointmentStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, text) = when (status) {
        AppointmentStatus.SCHEDULED -> Triple(
            Color(0xFF2196F3), // Blue
            Color.White,
            "SCHEDULED"
        )
        AppointmentStatus.CONFIRMED -> Triple(
            Color(0xFF4CAF50), // Green
            Color.White,
            "CONFIRMED"
        )
        AppointmentStatus.COMPLETED -> Triple(
            Color(0xFF9E9E9E), // Gray
            Color.White,
            "COMPLETED"
        )
        AppointmentStatus.CANCELLED -> Triple(
            Color(0xFFFF9800), // Orange
            Color.White,
            "CANCELLED"
        )
        AppointmentStatus.NO_SHOW -> Triple(
            Color(0xFFF44336), // Red
            Color.White,
            "NO SHOW"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
```

---

## 5. Day View

### 5.1 Purpose

Time-blocked visualization of a single day showing appointments as blocks, blocked time from external calendars, and current time indicator.

### 5.2 Layout

```
┌─────────────────────────────────────────────┐
│  Monday, January 13, 2025                   │
│  ─────────────────────────────────────────  │
│  6 appts · 12 horses · 47 mi · 1h 23m drive │
├─────────────────────────────────────────────┤
│                                             │
│  6:00 ─────────────────────────────────     │
│                                             │
│  7:00 ─────────────────────────────────     │
│                                             │
│  8:00 ┌───────────────────────────────┐     │
│       │ 🟢 Johnson Ranch              │     │
│       │ 3 horses · Full, Full, Trim   │     │
│  9:00 │                               │     │
│       │ 8:00 AM - 9:30 AM             │     │
│  9:30 └───────────────────────────────┘     │
│       ↓ 15 min drive                        │
│ 10:00 ┌───────────────────────────────┐     │
│       │ 🔵 Williams Farm              │     │
│       │ 2 horses · Trim, Trim         │     │
│ 11:00 │                               │     │
│       └───────────────────────────────┘     │
│ ─────────────── 🔴 NOW ───────────────      │
│ 12:00 ┌───────────────────────────────┐     │
│       │ ░░░ Lunch (blocked) ░░░░░░░░░ │     │
│  1:00 └───────────────────────────────┘     │
│                                             │
│  2:00 ┌───────────────────────────────┐     │
│       │ 🔵 Martinez Ranch             │     │
│       │ 4 horses · Full, Full, Trim,  │     │
│  3:00 │ Corrective                    │     │
│       │                               │     │
│  4:00 └───────────────────────────────┘     │
│                                             │
└─────────────────────────────────────────────┘
```

### 5.3 Day Stats Header

```kotlin
// DayStatsHeader.kt
@Composable
fun DayStatsHeader(
    date: LocalDate,
    stats: DayStats,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Date
        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatChip(
                icon = Icons.Default.Event,
                value = "${stats.appointmentCount} appts"
            )
            StatChip(
                icon = Icons.Default.Pets,
                value = "${stats.horseCount} horses"
            )
            if (stats.totalMiles > 0) {
                StatChip(
                    icon = Icons.Default.Route,
                    value = "${stats.totalMiles.roundToInt()} mi"
                )
                StatChip(
                    icon = Icons.Default.DriveEta,
                    value = formatDuration(stats.totalDriveMinutes)
                )
            }
        }
    }
}

data class DayStats(
    val appointmentCount: Int = 0,
    val horseCount: Int = 0,
    val totalMiles: Double = 0.0,
    val totalDriveMinutes: Int = 0
)
```

### 5.4 Time Grid

```kotlin
// TimeSlotGrid.kt
@Composable
fun TimeSlotGrid(
    startHour: Int = 6,
    endHour: Int = 20,
    modifier: Modifier = Modifier
) {
    val slotHeight = 60.dp // 1 hour = 60dp
    
    Column(modifier = modifier) {
        for (hour in startHour..endHour) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight),
                verticalAlignment = Alignment.Top
            ) {
                // Time label
                Text(
                    text = formatHour(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp)
                )
                
                // Grid line
                Divider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }
    }
}

private fun formatHour(hour: Int): String {
    return when {
        hour == 0 || hour == 24 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
```

### 5.5 Appointment Block

```kotlin
// AppointmentBlock.kt
@Composable
fun AppointmentBlock(
    appointment: AppointmentWithDetails,
    slotHeight: Dp = 60.dp, // 1 hour
    startHour: Int = 6,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startTime = appointment.appointment.time
    val duration = appointment.appointment.durationMinutes
    
    // Calculate position
    val startMinutes = startTime.hour * 60 + startTime.minute
    val gridStartMinutes = startHour * 60
    val topOffset = ((startMinutes - gridStartMinutes) / 60f * slotHeight.value).dp
    val height = (duration / 60f * slotHeight.value).dp
    
    val statusColor = when (appointment.appointment.status) {
        AppointmentStatus.SCHEDULED -> Color(0xFF2196F3)
        AppointmentStatus.CONFIRMED -> Color(0xFF4CAF50)
        AppointmentStatus.COMPLETED -> Color(0xFF9E9E9E)
        AppointmentStatus.CANCELLED -> Color(0xFFFF9800)
        AppointmentStatus.NO_SHOW -> Color(0xFFF44336)
    }
    
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(start = 56.dp, end = 8.dp)
            .offset(y = topOffset)
            .height(height.coerceAtLeast(48.dp))
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.15f)
        ),
        border = BorderStroke(2.dp, statusColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Status dot + client name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = appointment.client.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Horses and services
            if (height > 60.dp) {
                val horsesSummary = buildString {
                    append("${appointment.horses.size} horses · ")
                    append(appointment.horses.joinToString(", ") { 
                        it.appointmentHorse.serviceType.displayName 
                    })
                }
                Text(
                    text = horsesSummary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Time range (if space)
            if (height > 80.dp) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${formatTime(startTime)} - ${formatTime(startTime.plusMinutes(duration.toLong()))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

### 5.6 Blocked Time Block

```kotlin
// BlockedTimeBlock.kt
@Composable
fun BlockedTimeBlock(
    blockedTime: BlockedTime,
    slotHeight: Dp = 60.dp,
    startHour: Int = 6,
    modifier: Modifier = Modifier
) {
    val startMinutes = blockedTime.startTime.hour * 60 + blockedTime.startTime.minute
    val endMinutes = blockedTime.endTime.hour * 60 + blockedTime.endTime.minute
    val gridStartMinutes = startHour * 60
    
    val topOffset = ((startMinutes - gridStartMinutes) / 60f * slotHeight.value).dp
    val height = ((endMinutes - startMinutes) / 60f * slotHeight.value).dp
    
    Box(
        modifier = modifier
            .padding(start = 56.dp, end = 8.dp)
            .offset(y = topOffset)
            .height(height.coerceAtLeast(32.dp))
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "░░░ ${blockedTime.title} ░░░",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class BlockedTime(
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val source: String // External calendar name
)
```

### 5.7 Current Time Indicator

```kotlin
// CurrentTimeIndicator.kt
@Composable
fun CurrentTimeIndicator(
    slotHeight: Dp = 60.dp,
    startHour: Int = 6,
    modifier: Modifier = Modifier
) {
    val currentTime = remember { mutableStateOf(LocalTime.now()) }
    
    // Update every minute
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTime.value = LocalTime.now()
        }
    }
    
    val time = currentTime.value
    val startMinutes = time.hour * 60 + time.minute
    val gridStartMinutes = startHour * 60
    val topOffset = ((startMinutes - gridStartMinutes) / 60f * slotHeight.value).dp
    
    Row(
        modifier = modifier
            .offset(y = topOffset)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(0xFFF44336), CircleShape)
        )
        
        // Red line
        Divider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFF44336),
            thickness = 2.dp
        )
        
        // Time label
        Text(
            text = "NOW",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFF44336),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
```

### 5.8 Drive Time Indicator

```kotlin
// DriveTimeIndicator.kt
@Composable
fun DriveTimeIndicator(
    driveMinutes: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(start = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DriveEta,
            contentDescription = "Drive time",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "↓ ${driveMinutes} min drive",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### 5.9 Complete Day View

```kotlin
// DayView.kt
@Composable
fun DayView(
    date: LocalDate,
    stats: DayStats,
    appointments: List<AppointmentWithDetails>,
    blockedTimes: List<BlockedTime>,
    driveTimesBetween: Map<String, Int>, // appointmentId -> driveMinutesFromPrevious
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Stats header
        DayStatsHeader(date = date, stats = stats)
        
        Divider()
        
        // Scrollable time grid
        val scrollState = rememberScrollState()
        
        // Auto-scroll to current time on load
        LaunchedEffect(Unit) {
            if (date == LocalDate.now()) {
                val currentHour = LocalTime.now().hour
                val scrollPosition = ((currentHour - 6).coerceAtLeast(0) * 60)
                scrollState.animateScrollTo(scrollPosition)
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Background time grid
            TimeSlotGrid()
            
            // Blocked times
            blockedTimes.forEach { blocked ->
                BlockedTimeBlock(blockedTime = blocked)
            }
            
            // Appointments with drive times
            val sortedAppointments = appointments.sortedBy { it.appointment.time }
            sortedAppointments.forEachIndexed { index, appointment ->
                // Drive time indicator (if not first)
                if (index > 0) {
                    driveTimesBetween[appointment.appointment.id]?.let { driveMinutes ->
                        // Position between previous and current appointment
                        DriveTimeIndicator(driveMinutes = driveMinutes)
                    }
                }
                
                AppointmentBlock(
                    appointment = appointment,
                    onClick = { onAppointmentClick(appointment.appointment.id) }
                )
            }
            
            // Current time indicator (only for today)
            if (date == LocalDate.now()) {
                CurrentTimeIndicator()
            }
        }
    }
}
```

### 5.10 Day Navigation Gestures

```kotlin
// DayViewWithGestures.kt
@Composable
fun DayViewWithGestures(
    // ... existing params
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val threshold = 100f
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offsetX > threshold -> onPreviousDay()
                            offsetX < -threshold -> onNextDay()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        DayView(/* ... */)
    }
}
```

---

## 6. Week View

### 6.1 Purpose

7-day overview showing appointment load per day with visual intensity indicators for workload planning.

### 6.2 Layout

```
┌─────────────────────────────────────────────┐
│        ◀  Jan 13 - 19, 2025  ▶             │
├─────────────────────────────────────────────┤
│                                             │
│  Mon   Tue   Wed   Thu   Fri   Sat   Sun    │
│ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐  │
│ │13 │ │14 │ │15 │ │16 │ │17 │ │18 │ │19 │  │
│ │   │ │   │ │   │ │   │ │   │ │   │ │   │  │
│ │ 6 │ │ 4 │ │ 5 │ │ 7 │ │ 3 │ │ - │ │ - │  │
│ │███│ │██ │ │██ │ │███│ │█  │ │   │ │   │  │
│ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘  │
│  ◉                                          │
│                                             │
│ ◉ = Today                                   │
│ Bar height = relative appointment load      │
│                                             │
├─────────────────────────────────────────────┤
│                                             │
│  Selected: Monday, Jan 13                   │
│  6 appointments · 12 horses · $1,340.00     │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 8:00 AM    Johnson Ranch            │    │
│  │            3 horses · $495.00       │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │ 10:30 AM   Williams Farm            │    │
│  │            2 horses · $90.00        │    │
│  └─────────────────────────────────────┘    │
│           (scrollable list)                 │
│                                             │
└─────────────────────────────────────────────┘
```

### 6.3 Implementation

```kotlin
// WeekView.kt
@Composable
fun WeekView(
    weekStart: LocalDate, // Monday
    appointmentCounts: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    selectedDayAppointments: List<AppointmentWithDetails>,
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Week header with day cells
        WeekGrid(
            weekStart = weekStart,
            appointmentCounts = appointmentCounts,
            selectedDate = selectedDate,
            onDateSelected = onDateSelected
        )
        
        Divider()
        
        // Selected day detail
        SelectedDayDetail(
            date = selectedDate,
            appointments = selectedDayAppointments,
            onAppointmentClick = onAppointmentClick
        )
    }
}

@Composable
fun WeekGrid(
    weekStart: LocalDate,
    appointmentCounts: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val maxCount = appointmentCounts.values.maxOrNull() ?: 1
    
    Column(modifier = modifier.padding(8.dp)) {
        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Day cells
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (0..6).forEach { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val count = appointmentCounts[date] ?: 0
                
                WeekDayCell(
                    date = date,
                    appointmentCount = count,
                    maxCount = maxCount,
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    onClick = { onDateSelected(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun WeekDayCell(
    date: LocalDate,
    appointmentCount: Int,
    maxCount: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = if (maxCount > 0) {
        (appointmentCount.toFloat() / maxCount * 24).dp
    } else 0.dp
    
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    
    val borderColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.8f),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isToday) BorderStroke(2.dp, borderColor) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Date number
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Appointment count
            if (appointmentCount > 0) {
                Text(
                    text = appointmentCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Load indicator bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight.coerceAtLeast(2.dp))
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (appointmentCount > 0) 0.6f else 0.1f
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
```

### 6.4 Selected Day Detail

```kotlin
// SelectedDayDetail.kt
@Composable
fun SelectedDayDetail(
    date: LocalDate,
    appointments: List<AppointmentWithDetails>,
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPrice = appointments.sumOf { apt ->
        apt.horses.sumOf { it.appointmentHorse.price }
    }
    val horseCount = appointments.sumOf { it.horses.size }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Summary header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Selected: ${date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${appointments.size} appointments · $horseCount horses · ${formatCurrency(totalPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Appointment list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = appointments.sortedBy { it.appointment.time },
                key = { it.appointment.id }
            ) { appointment ->
                CompactAppointmentCard(
                    appointment = appointment,
                    onClick = { onAppointmentClick(appointment.appointment.id) }
                )
            }
            
            // Empty state
            if (appointments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No appointments scheduled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
```

---

## 7. Empty States

### 7.1 No Appointments (Any View)

```
┌─────────────────────────────────────────────┐
│                                             │
│                    📅                       │
│                                             │
│         No appointments scheduled           │
│                                             │
│      Your calendar is clear. Ready to       │
│      schedule your first appointment?       │
│                                             │
│           [+ New Appointment]               │
│                                             │
└─────────────────────────────────────────────┘
```

### 7.2 No Appointments for Day/Week

```
┌─────────────────────────────────────────────┐
│                                             │
│         No appointments this [day/week]     │
│                                             │
│           [+ Schedule Appointment]          │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 8. Data Models

### 8.1 Display Models

```kotlin
// CalendarDisplayModels.kt

data class DayStats(
    val appointmentCount: Int = 0,
    val horseCount: Int = 0,
    val totalMiles: Double = 0.0,
    val totalDriveMinutes: Int = 0,
    val totalRevenue: BigDecimal = BigDecimal.ZERO
)

data class BlockedTime(
    val id: String,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val source: String, // Calendar name
    val color: Int? = null
)

data class AppointmentDisplayData(
    val id: String,
    val date: LocalDate,
    val time: LocalTime,
    val duration: Int,
    val clientName: String,
    val clientCity: String?,
    val horseCount: Int,
    val servicesSummary: String,
    val totalPrice: BigDecimal,
    val status: AppointmentStatus,
    val isConfirmed: Boolean,
    val driveMinutesFromPrevious: Int?
)

data class WeekDayData(
    val date: LocalDate,
    val appointmentCount: Int,
    val horseCount: Int,
    val totalRevenue: BigDecimal,
    val isWorkingDay: Boolean
)
```

### 8.2 DAO Extensions

```kotlin
// AppointmentDao.kt (additional queries for calendar)
@Dao
interface AppointmentDao {
    // ... existing queries
    
    @Query("""
        SELECT date, COUNT(*) as count 
        FROM appointments 
        WHERE user_id = :userId 
        AND date >= :startDate 
        AND date <= :endDate
        AND status NOT IN ('CANCELLED', 'NO_SHOW')
        GROUP BY date
    """)
    fun getAppointmentCountsByDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<Map<LocalDate, Int>>
    
    @Query("""
        SELECT * FROM appointments 
        WHERE user_id = :userId 
        AND date = :date
        AND status NOT IN ('CANCELLED')
        ORDER BY time ASC
    """)
    fun getActiveAppointmentsForDate(
        userId: String,
        date: LocalDate
    ): Flow<List<AppointmentWithDetails>>
}
```

---

## 9. Acceptance Criteria

### 9.1 View Switching

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-006-01 | Switch to Day view | On Agenda view | Tap "Day" segment | Day view shown with today's appointments |
| AC-006-02 | Switch to Week view | On Day view | Tap "Week" segment | Week view shown with current week |
| AC-006-03 | View preference persists | Switch to Week view | Close and reopen app | Week view shown |
| AC-006-04 | Smooth transitions | Any view | Switch views | Animation < 200ms, no flicker |

### 9.2 Agenda View

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-006-05 | Appointments grouped by date | 5 appointments across 3 days | View Agenda | Grouped under date headers |
| AC-006-06 | Today header special | Today has appointments | View Agenda | Header shows "Today - Monday, Jan 13" |
| AC-006-07 | Infinite scroll | 50+ appointments | Scroll down | Loads more appointments smoothly |
| AC-006-08 | Pull to refresh | Network available | Pull down | Syncs latest appointments |
| AC-006-09 | Status colors correct | Appointments with all statuses | View cards | Colors match status mapping |

### 9.3 Day View

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-006-10 | Time grid shows correctly | Day view open | View screen | 6 AM to 8 PM visible, scrollable |
| AC-006-11 | Appointments as blocks | 2-hour appointment at 9 AM | View day | Block spans 9 AM to 11 AM |
| AC-006-12 | Current time indicator | Viewing today | At 10:30 AM | Red line at 10:30 position |
| AC-006-13 | Blocked time shown | External calendar has "Lunch" blocked | View day | Gray block for lunch period |
| AC-006-14 | Day stats accurate | 4 appts, 8 horses, 35 miles | View header | Shows "4 appts · 8 horses · 35 mi" |
| AC-006-15 | Swipe navigation | Day view open | Swipe left | Next day shown |
| AC-006-16 | Auto-scroll to current time | Open Day view for today | View loads | Scrolled to current time position |

### 9.4 Week View

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-006-17 | 7 days displayed | Week view open | View screen | Mon-Sun columns visible |
| AC-006-18 | Appointment counts shown | Mon=6, Tue=4, Wed=5 | View week | Numbers shown in each cell |
| AC-006-19 | Today highlighted | Today is Wednesday | View week | Wednesday has border highlight |
| AC-006-20 | Day selection | Week view open | Tap Thursday | Thursday selected, detail list updates |
| AC-006-21 | Load bars proportional | Max day has 7 appts | View week | Max day has full bar, others proportional |

### 9.5 Navigation

| ID | Scenario | Given | When | Then |
|----|----------|-------|------|------|
| AC-006-22 | Today button works | Viewing different date | Tap Today | Returns to current date in any view |
| AC-006-23 | Date picker opens | Any view | Tap month header | DatePicker dialog opens |
| AC-006-24 | Navigate to selected date | DatePicker open | Select Feb 15 | View updates to Feb 15 |

---

## 10. Performance Requirements

| Operation | Target | Measurement |
|-----------|--------|-------------|
| View render | < 100ms | Initial render time |
| View switch | < 200ms | Animation + data load |
| Scroll FPS | 60 FPS | Agenda infinite scroll |
| Date change | < 100ms | Day/week navigation |
| Pull refresh | < 2s | Network + UI update |

---

## 11. File References

### 11.1 UI Layer

| File | Purpose |
|------|---------|
| `feature/calendar/ui/CalendarScreen.kt` | Container screen |
| `feature/calendar/ui/CalendarViewModel.kt` | State management |
| `feature/calendar/ui/CalendarHeader.kt` | Navigation header |
| `feature/calendar/ui/ViewTypeSelector.kt` | View type tabs |
| `feature/calendar/ui/agenda/AgendaView.kt` | Agenda view |
| `feature/calendar/ui/agenda/AgendaDateHeader.kt` | Date headers |
| `feature/calendar/ui/day/DayView.kt` | Day view |
| `feature/calendar/ui/day/TimeSlotGrid.kt` | Time grid |
| `feature/calendar/ui/day/AppointmentBlock.kt` | Appointment blocks |
| `feature/calendar/ui/day/BlockedTimeBlock.kt` | Blocked time |
| `feature/calendar/ui/day/CurrentTimeIndicator.kt` | Now indicator |
| `feature/calendar/ui/day/DayStatsHeader.kt` | Stats display |
| `feature/calendar/ui/week/WeekView.kt` | Week view |
| `feature/calendar/ui/week/WeekGrid.kt` | Day cells grid |
| `feature/calendar/ui/week/WeekDayCell.kt` | Individual day cell |
| `feature/calendar/ui/component/AppointmentCard.kt` | List card |
| `feature/calendar/ui/component/StatusBadge.kt` | Status chip |

### 11.2 Data Layer

| File | Purpose |
|------|---------|
| `core/model/CalendarViewType.kt` | View type enum |
| `core/data/UserPreferencesManager.kt` | Preference storage |
| `feature/calendar/data/CalendarRepository.kt` | Calendar data access |

---

## 12. Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] CalendarViewType enum
- [ ] UserPreferencesManager view type persistence
- [ ] CalendarViewModel base
- [ ] CalendarScreen container
- [ ] View type selector

### Phase 2: Agenda View
- [ ] AgendaView with infinite scroll
- [ ] AgendaDateHeader with sticky headers
- [ ] AppointmentCard component
- [ ] StatusBadge component
- [ ] Pull-to-refresh

### Phase 3: Day View
- [ ] DayView container
- [ ] TimeSlotGrid
- [ ] AppointmentBlock positioning
- [ ] CurrentTimeIndicator
- [ ] DayStatsHeader
- [ ] Swipe navigation

### Phase 4: Week View
- [ ] WeekView container
- [ ] WeekGrid with day cells
- [ ] WeekDayCell with load bars
- [ ] Selected day detail list
- [ ] Week navigation

### Phase 5: Integration
- [ ] Blocked time from external calendars
- [ ] Route integration for drive times
- [ ] Deep link handling
- [ ] Accessibility
- [ ] Performance optimization
