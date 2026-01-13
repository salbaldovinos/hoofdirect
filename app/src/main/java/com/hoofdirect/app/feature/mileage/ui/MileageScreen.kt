package com.hoofdirect.app.feature.mileage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.MileageLogEntity
import com.hoofdirect.app.core.database.entity.MileagePurpose
import com.hoofdirect.app.designsystem.component.LoadingIndicator
import com.hoofdirect.app.feature.mileage.data.MileageRepository
import com.hoofdirect.app.feature.mileage.data.MileageSummary
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageScreen(
    viewModel: MileageViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mileage Tracking") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddSheet() }) {
                Icon(Icons.Default.Add, contentDescription = "Add trip")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    YearSelector(
                        selectedYear = uiState.selectedYear,
                        onYearSelected = { viewModel.selectYear(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    uiState.summary?.let { summary ->
                        AnnualSummaryCard(
                            summary = summary,
                            year = uiState.selectedYear,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (uiState.trips.isEmpty()) {
                    item {
                        EmptyTripsMessage(
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    val groupedTrips = uiState.trips.groupBy { it.date }
                    groupedTrips.forEach { (date, trips) ->
                        item {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(trips, key = { it.id }) { trip ->
                            TripCard(
                                trip = trip,
                                onClick = { viewModel.editTrip(trip) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Add/Edit Sheet
    if (uiState.showAddSheet || uiState.editingTrip != null) {
        AddTripSheet(
            trip = uiState.editingTrip,
            isSaving = uiState.isSaving,
            onDismiss = {
                if (uiState.showAddSheet) viewModel.dismissAddSheet()
                else viewModel.dismissEdit()
            },
            onSave = { date, startAddress, endAddress, miles, purpose, notes ->
                viewModel.saveTrip(date, startAddress, endAddress, miles, purpose, notes)
            },
            onDelete = uiState.editingTrip?.let { trip ->
                { viewModel.deleteTrip(trip) }
            }
        )
    }
}

@Composable
fun YearSelector(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentYear = Year.now().value
    val years = (currentYear downTo currentYear - 2).toList()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        years.forEach { year ->
            FilterChip(
                selected = year == selectedYear,
                onClick = { onYearSelected(year) },
                label = { Text(year.toString()) }
            )
        }
    }
}

@Composable
fun AnnualSummaryCard(
    summary: MileageSummary,
    year: Int,
    modifier: Modifier = Modifier
) {
    val irsRate = MileageRepository.getIrsRate(year)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$year Tax Summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Total Miles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "%.1f".format(summary.totalMiles),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Est. Deduction",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$${"%,.2f".format(summary.estimatedDeduction)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${summary.tripCount} trips logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "IRS Rate: \$${"%.2f".format(irsRate)}/mile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun TripCard(
    trip: MileageLogEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val purpose = try {
        MileagePurpose.valueOf(trip.purpose)
    } catch (e: Exception) {
        MileagePurpose.OTHER
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = purpose.displayName,
                    style = MaterialTheme.typography.titleMedium
                )

                if (trip.startAddress != null || trip.endAddress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            trip.startAddress?.let { append(it) }
                            if (trip.startAddress != null && trip.endAddress != null) {
                                append(" → ")
                            }
                            trip.endAddress?.let { append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                trip.notes?.let { notes ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "${"%.1f".format(trip.miles)} mi",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun EmptyTripsMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(16.dp)
        )
        Text(
            text = "No trips logged",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap + to add your first trip",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTripSheet(
    trip: MileageLogEntity?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        date: LocalDate,
        startAddress: String?,
        endAddress: String?,
        miles: Double,
        purpose: MileagePurpose,
        notes: String?
    ) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var date by remember(trip) { mutableStateOf(trip?.date ?: LocalDate.now()) }
    var startAddress by remember(trip) { mutableStateOf(trip?.startAddress ?: "") }
    var endAddress by remember(trip) { mutableStateOf(trip?.endAddress ?: "") }
    var milesText by remember(trip) { mutableStateOf(trip?.miles?.toString() ?: "") }
    var purpose by remember(trip) {
        mutableStateOf(
            try {
                MileagePurpose.valueOf(trip?.purpose ?: MileagePurpose.CLIENT_VISIT.name)
            } catch (e: Exception) {
                MileagePurpose.CLIENT_VISIT
            }
        )
    }
    var notes by remember(trip) { mutableStateOf(trip?.notes ?: "") }

    var milesError by remember { mutableStateOf<String?>(null) }
    var purposeExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // DatePicker state - initialize with current date in milliseconds
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Allow dates up to today (no future dates for mileage logging)
                val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                return utcTimeMillis <= today + 86400000 // Add one day buffer for timezone issues
            }
        }
    )

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = if (trip != null) "Edit Trip" else "Add Trip",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Date picker field
            OutlinedTextField(
                value = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                onValueChange = { },
                label = { Text("Date") },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Select date"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Miles input
            OutlinedTextField(
                value = milesText,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() || c == '.' }
                    if (filtered.count { c -> c == '.' } <= 1) {
                        milesText = filtered
                        milesError = null
                    }
                },
                label = { Text("Miles") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = milesError != null,
                supportingText = milesError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Purpose dropdown
            ExposedDropdownMenuBox(
                expanded = purposeExpanded,
                onExpandedChange = { purposeExpanded = it }
            ) {
                OutlinedTextField(
                    value = purpose.displayName,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Purpose") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = purposeExpanded,
                    onDismissRequest = { purposeExpanded = false }
                ) {
                    MileagePurpose.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                purpose = option
                                purposeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start address
            OutlinedTextField(
                value = startAddress,
                onValueChange = { startAddress = it.take(100) },
                label = { Text("From (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // End address
            OutlinedTextField(
                value = endAddress,
                onValueChange = { endAddress = it.take(100) },
                label = { Text("To (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(200) },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val miles = milesText.toDoubleOrNull()
                        if (miles == null || miles <= 0) {
                            milesError = "Enter valid miles"
                            return@Button
                        }

                        onSave(
                            date,
                            startAddress.ifBlank { null },
                            endAddress.ifBlank { null },
                            miles,
                            purpose,
                            notes.ifBlank { null }
                        )
                    },
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Saving..." else "Save")
                }
            }
        }
    }
}
