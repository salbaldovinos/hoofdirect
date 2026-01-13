package com.hoofdirect.app.feature.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.designsystem.component.AppointmentStatusBadge
import com.hoofdirect.app.designsystem.component.EmptyState
import com.hoofdirect.app.designsystem.component.LoadingIndicator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onNavigateToAppointment: (String) -> Unit = {},
    onNavigateToNewAppointment: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh data when screen appears
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                actions = {
                    IconButton(onClick = onNavigateToNewAppointment) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "New appointment"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewAppointment) {
                Icon(Icons.Default.Add, contentDescription = "New appointment")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Week navigation
            WeekHeader(
                selectedDate = uiState.selectedDate,
                appointmentCounts = uiState.weekAppointmentCounts,
                onPreviousWeek = { viewModel.previousWeek() },
                onNextWeek = { viewModel.nextWeek() },
                onDateSelected = { viewModel.selectDate(it) }
            )

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.appointments.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Schedule,
                        title = "No appointments today",
                        message = "No appointments scheduled for ${uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))}",
                        actionLabel = "Add New Appointment",
                        onAction = onNavigateToNewAppointment
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.appointments) { appointment ->
                            AppointmentCard(
                                appointment = appointment,
                                clientName = uiState.clientNames[appointment.clientId] ?: "Unknown",
                                onClick = { onNavigateToAppointment(appointment.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekHeader(
    selectedDate: LocalDate,
    appointmentCounts: Map<LocalDate, Int> = emptyMap(),
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous week")
            }

            Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(onClick = onNextWeek) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next week")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0..6) {
                val date = startOfWeek.plusDays(i.toLong())
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                val hasAppointments = appointmentCounts.getOrDefault(date, 0) > 0

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onDateSelected(date) }
                        .padding(4.dp)
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    // Appointment indicator dot
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (hasAppointments) {
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(
    appointment: AppointmentEntity,
    clientName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = appointment.startTime.format(DateTimeFormatter.ofPattern("h:mm")),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = appointment.startTime.format(DateTimeFormatter.ofPattern("a")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${appointment.durationMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                AppointmentStatusBadge(status = appointment.status)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${appointment.totalPrice}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}
