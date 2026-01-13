package com.hoofdirect.app.feature.appointment.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentFormScreen(
    viewModel: AppointmentFormViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Navigate on save success
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            val dateStr = uiState.formState.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
            android.widget.Toast.makeText(
                context,
                "Appointment saved for $dateStr",
                android.widget.Toast.LENGTH_LONG
            ).show()
            onSaveSuccess()
        }
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
                title = { Text(if (uiState.isEditMode) "Edit Appointment" else "New Appointment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Client Selection
                ClientSelector(
                    selectedClient = uiState.selectedClient,
                    availableClients = uiState.availableClients,
                    onClientSelected = { viewModel.selectClient(it) },
                    error = uiState.formState.clientError,
                    enabled = !uiState.isEditMode
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Date & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DateSelector(
                        date = uiState.formState.date,
                        onDateSelected = { viewModel.updateDate(it) },
                        modifier = Modifier.weight(1f)
                    )

                    TimeSelector(
                        time = uiState.formState.startTime,
                        onTimeSelected = { viewModel.updateStartTime(it) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration
                DurationSelector(
                    duration = uiState.formState.durationMinutes,
                    onDurationSelected = { viewModel.updateDuration(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Horse Selection
                Text(
                    text = "Select Horses & Services",
                    style = MaterialTheme.typography.titleMedium
                )

                if (uiState.formState.horseError != null) {
                    Text(
                        text = uiState.formState.horseError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.selectedClient == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Select a client to see their horses",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (uiState.isLoadingHorses) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else if (uiState.horseServices.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "No horses found for this client. Add horses first.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    uiState.horseServices.forEach { horseService ->
                        HorseServiceCard(
                            horseService = horseService,
                            onToggleSelection = { viewModel.toggleHorseSelection(horseService.horse.id) },
                            onServiceTypeChanged = { viewModel.updateHorseServiceType(horseService.horse.id, it) },
                            onPriceChanged = { viewModel.updateHorsePrice(horseService.horse.id, it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes
                OutlinedTextField(
                    value = uiState.formState.notes,
                    onValueChange = { viewModel.updateNotes(it) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Total Price
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$${uiState.totalPrice}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                Button(
                    onClick = { viewModel.validateAndSave() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text(if (uiState.isEditMode) "Update Appointment" else "Create Appointment")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ClientSelector(
    selectedClient: com.hoofdirect.app.core.database.entity.ClientEntity?,
    availableClients: List<com.hoofdirect.app.core.database.entity.ClientEntity>,
    onClientSelected: (com.hoofdirect.app.core.database.entity.ClientEntity) -> Unit,
    error: String?,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Client",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (selectedClient != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = selectedClient?.name ?: "Select a client",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedClient != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (enabled) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                availableClients.forEach { client ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(client.name)
                                if (client.businessName != null) {
                                    Text(
                                        text = client.businessName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onClientSelected(client)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    OutlinedCard(
        modifier = modifier.clickable {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = date.format(dateFormatter),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun TimeSelector(
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    OutlinedCard(
        modifier = modifier.clickable {
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    onTimeSelected(LocalTime.of(hourOfDay, minute))
                },
                time.hour,
                time.minute,
                false
            ).show()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = time.format(timeFormatter),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun DurationSelector(
    duration: Int,
    onDurationSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val durations = listOf(30, 45, 60, 90, 120)

    Column {
        Text(
            text = "Duration",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$duration minutes",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand"
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                durations.forEach { d ->
                    DropdownMenuItem(
                        text = { Text("$d minutes") },
                        onClick = {
                            onDurationSelected(d)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HorseServiceCard(
    horseService: HorseServiceItem,
    onToggleSelection: () -> Unit,
    onServiceTypeChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit
) {
    val serviceTypes = listOf("Full Trim", "Front Trim", "Full Shoe", "Front Shoe", "Reset", "Other")
    var serviceDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (horseService.isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = horseService.isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = horseService.horse.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (horseService.horse.breed != null) {
                        Text(
                            text = horseService.horse.breed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (horseService.isSelected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Service Type Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = horseService.serviceType,
                            onValueChange = { },
                            label = { Text("Service") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { serviceDropdownExpanded = true },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { serviceDropdownExpanded = true }
                                )
                            }
                        )

                        DropdownMenu(
                            expanded = serviceDropdownExpanded,
                            onDismissRequest = { serviceDropdownExpanded = false }
                        ) {
                            serviceTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        onServiceTypeChanged(type)
                                        serviceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Price
                    OutlinedTextField(
                        value = horseService.price,
                        onValueChange = { onPriceChanged(it) },
                        label = { Text("Price") },
                        modifier = Modifier.weight(0.6f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix = { Text("$") },
                        singleLine = true
                    )
                }
            }
        }
    }
}
