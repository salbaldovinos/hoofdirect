package com.hoofdirect.app.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.core.database.entity.AppointmentStatus
import com.hoofdirect.app.designsystem.theme.HdStatusCancelled
import com.hoofdirect.app.designsystem.theme.HdStatusCompleted
import com.hoofdirect.app.designsystem.theme.HdStatusConfirmed
import com.hoofdirect.app.designsystem.theme.HdStatusNoShow
import com.hoofdirect.app.designsystem.theme.HdStatusScheduled

@Composable
fun AppointmentStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (color, displayText) = when (status) {
        AppointmentStatus.SCHEDULED.name -> HdStatusScheduled to "Scheduled"
        AppointmentStatus.CONFIRMED.name -> HdStatusConfirmed to "Confirmed"
        AppointmentStatus.IN_PROGRESS.name -> HdStatusConfirmed to "In Progress"
        AppointmentStatus.COMPLETED.name -> HdStatusCompleted to "Completed"
        AppointmentStatus.CANCELLED.name -> HdStatusCancelled to "Cancelled"
        AppointmentStatus.NO_SHOW.name -> HdStatusNoShow to "No Show"
        else -> HdStatusScheduled to status
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
