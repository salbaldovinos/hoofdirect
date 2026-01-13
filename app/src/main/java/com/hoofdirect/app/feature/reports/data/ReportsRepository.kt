package com.hoofdirect.app.feature.reports.data

import com.hoofdirect.app.core.database.dao.AppointmentDao
import com.hoofdirect.app.core.database.dao.InvoiceDao
import com.hoofdirect.app.core.database.dao.MileageLogDao
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

enum class ReportPeriod(val displayName: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

data class DashboardStats(
    val appointmentsCompleted: Int = 0,
    val revenueEarned: Double = 0.0,
    val milesDriven: Double = 0.0,
    val horsesServiced: Int = 0,
    val periodLabel: String = ""
)

@Singleton
class ReportsRepository @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val invoiceDao: InvoiceDao,
    private val mileageLogDao: MileageLogDao
) {

    suspend fun getDashboardStats(userId: String, period: ReportPeriod): DashboardStats {
        val today = LocalDate.now()
        val (startDate, endDate, periodLabel) = when (period) {
            ReportPeriod.THIS_WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                val end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                Triple(start, end, "This Week")
            }
            ReportPeriod.THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                val end = today.with(TemporalAdjusters.lastDayOfMonth())
                Triple(start, end, today.month.name.lowercase().replaceFirstChar { it.uppercase() })
            }
            ReportPeriod.THIS_YEAR -> {
                val start = LocalDate.of(today.year, 1, 1)
                val end = LocalDate.of(today.year, 12, 31)
                Triple(start, end, today.year.toString())
            }
        }

        val appointmentsCompleted = appointmentDao.getCompletedCountForRange(userId, startDate, endDate)
        val revenueEarned = invoiceDao.getTotalRevenue(userId, startDate, endDate) ?: 0.0
        val milesDriven = mileageLogDao.getTotalMilesForRange(userId, startDate, endDate)
        val horsesServiced = appointmentDao.getUniqueHorsesServicedForRange(userId, startDate, endDate)

        return DashboardStats(
            appointmentsCompleted = appointmentsCompleted,
            revenueEarned = revenueEarned,
            milesDriven = milesDriven,
            horsesServiced = horsesServiced,
            periodLabel = periodLabel
        )
    }
}
