package com.hoofdirect.app.feature.horse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.core.database.entity.HorseTemperament
import com.hoofdirect.app.designsystem.component.LoadingIndicator
import com.hoofdirect.app.designsystem.theme.HdStatusCancelled
import com.hoofdirect.app.designsystem.theme.HdStatusCompleted
import com.hoofdirect.app.designsystem.theme.HdStatusConfirmed
import com.hoofdirect.app.designsystem.theme.HdStatusNoShow
import com.hoofdirect.app.designsystem.theme.HdStatusScheduled
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorseDetailScreen(
    viewModel: HorseDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToEdit: (String) -> Unit = {},
    onNavigateToClient: (String) -> Unit = {},
    onNavigateToNewAppointment: (String) -> Unit = {},
    onHorseDeleted: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionResult) {
        when (val result = uiState.actionResult) {
            is HorseActionResult.HorseArchived -> {
                snackbarHostState.showSnackbar("Horse archived")
                viewModel.clearActionResult()
                onNavigateBack()
            }
            is HorseActionResult.HorseDeleted -> {
                onHorseDeleted()
            }
            is HorseActionResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearActionResult()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.horse?.name ?: "Horse",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.horse?.let { horse ->
                        IconButton(onClick = { onNavigateToEdit(horse.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.showArchiveDialog()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.showDeleteDialog()
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.horse == null -> {
                Text(
                    text = "Horse not found",
                    modifier = Modifier.padding(padding).padding(16.dp)
                )
            }
            else -> {
                HorseDetailContent(
                    horse = uiState.horse!!,
                    clientName = uiState.client?.name,
                    dueStatus = uiState.dueStatus,
                    onNavigateToClient = { uiState.client?.let { onNavigateToClient(it.id) } },
                    onScheduleAppointment = { onNavigateToNewAppointment(uiState.horse!!.id) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    // Archive confirmation dialog
    if (uiState.showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissArchiveDialog() },
            title = { Text("Archive \"${uiState.horse?.name}\"?") },
            text = {
                Text(
                    "This horse will be hidden from your active lists but their service history will be preserved."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.archiveHorse() }) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissArchiveDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete \"${uiState.horse?.name}\"?") },
            text = {
                Text("This will permanently delete this horse and all their service history. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteHorse() }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HorseDetailContent(
    horse: HorseEntity,
    clientName: String?,
    dueStatus: DueStatus,
    onNavigateToClient: () -> Unit,
    onScheduleAppointment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with avatar
        HorseHeader(
            horse = horse,
            modifier = Modifier.padding(16.dp)
        )

        // Owner section
        if (clientName != null) {
            OwnerSection(
                clientName = clientName,
                onClick = onNavigateToClient,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onScheduleAppointment,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Horse info section
        HorseInfoSection(
            horse = horse,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Service settings section
        ServiceSettingsSection(
            horse = horse,
            dueStatus = dueStatus,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Medical notes section
        if (!horse.medicalNotes.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            MedicalNotesSection(
                notes = horse.medicalNotes,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HorseHeader(
    horse: HorseEntity,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = horse.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = horse.name,
                style = MaterialTheme.typography.headlineSmall
            )
            if (!horse.breed.isNullOrBlank() || !horse.color.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(horse.breed, horse.color).joinToString(" • "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OwnerSection(
    clientName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Owner",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HorseInfoSection(
    horse: HorseEntity,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (!horse.breed.isNullOrBlank()) {
                    InfoRow(label = "Breed", value = horse.breed)
                }
                if (!horse.color.isNullOrBlank()) {
                    InfoRow(label = "Color", value = horse.color)
                }
                if (horse.age != null) {
                    InfoRow(label = "Age", value = "${horse.age} years")
                }
                if (!horse.temperament.isNullOrBlank()) {
                    val displayName = HorseTemperament.entries
                        .find { it.name == horse.temperament }
                        ?.displayName ?: horse.temperament
                    InfoRow(label = "Temperament", value = displayName)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ServiceSettingsSection(
    horse: HorseEntity,
    dueStatus: DueStatus,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Service Settings",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (!horse.defaultServiceType.isNullOrBlank()) {
                    InfoRow(label = "Default Service", value = horse.defaultServiceType)
                }

                if (horse.shoeingCycleWeeks != null) {
                    InfoRow(label = "Cycle", value = "${horse.shoeingCycleWeeks} weeks")
                }

                if (horse.lastServiceDate != null) {
                    InfoRow(
                        label = "Last Service",
                        value = horse.lastServiceDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Due status badge
                DueStatusBadge(
                    dueStatus = dueStatus,
                    nextDueDate = horse.nextDueDate
                )
            }
        }
    }
}

@Composable
private fun DueStatusBadge(
    dueStatus: DueStatus,
    nextDueDate: java.time.LocalDate?,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (dueStatus) {
        is DueStatus.Ok -> HdStatusCompleted to "Due in ${dueStatus.daysUntil} days"
        is DueStatus.DueSoon -> HdStatusScheduled to "Due in ${dueStatus.daysUntil} days"
        is DueStatus.DueToday -> HdStatusConfirmed to "Due today"
        is DueStatus.Overdue -> HdStatusNoShow to "${dueStatus.daysOverdue} days overdue"
        is DueStatus.NeverServiced -> HdStatusCancelled to "Never serviced"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = color
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Next Due",
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
                Text(
                    text = if (nextDueDate != null) {
                        nextDueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) + " ($text)"
                    } else {
                        text
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun MedicalNotesSection(
    notes: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MedicalServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Medical Notes",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
