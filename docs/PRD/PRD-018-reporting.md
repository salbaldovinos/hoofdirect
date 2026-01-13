# PRD-018: Reporting & Analytics

**Priority**: P1  
**Phase**: 6 - Polish & Launch  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Provide business insights through dashboards and reports to help farriers track performance and make informed decisions.

### Business Value
- Revenue visibility
- Client analysis
- Tax preparation support
- Business growth insights

### Success Metrics
| Metric | Target |
|--------|--------|
| Report load time | < 1 second |
| Data accuracy | 100% |
| Export success | 100% |

---

## Functional Requirements

### FR-018-01: Dashboard
- Key metrics at a glance
- Period: This week, month, year
- Cards: Appointments, Revenue, Miles, Horses

### FR-018-02: Revenue Report
- By period, by client, by service type
- Chart visualization
- Export to CSV

### FR-018-03: Appointment Report
- Completed, cancelled, no-show counts
- Completion rate %

### FR-018-04: Mileage Report
- Total miles, tax deduction estimate
- Export for Schedule C

### FR-018-05: Due Soon Report
- Horses approaching due date
- Grouped by client

---

## Technical Implementation

```kotlin
data class DashboardStats(
    val appointmentsCompleted: Int,
    val revenueEarned: BigDecimal,
    val milesDriven: Double,
    val horsesServiced: Int
)

enum class ReportPeriod {
    THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH
}
```

---

## UI Specifications

```
┌─────────────────────────────────────────┐
│ Reports             [This Month ▼]      │
├─────────────────────────────────────────┤
│  ┌───────────┐  ┌───────────┐          │
│  │    24     │  │  $4,320   │          │
│  │ Completed │  │  Revenue  │          │
│  └───────────┘  └───────────┘          │
│  ┌───────────┐  ┌───────────┐          │
│  │   847     │  │    68     │          │
│  │   Miles   │  │  Horses   │          │
│  └───────────┘  └───────────┘          │
└─────────────────────────────────────────┘
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-018-01 | Dashboard loads < 1s | Performance test |
| AC-018-02 | Revenue totals accurate | Integration test |
| AC-018-03 | Export generates valid CSV | Integration test |
| AC-018-04 | Works offline | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-005 (Appointments) | Internal | Required |
| PRD-013 (Invoicing) | Internal | Required |
| PRD-011 (Mileage) | Internal | Required |
