# FRD-018: Reporting & Analytics

**Version:** 1.0  
**Last Updated:** January 2026  
**PRD Reference:** PRD-018-reporting.md  
**Priority:** P1  
**Phase:** 6 - Polish & Launch

---

## 1. Overview

### 1.1 Purpose

Provide business insights through dashboards and reports to help farriers track performance, understand revenue trends, and prepare for tax filing with accurate mileage and income data.

### 1.2 Scope

This FRD covers:
- Dashboard with key metrics at a glance
- Revenue reports with filtering and breakdown
- Appointment completion reports
- Mileage reports for tax purposes
- Due soon report for upcoming horse service
- CSV export functionality

### 1.3 Dependencies

| Dependency | Type | FRD Reference |
|------------|------|---------------|
| Appointments | Internal | FRD-005 |
| Invoicing | Internal | FRD-013 |
| Mileage Tracking | Internal | FRD-011 |
| Horse Management | Internal | FRD-004 |
| Client Management | Internal | FRD-003 |

---

## 2. Report Period Selection

### 2.1 Available Periods

```kotlin
// Location: core/domain/model/ReportPeriod.kt
enum class ReportPeriod {
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    THIS_QUARTER,
    LAST_QUARTER,
    THIS_YEAR,
    LAST_YEAR,
    CUSTOM;
    
    fun getDateRange(): ClosedRange<LocalDate> {
        val today = LocalDate.now()
        return when (this) {
            THIS_WEEK -> {
                val start = today.with(DayOfWeek.MONDAY)
                start..today
            }
            LAST_WEEK -> {
                val end = today.with(DayOfWeek.MONDAY).minusDays(1)
                val start = end.minusDays(6)
                start..end
            }
            THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                start..today
            }
            LAST_MONTH -> {
                val end = today.withDayOfMonth(1).minusDays(1)
                val start = end.withDayOfMonth(1)
                start..end
            }
            THIS_QUARTER -> {
                val quarterStart = today.with(today.month.firstMonthOfQuarter())
                    .withDayOfMonth(1)
                quarterStart..today
            }
            LAST_QUARTER -> {
                val thisQuarterStart = today.with(today.month.firstMonthOfQuarter())
                    .withDayOfMonth(1)
                val lastQuarterEnd = thisQuarterStart.minusDays(1)
                val lastQuarterStart = lastQuarterEnd.minusMonths(2).withDayOfMonth(1)
                lastQuarterStart..lastQuarterEnd
            }
            THIS_YEAR -> {
                val start = today.withDayOfYear(1)
                start..today
            }
            LAST_YEAR -> {
                val lastYear = today.minusYears(1)
                val start = lastYear.withDayOfYear(1)
                val end = lastYear.withDayOfYear(lastYear.lengthOfYear())
                start..end
            }
            CUSTOM -> today..today // Placeholder, set via parameters
        }
    }
    
    val displayName: String
        get() = when (this) {
            THIS_WEEK -> "This Week"
            LAST_WEEK -> "Last Week"
            THIS_MONTH -> "This Month"
            LAST_MONTH -> "Last Month"
            THIS_QUARTER -> "This Quarter"
            LAST_QUARTER -> "Last Quarter"
            THIS_YEAR -> "This Year"
            LAST_YEAR -> "Last Year"
            CUSTOM -> "Custom Range"
        }
}
```

### 2.2 Period Selector Component

```kotlin
// Location: feature/reports/ui/components/PeriodSelector.kt
@Composable
fun PeriodSelector(
    selectedPeriod: ReportPeriod,
    customRange: ClosedRange<LocalDate>?,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onCustomRangeSelected: (ClosedRange<LocalDate>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { showDropdown = true }
        ) {
            Text(selectedPeriod.displayName)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            ReportPeriod.values().forEach { period ->
                DropdownMenuItem(
                    text = { Text(period.displayName) },
                    onClick = {
                        showDropdown = false
                        if (period == ReportPeriod.CUSTOM) {
                            showDatePicker = true
                        } else {
                            onPeriodSelected(period)
                        }
                    },
                    leadingIcon = if (period == selectedPeriod) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
    
    if (showDatePicker) {
        DateRangePickerDialog(
            initialRange = customRange,
            onConfirm = { range ->
                showDatePicker = false
                onCustomRangeSelected(range)
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
```

---

## 3. Dashboard

### 3.1 Route and Navigation

**Route:** `/reports`

Access via bottom navigation "Reports" tab.

### 3.2 Dashboard Layout

```
┌─────────────────────────────────────────┐
│ Reports           [This Month ▼]        │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────┐  ┌─────────────┐       │
│  │     24      │  │   $4,320    │       │
│  │  Completed  │  │   Revenue   │       │
│  │  ▲ 12%     │  │   ▲ 8%     │       │
│  └─────────────┘  └─────────────┘       │
│                                         │
│  ┌─────────────┐  ┌─────────────┐       │
│  │    847      │  │     68      │       │
│  │   Miles     │  │   Horses    │       │
│  │  ▼ 5%      │  │   ▲ 15%    │       │
│  └─────────────┘  └─────────────┘       │
│                                         │
│  Quick Reports                          │
│  ─────────────────────────────────────  │
│  Revenue Details                    >   │
│  Appointments                       >   │
│  Mileage Summary                    >   │
│  Due Soon                           >   │
│                                         │
└─────────────────────────────────────────┘
```

### 3.3 Dashboard Data Model

```kotlin
// Location: core/domain/model/DashboardStats.kt
data class DashboardStats(
    val appointmentsCompleted: Int,
    val appointmentsDelta: Float?,  // Percentage change from previous period
    val revenueEarned: BigDecimal,
    val revenueDelta: Float?,
    val milesDriven: Double,
    val milesDelta: Float?,
    val horsesServiced: Int,
    val horsesDelta: Float?
) {
    companion object {
        val EMPTY = DashboardStats(
            appointmentsCompleted = 0,
            appointmentsDelta = null,
            revenueEarned = BigDecimal.ZERO,
            revenueDelta = null,
            milesDriven = 0.0,
            milesDelta = null,
            horsesServiced = 0,
            horsesDelta = null
        )
    }
}
```

### 3.4 Dashboard Repository

```kotlin
// Location: data/repository/ReportsRepository.kt
interface ReportsRepository {
    fun getDashboardStats(period: ReportPeriod): Flow<DashboardStats>
    fun getRevenueReport(period: ReportPeriod): Flow<RevenueReport>
    fun getAppointmentReport(period: ReportPeriod): Flow<AppointmentReport>
    fun getMileageReport(period: ReportPeriod): Flow<MileageReport>
    fun getDueSoonReport(): Flow<DueSoonReport>
    suspend fun exportRevenueReportCsv(period: ReportPeriod): File
    suspend fun exportMileageReportCsv(period: ReportPeriod): File
}

// Location: data/repository/ReportsRepositoryImpl.kt
class ReportsRepositoryImpl @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val invoiceDao: InvoiceDao,
    private val mileageDao: MileageDao,
    private val horseDao: HorseDao,
    private val clientDao: ClientDao,
    @ApplicationContext private val context: Context
) : ReportsRepository {
    
    override fun getDashboardStats(period: ReportPeriod): Flow<DashboardStats> = flow {
        val dateRange = period.getDateRange()
        val previousRange = getPreviousPeriodRange(period)
        
        // Current period stats
        val appointments = appointmentDao.getCompletedCount(
            startDate = dateRange.start.toString(),
            endDate = dateRange.endInclusive.toString()
        )
        
        val revenue = invoiceDao.getTotalPaidAmount(
            startDate = dateRange.start.toString(),
            endDate = dateRange.endInclusive.toString()
        )
        
        val miles = mileageDao.getTotalMiles(
            startDate = dateRange.start.toString(),
            endDate = dateRange.endInclusive.toString()
        )
        
        val horses = appointmentDao.getUniqueHorsesServiced(
            startDate = dateRange.start.toString(),
            endDate = dateRange.endInclusive.toString()
        )
        
        // Previous period for comparison
        val prevAppointments = appointmentDao.getCompletedCount(
            startDate = previousRange.start.toString(),
            endDate = previousRange.endInclusive.toString()
        )
        
        val prevRevenue = invoiceDao.getTotalPaidAmount(
            startDate = previousRange.start.toString(),
            endDate = previousRange.endInclusive.toString()
        )
        
        val prevMiles = mileageDao.getTotalMiles(
            startDate = previousRange.start.toString(),
            endDate = previousRange.endInclusive.toString()
        )
        
        val prevHorses = appointmentDao.getUniqueHorsesServiced(
            startDate = previousRange.start.toString(),
            endDate = previousRange.endInclusive.toString()
        )
        
        emit(DashboardStats(
            appointmentsCompleted = appointments,
            appointmentsDelta = calculateDelta(appointments, prevAppointments),
            revenueEarned = revenue ?: BigDecimal.ZERO,
            revenueDelta = calculateDelta(revenue, prevRevenue),
            milesDriven = miles ?: 0.0,
            milesDelta = calculateDelta(miles, prevMiles),
            horsesServiced = horses,
            horsesDelta = calculateDelta(horses, prevHorses)
        ))
    }
    
    private fun calculateDelta(current: Int, previous: Int): Float? {
        if (previous == 0) return null
        return ((current - previous).toFloat() / previous) * 100
    }
    
    private fun calculateDelta(current: BigDecimal?, previous: BigDecimal?): Float? {
        val curr = current ?: BigDecimal.ZERO
        val prev = previous ?: BigDecimal.ZERO
        if (prev == BigDecimal.ZERO) return null
        return ((curr - prev).toFloat() / prev.toFloat()) * 100
    }
    
    private fun calculateDelta(current: Double?, previous: Double?): Float? {
        val curr = current ?: 0.0
        val prev = previous ?: 0.0
        if (prev == 0.0) return null
        return ((curr - prev).toFloat() / prev.toFloat()) * 100
    }
}
```

### 3.5 Stat Card Component

```kotlin
// Location: feature/reports/ui/components/StatCard.kt
@Composable
fun StatCard(
    value: String,
    label: String,
    delta: Float?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            delta?.let { change ->
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (change >= 0) Icons.Default.TrendingUp 
                                      else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "${if (change >= 0) "+" else ""}${change.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
        }
    }
}
```

---

## 4. Revenue Report

### 4.1 Route

**Route:** `/reports/revenue`

### 4.2 Revenue Report Data

```kotlin
// Location: core/domain/model/RevenueReport.kt
data class RevenueReport(
    val period: ReportPeriod,
    val dateRange: ClosedRange<LocalDate>,
    val totalRevenue: BigDecimal,
    val totalPaid: BigDecimal,
    val totalOutstanding: BigDecimal,
    val byClient: List<RevenueByClient>,
    val byService: List<RevenueByService>,
    val byMonth: List<RevenueByMonth>,
    val averageInvoiceAmount: BigDecimal
)

data class RevenueByClient(
    val clientId: String,
    val clientName: String,
    val totalAmount: BigDecimal,
    val invoiceCount: Int,
    val percentage: Float  // Of total revenue
)

data class RevenueByService(
    val serviceType: ServiceType,
    val serviceName: String,
    val totalAmount: BigDecimal,
    val count: Int,
    val percentage: Float
)

data class RevenueByMonth(
    val month: YearMonth,
    val amount: BigDecimal
)
```

### 4.3 Revenue Report Screen

```
┌─────────────────────────────────────────┐
│ [←] Revenue Report    [This Month ▼]    │
├─────────────────────────────────────────┤
│                                         │
│  Total Revenue                          │
│  $4,320.00                             │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Paid          $3,890.00  90%   │    │
│  │ Outstanding   $430.00    10%   │    │
│  └─────────────────────────────────┘    │
│                                         │
│  By Client                              │
│  ─────────────────────────────────────  │
│  Johnson Farm         $890.00    21%    │
│  ████████████████░░░░░░░░░░░░░░░       │
│  Smith Ranch          $720.00    17%    │
│  ████████████░░░░░░░░░░░░░░░░░░░       │
│  Miller Stables       $680.00    16%    │
│  ████████████░░░░░░░░░░░░░░░░░░░       │
│  [View All]                             │
│                                         │
│  By Service                             │
│  ─────────────────────────────────────  │
│  Full Set            $2,160.00   50%    │
│  ██████████████████████████░░░░░       │
│  Front Shoes         $1,080.00   25%    │
│  █████████████░░░░░░░░░░░░░░░░░░       │
│  Trim                $720.00    17%    │
│  █████████░░░░░░░░░░░░░░░░░░░░░░       │
│                                         │
│  [Export CSV]                           │
│                                         │
└─────────────────────────────────────────┘
```

### 4.4 Revenue Report ViewModel

```kotlin
// Location: feature/reports/ui/RevenueReportViewModel.kt
@HiltViewModel
class RevenueReportViewModel @Inject constructor(
    private val reportsRepository: ReportsRepository
) : ViewModel() {
    
    private val _selectedPeriod = MutableStateFlow(ReportPeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()
    
    private val _customRange = MutableStateFlow<ClosedRange<LocalDate>?>(null)
    
    val report: StateFlow<RevenueReport?> = _selectedPeriod
        .flatMapLatest { period ->
            reportsRepository.getRevenueReport(period)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    fun selectPeriod(period: ReportPeriod) {
        _selectedPeriod.value = period
    }
    
    fun selectCustomRange(range: ClosedRange<LocalDate>) {
        _customRange.value = range
        _selectedPeriod.value = ReportPeriod.CUSTOM
    }
    
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    
    fun exportCsv() {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val file = reportsRepository.exportRevenueReportCsv(_selectedPeriod.value)
                _exportState.value = ExportState.Success(file)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }
}

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Success(val file: File) : ExportState()
    data class Error(val message: String) : ExportState()
}
```

---

## 5. Appointment Report

### 5.1 Route

**Route:** `/reports/appointments`

### 5.2 Appointment Report Data

```kotlin
// Location: core/domain/model/AppointmentReport.kt
data class AppointmentReport(
    val period: ReportPeriod,
    val dateRange: ClosedRange<LocalDate>,
    val totalScheduled: Int,
    val completed: Int,
    val cancelled: Int,
    val noShow: Int,
    val pending: Int,
    val completionRate: Float,  // completed / (completed + cancelled + noShow)
    val byWeek: List<AppointmentsByWeek>,
    val averageDuration: Int,  // minutes
    val busiestDay: DayOfWeek?
)

data class AppointmentsByWeek(
    val weekStart: LocalDate,
    val completed: Int,
    val cancelled: Int,
    val noShow: Int
)
```

### 5.3 Appointment Report Screen

```
┌─────────────────────────────────────────┐
│ [←] Appointments      [This Month ▼]    │
├─────────────────────────────────────────┤
│                                         │
│  Completion Rate                        │
│  ┌─────────────────────────────────┐    │
│  │           92%                   │    │
│  │    ████████████████░░░░         │    │
│  └─────────────────────────────────┘    │
│                                         │
│  Summary                                │
│  ─────────────────────────────────────  │
│  Completed     24    ●●●●●●●●●●●●●●●●  │
│  Cancelled      1    ●                 │
│  No-Show        1    ●                 │
│  Pending        2    ●●                │
│                                         │
│  Weekly Trend                           │
│  ─────────────────────────────────────  │
│  Week 1  ████████████████  8           │
│  Week 2  ██████████████    7           │
│  Week 3  ██████████        5           │
│  Week 4  ████████          4           │
│                                         │
│  Insights                               │
│  ─────────────────────────────────────  │
│  📊 Avg duration: 48 minutes           │
│  📅 Busiest day: Tuesdays              │
│                                         │
└─────────────────────────────────────────┘
```

---

## 6. Mileage Report

### 6.1 Route

**Route:** `/reports/mileage`

### 6.2 Mileage Report Data

```kotlin
// Location: core/domain/model/MileageReport.kt
data class MileageReport(
    val period: ReportPeriod,
    val dateRange: ClosedRange<LocalDate>,
    val totalMiles: Double,
    val businessMiles: Double,
    val personalMiles: Double,
    val tripCount: Int,
    val estimatedDeduction: BigDecimal,  // IRS rate × business miles
    val irsRate: BigDecimal,  // Current year's standard mileage rate
    val byMonth: List<MileageByMonth>,
    val topClients: List<MileageByClient>
)

data class MileageByMonth(
    val month: YearMonth,
    val miles: Double
)

data class MileageByClient(
    val clientId: String,
    val clientName: String,
    val miles: Double,
    val tripCount: Int
)
```

### 6.3 Mileage Report Screen

```
┌─────────────────────────────────────────┐
│ [←] Mileage           [This Year ▼]     │
├─────────────────────────────────────────┤
│                                         │
│  Total Miles                            │
│  8,472                                 │
│                                         │
│  Business: 8,124 mi (96%)              │
│  Personal: 348 mi (4%)                 │
│                                         │
│  Tax Deduction Estimate                 │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐    │
│  │ $5,441.06                       │    │
│  │ Based on 8,124 mi × $0.67/mi    │    │
│  │ (2025 IRS standard rate)        │    │
│  └─────────────────────────────────┘    │
│                                         │
│  Monthly Breakdown                      │
│  ─────────────────────────────────────  │
│  Jan  ████████████████  812 mi         │
│  Feb  ██████████████    702 mi         │
│  Mar  ████████████████  824 mi         │
│  ...                                    │
│                                         │
│  Top Clients by Mileage                 │
│  ─────────────────────────────────────  │
│  Johnson Farm         1,248 mi         │
│  Miller Stables         892 mi         │
│  Smith Ranch            764 mi         │
│                                         │
│  [Export for Schedule C]                │
│                                         │
└─────────────────────────────────────────┘
```

### 6.4 Mileage CSV Export

```kotlin
// Location: data/export/MileageCsvExporter.kt
class MileageCsvExporter @Inject constructor(
    private val mileageDao: MileageDao,
    @ApplicationContext private val context: Context
) {
    suspend fun export(
        dateRange: ClosedRange<LocalDate>
    ): File {
        val trips = mileageDao.getTripsInRange(
            startDate = dateRange.start.toString(),
            endDate = dateRange.endInclusive.toString()
        )
        
        val outputDir = File(context.cacheDir, "exports")
        outputDir.mkdirs()
        
        val filename = "mileage_${dateRange.start}_to_${dateRange.endInclusive}.csv"
        val file = File(outputDir, filename)
        
        file.bufferedWriter().use { writer ->
            // Header
            writer.write("Date,Description,Miles,Purpose,Client\n")
            
            // Data rows
            trips.forEach { trip ->
                writer.write(
                    "${trip.date}," +
                    "\"${trip.description.escapeCsv()}\"," +
                    "${trip.miles}," +
                    "${trip.purpose}," +
                    "\"${trip.clientName?.escapeCsv() ?: ""}\"\n"
                )
            }
            
            // Summary
            writer.write("\n")
            writer.write("Total Business Miles,${trips.filter { it.purpose == "BUSINESS" }.sumOf { it.miles }}\n")
            writer.write("IRS Rate (2025),$0.67\n")
            writer.write("Estimated Deduction,${calculateDeduction(trips)}\n")
        }
        
        return file
    }
    
    private fun String.escapeCsv(): String = 
        this.replace("\"", "\"\"")
}
```

---

## 7. Due Soon Report

### 7.1 Route

**Route:** `/reports/due-soon`

### 7.2 Due Soon Data

```kotlin
// Location: core/domain/model/DueSoonReport.kt
data class DueSoonReport(
    val totalHorsesDueSoon: Int,
    val overdue: List<HorseDueItem>,      // Past due date
    val dueThisWeek: List<HorseDueItem>,  // Due within 7 days
    val dueNextWeek: List<HorseDueItem>,  // Due 8-14 days
    val dueLater: List<HorseDueItem>      // Due 15-30 days
)

data class HorseDueItem(
    val horseId: String,
    val horseName: String,
    val clientId: String,
    val clientName: String,
    val dueDate: LocalDate,
    val lastServiceDate: LocalDate?,
    val lastServiceType: ServiceType?,
    val daysOverdue: Int,  // Negative if in future
    val phoneNumber: String?
)
```

### 7.3 Due Soon Report Screen

```
┌─────────────────────────────────────────┐
│ [←] Due Soon                            │
├─────────────────────────────────────────┤
│                                         │
│  12 horses need service soon            │
│                                         │
│  🔴 Overdue (3)                         │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐    │
│  │ Bella - Smith Ranch             │    │
│  │ Due: Jan 5 (7 days overdue)     │    │
│  │ Last: Trim on Dec 5             │    │
│  │         [Call]  [Schedule]      │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │ Thunder - Johnson Farm          │    │
│  │ Due: Jan 8 (4 days overdue)     │    │
│  │ Last: Full Set on Dec 8         │    │
│  │         [Call]  [Schedule]      │    │
│  └─────────────────────────────────┘    │
│                                         │
│  📅 Due This Week (4)                   │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐    │
│  │ Star - Miller Stables           │    │
│  │ Due: Jan 15 (in 3 days)         │    │
│  │         [Call]  [Schedule]      │    │
│  └─────────────────────────────────┘    │
│  ...                                    │
│                                         │
│  📆 Due Next Week (3)                   │
│  ─────────────────────────────────────  │
│  ...                                    │
│                                         │
└─────────────────────────────────────────┘
```

### 7.4 Due Soon Repository Query

```kotlin
// Location: data/repository/ReportsRepositoryImpl.kt
override fun getDueSoonReport(): Flow<DueSoonReport> = flow {
    val today = LocalDate.now()
    val thirtyDaysOut = today.plusDays(30)
    
    val horses = horseDao.getHorsesDueBetween(
        startDate = today.minusDays(60).toString(),  // Include overdue
        endDate = thirtyDaysOut.toString()
    )
    
    val items = horses.map { horse ->
        HorseDueItem(
            horseId = horse.id,
            horseName = horse.name,
            clientId = horse.clientId,
            clientName = horse.clientName,
            dueDate = LocalDate.parse(horse.nextDueDate),
            lastServiceDate = horse.lastServiceDate?.let { LocalDate.parse(it) },
            lastServiceType = horse.lastServiceType?.let { ServiceType.valueOf(it) },
            daysOverdue = ChronoUnit.DAYS.between(
                LocalDate.parse(horse.nextDueDate), 
                today
            ).toInt(),
            phoneNumber = horse.clientPhone
        )
    }
    
    emit(DueSoonReport(
        totalHorsesDueSoon = items.size,
        overdue = items.filter { it.daysOverdue > 0 }
            .sortedByDescending { it.daysOverdue },
        dueThisWeek = items.filter { it.daysOverdue in -7..0 }
            .sortedBy { it.dueDate },
        dueNextWeek = items.filter { it.daysOverdue in -14..-8 }
            .sortedBy { it.dueDate },
        dueLater = items.filter { it.daysOverdue < -14 }
            .sortedBy { it.dueDate }
    ))
}
```

---

## 8. CSV Export

### 8.1 Export Format

**Revenue Report CSV:**
```csv
Date,Client,Service,Amount,Status
2025-01-05,Johnson Farm,Full Set,$180.00,Paid
2025-01-05,Johnson Farm,Trim,$45.00,Paid
2025-01-06,Smith Ranch,Front Shoes,$120.00,Outstanding
...

Total Revenue,$4320.00
Total Paid,$3890.00
Total Outstanding,$430.00
```

**Mileage Report CSV:**
```csv
Date,Description,Miles,Purpose,Client
2025-01-05,Round trip to Johnson Farm,48.2,Business,Johnson Farm
2025-01-05,Round trip to Smith Ranch,32.1,Business,Smith Ranch
...

Total Business Miles,8124
IRS Rate (2025),$0.67
Estimated Deduction,$5441.06
```

### 8.2 Share Intent

```kotlin
// Location: feature/reports/ui/ReportsScreen.kt
fun shareExportedFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(
        Intent.createChooser(intent, "Share Report")
    )
}
```

---

## 9. Acceptance Criteria

### 9.1 Dashboard

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-01 | Dashboard loads in < 1 second | Performance test |
| AC-018-02 | Shows 24 completed appointments for this month | Integration test |
| AC-018-03 | Shows $4,320 revenue for this month | Integration test |
| AC-018-04 | Shows +12% delta vs previous month | Unit test |
| AC-018-05 | Period selector changes all stats | UI test |

### 9.2 Revenue Report

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-06 | Total revenue matches sum of all invoices | Integration test |
| AC-018-07 | By-client breakdown percentages sum to 100% | Unit test |
| AC-018-08 | By-service breakdown shows correct counts | Integration test |
| AC-018-09 | Export generates valid CSV file | Integration test |
| AC-018-10 | CSV contains all required columns | Unit test |

### 9.3 Appointment Report

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-11 | Completion rate = completed / (completed + cancelled + no-show) | Unit test |
| AC-018-12 | Weekly breakdown shows correct counts | Integration test |
| AC-018-13 | Average duration calculated correctly | Unit test |

### 9.4 Mileage Report

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-14 | Total miles matches sum of all trips | Integration test |
| AC-018-15 | Tax deduction = business miles × IRS rate | Unit test |
| AC-018-16 | IRS rate shows 2025 value ($0.67) | Manual test |
| AC-018-17 | Export for Schedule C generates correct format | Integration test |

### 9.5 Due Soon Report

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-18 | Overdue section shows horses past due date | Integration test |
| AC-018-19 | Due This Week shows horses due within 7 days | Integration test |
| AC-018-20 | "Call" button opens dialer with client phone | UI test |
| AC-018-21 | "Schedule" button opens appointment creation | UI test |

### 9.6 Offline

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-22 | All reports work offline from local data | Integration test |
| AC-018-23 | Export works offline | Integration test |

---

## 10. Performance Requirements

| Metric | Target |
|--------|--------|
| Dashboard load | < 1 second |
| Report generation | < 2 seconds |
| CSV export | < 3 seconds |
| Data accuracy | 100% match with source data |

---

## 11. File References

```
feature/reports/
├── ui/
│   ├── ReportsScreen.kt
│   ├── ReportsViewModel.kt
│   ├── DashboardSection.kt
│   ├── RevenueReportScreen.kt
│   ├── RevenueReportViewModel.kt
│   ├── AppointmentReportScreen.kt
│   ├── AppointmentReportViewModel.kt
│   ├── MileageReportScreen.kt
│   ├── MileageReportViewModel.kt
│   ├── DueSoonScreen.kt
│   ├── DueSoonViewModel.kt
│   └── components/
│       ├── StatCard.kt
│       ├── PeriodSelector.kt
│       ├── ProgressBar.kt
│       ├── BreakdownRow.kt
│       └── HorseDueCard.kt

core/domain/model/
├── ReportPeriod.kt
├── DashboardStats.kt
├── RevenueReport.kt
├── AppointmentReport.kt
├── MileageReport.kt
└── DueSoonReport.kt

data/repository/
├── ReportsRepository.kt
└── ReportsRepositoryImpl.kt

data/export/
├── RevenueCsvExporter.kt
└── MileageCsvExporter.kt
```

---

## 12. Analytics Events

| Event | Trigger | Properties |
|-------|---------|------------|
| `reports_viewed` | Reports tab opened | - |
| `report_period_changed` | Period selector changed | period, previous_period |
| `revenue_report_viewed` | Revenue report opened | period |
| `mileage_report_viewed` | Mileage report opened | period |
| `due_soon_viewed` | Due Soon report opened | total_due |
| `report_exported` | Export button tapped | report_type, period, format |
| `due_soon_call_tapped` | Call button on due horse | - |
| `due_soon_schedule_tapped` | Schedule button on due horse | - |
