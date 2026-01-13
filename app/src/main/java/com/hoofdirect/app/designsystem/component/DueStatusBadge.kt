package com.hoofdirect.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.designsystem.theme.HdStatusCancelled
import com.hoofdirect.app.designsystem.theme.HdStatusCompleted
import com.hoofdirect.app.designsystem.theme.HdStatusConfirmed
import com.hoofdirect.app.designsystem.theme.HdStatusNoShow
import com.hoofdirect.app.designsystem.theme.HdStatusScheduled
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

sealed class DueStatus(val daysUntil: Int) {
    class Ok(days: Int) : DueStatus(days)
    class DueSoon(days: Int) : DueStatus(days)
    class DueToday : DueStatus(0)
    class Overdue(daysOverdue: Int) : DueStatus(-daysOverdue)
    class NeverServiced : DueStatus(Int.MAX_VALUE)
}

fun calculateDueStatus(nextDueDate: LocalDate?): DueStatus {
    if (nextDueDate == null) return DueStatus.NeverServiced()

    val today = LocalDate.now()
    val daysUntilDue = ChronoUnit.DAYS.between(today, nextDueDate).toInt()

    return when {
        daysUntilDue < 0 -> DueStatus.Overdue(abs(daysUntilDue))
        daysUntilDue == 0 -> DueStatus.DueToday()
        daysUntilDue <= 7 -> DueStatus.DueSoon(daysUntilDue)
        else -> DueStatus.Ok(daysUntilDue)
    }
}

@Composable
fun DueStatusBadge(
    status: DueStatus,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (status) {
        is DueStatus.Ok -> HdStatusConfirmed to "Due in ${status.daysUntil} days"
        is DueStatus.DueSoon -> HdStatusScheduled to "Due in ${status.daysUntil} days"
        is DueStatus.DueToday -> HdStatusCancelled to "Due today"
        is DueStatus.Overdue -> HdStatusNoShow to "${abs(status.daysUntil)} days overdue"
        is DueStatus.NeverServiced -> HdStatusCompleted to "Never serviced"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

