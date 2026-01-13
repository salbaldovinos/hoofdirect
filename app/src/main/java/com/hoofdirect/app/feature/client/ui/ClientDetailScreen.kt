package com.hoofdirect.app.feature.client.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.designsystem.component.DueStatusBadge
import com.hoofdirect.app.designsystem.component.calculateDueStatus
import com.hoofdirect.app.designsystem.component.LoadingIndicator
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    viewModel: ClientDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToEdit: (String) -> Unit = {},
    onNavigateToHorse: (String) -> Unit = {},
    onNavigateToAddHorse: (String) -> Unit = {},
    onNavigateToAppointment: (String) -> Unit = {},
    onNavigateToNewAppointment: (String) -> Unit = {},
    onClientDeleted: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionResult) {
        when (val result = uiState.actionResult) {
            is ActionResult.ClientArchived -> {
                snackbarHostState.showSnackbar("Client archived")
                viewModel.clearActionResult()
            }
            is ActionResult.ClientRestored -> {
                snackbarHostState.showSnackbar("Client restored")
                viewModel.clearActionResult()
            }
            is ActionResult.ClientDeleted -> {
                onClientDeleted()
            }
            is ActionResult.Error -> {
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
                        text = uiState.client?.name ?: "Client",
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
                    uiState.client?.let { client ->
                        IconButton(onClick = { onNavigateToEdit(client.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (client.isActive) {
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.showArchiveDialog()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Restore") },
                                    leadingIcon = { Icon(Icons.Default.Restore, null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.restoreClient()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete permanently") },
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.client == null -> {
                Text(
                    text = "Client not found",
                    modifier = Modifier.padding(padding).padding(16.dp)
                )
            }
            else -> {
                ClientDetailContent(
                    client = uiState.client!!,
                    horses = uiState.horses,
                    upcomingAppointments = uiState.upcomingAppointments,
                    onCall = { viewModel.callClient(context) },
                    onText = { viewModel.textClient(context) },
                    onEmail = { viewModel.emailClient(context) },
                    onNavigate = { viewModel.navigateToClient(context) },
                    onSchedule = { onNavigateToNewAppointment(uiState.client!!.id) },
                    onHorseClick = onNavigateToHorse,
                    onAddHorse = { onNavigateToAddHorse(uiState.client!!.id) },
                    onAppointmentClick = onNavigateToAppointment,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    // Archive confirmation dialog
    if (uiState.showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissArchiveDialog() },
            title = { Text("Archive \"${uiState.client?.name}\"?") },
            text = {
                Text(
                    "This client and their horses will be hidden from your active list. " +
                    "You can restore them anytime from the Archived section.\n\n" +
                    "Their appointment history will be preserved."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.archiveClient() }) {
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
            title = { Text("Permanently delete \"${uiState.client?.name}\"?") },
            text = {
                Text(
                    "This will permanently delete:\n" +
                    "- This client profile\n" +
                    "- ${uiState.horses.size} horses\n\n" +
                    "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteClient() }
                ) {
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
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
private fun ClientDetailContent(
    client: ClientEntity,
    horses: List<HorseEntity>,
    upcomingAppointments: List<AppointmentEntity>,
    onCall: () -> Unit,
    onText: () -> Unit,
    onEmail: () -> Unit,
    onNavigate: () -> Unit,
    onSchedule: () -> Unit,
    onHorseClick: (String) -> Unit,
    onAddHorse: () -> Unit,
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header section with contact info
        ClientHeaderSection(
            client = client,
            modifier = Modifier.padding(16.dp)
        )

        // Quick actions
        QuickActionsRow(
            client = client,
            onCall = onCall,
            onText = onText,
            onEmail = onEmail,
            onNavigate = onNavigate,
            onSchedule = onSchedule,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Horses section
        HorsesSection(
            horses = horses,
            onHorseClick = onHorseClick,
            onAddHorse = onAddHorse,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Upcoming appointments section
        if (upcomingAppointments.isNotEmpty()) {
            UpcomingAppointmentsSection(
                appointments = upcomingAppointments,
                onAppointmentClick = onAppointmentClick,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Access notes section
        if (!client.accessNotes.isNullOrBlank()) {
            NotesSection(
                icon = Icons.Default.Key,
                title = "Access Notes",
                content = client.accessNotes,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // General notes section
        if (!client.notes.isNullOrBlank()) {
            NotesSection(
                icon = Icons.Default.Notes,
                title = "Notes",
                content = client.notes,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ClientHeaderSection(
    client: ClientEntity,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Avatar and name
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = client.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (!client.businessName.isNullOrBlank()) {
                    Text(
                        text = client.businessName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contact info
        ContactInfoRow(
            icon = Icons.Default.Phone,
            text = client.phone
        )

        if (!client.email.isNullOrBlank()) {
            ContactInfoRow(
                icon = Icons.Default.Email,
                text = client.email
            )
        }

        if (!client.address.isNullOrBlank()) {
            ContactInfoRow(
                icon = Icons.Default.LocationOn,
                text = client.address
            )
        }
    }
}

@Composable
private fun ContactInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun QuickActionsRow(
    client: ClientEntity,
    onCall: () -> Unit,
    onText: () -> Unit,
    onEmail: () -> Unit,
    onNavigate: () -> Unit,
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.Phone,
            label = "Call",
            onClick = onCall,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Default.Message,
            label = "Text",
            onClick = onText,
            modifier = Modifier.weight(1f)
        )
        if (!client.email.isNullOrBlank()) {
            QuickActionButton(
                icon = Icons.Default.Email,
                label = "Email",
                onClick = onEmail,
                modifier = Modifier.weight(1f)
            )
        }
        if (!client.address.isNullOrBlank()) {
            QuickActionButton(
                icon = Icons.Default.Navigation,
                label = "Navigate",
                onClick = onNavigate,
                modifier = Modifier.weight(1f)
            )
        }
        QuickActionButton(
            icon = Icons.Default.CalendarMonth,
            label = "Schedule",
            onClick = onSchedule,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun HorsesSection(
    horses: List<HorseEntity>,
    onHorseClick: (String) -> Unit,
    onAddHorse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Horses (${horses.size})",
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onAddHorse) {
                Icon(Icons.Default.Add, contentDescription = "Add horse")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (horses.isEmpty()) {
                Text(
                    text = "No horses yet. Add a horse to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                horses.forEach { horse ->
                    HorseListItem(
                        horse = horse,
                        onClick = { onHorseClick(horse.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HorseListItem(
    horse: HorseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = horse.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = listOfNotNull(horse.breed, horse.defaultServiceType).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        horse.nextDueDate?.let { nextDueDate ->
            DueStatusBadge(status = calculateDueStatus(nextDueDate))
        }
    }
}

@Composable
private fun UpcomingAppointmentsSection(
    appointments: List<AppointmentEntity>,
    onAppointmentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Upcoming (${appointments.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            appointments.forEach { appointment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppointmentClick(appointment.id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appointment.date.format(
                                DateTimeFormatter.ofPattern("EEE, MMM d")
                            ) + " at " + appointment.startTime.format(
                                DateTimeFormatter.ofPattern("h:mm a")
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$${appointment.totalPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
