package com.hoofdirect.app.feature.appointment.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.AppointmentStatus
import com.hoofdirect.app.designsystem.component.AppointmentStatusBadge
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentDetailScreen(
    viewModel: AppointmentDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToClient: (String) -> Unit,
    onAppointmentDeleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate on delete
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onAppointmentDeleted()
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Show success snackbar
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }

    // Cancel dialog
    if (uiState.showCancelDialog) {
        CancelAppointmentDialog(
            onDismiss = { viewModel.hideCancelDialog() },
            onConfirm = { reason -> viewModel.cancelAppointment(reason) }
        )
    }

    // Delete dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Appointment") },
            text = { Text("Are you sure you want to delete this appointment? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAppointment() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appointment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.appointment != null) {
                        IconButton(onClick = { onNavigateToEdit(uiState.appointment!!.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                        }
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.appointment == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Appointment not found")
            }
        } else {
            val appointment = uiState.appointment!!
            val client = uiState.client
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Status Badge
                AppointmentStatusBadge(status = appointment.status)

                Spacer(modifier = Modifier.height(16.dp))

                // Client Card
                if (client != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onNavigateToClient(client.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = client.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (client.businessName != null) {
                                    Text(
                                        text = client.businessName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date & Time
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = appointment.date.format(dateFormatter),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${appointment.startTime.format(timeFormatter)} (${appointment.durationMinutes} min)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        if (appointment.address != null) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = appointment.address,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Horses & Services
                Text(
                    text = "Services",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        uiState.horseDetails.forEachIndexed { index, detail ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = detail.horse?.name ?: "Unknown Horse",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = detail.appointmentHorse.serviceType,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "$${detail.appointmentHorse.price}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "$${appointment.totalPrice}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Notes
                if (!appointment.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = appointment.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons based on status
                val status = try {
                    AppointmentStatus.valueOf(appointment.status)
                } catch (e: Exception) {
                    AppointmentStatus.SCHEDULED
                }

                when (status) {
                    AppointmentStatus.SCHEDULED -> {
                        Button(
                            onClick = { viewModel.confirmAppointment() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isProcessing
                        ) {
                            Text("Confirm Appointment")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.showCancelDialog() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel Appointment")
                        }
                    }

                    AppointmentStatus.CONFIRMED -> {
                        Button(
                            onClick = { viewModel.startAppointment() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isProcessing
                        ) {
                            Text("Start Service")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.showCancelDialog() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel Appointment")
                        }
                    }

                    AppointmentStatus.IN_PROGRESS -> {
                        Button(
                            onClick = { viewModel.completeAppointment() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isProcessing
                        ) {
                            Text("Complete Service")
                        }
                    }

                    AppointmentStatus.COMPLETED -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = "This appointment has been completed.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    AppointmentStatus.CANCELLED -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "This appointment was cancelled.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!appointment.cancellationReason.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Reason: ${appointment.cancellationReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    AppointmentStatus.NO_SHOW -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "Client was marked as no-show.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CancelAppointmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Appointment") },
        text = {
            Column {
                Text("Are you sure you want to cancel this appointment?")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.ifBlank { null }) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancel Appointment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Go Back")
            }
        }
    )
}
