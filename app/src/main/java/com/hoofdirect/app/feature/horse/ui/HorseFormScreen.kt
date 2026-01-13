package com.hoofdirect.app.feature.horse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.HorseTemperament
import com.hoofdirect.app.designsystem.component.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorseFormScreen(
    viewModel: HorseFormViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onSaveSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onSaveSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditMode) "Edit Horse" else "New Horse")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator()
        } else {
            HorseFormContent(
                formState = uiState.formState,
                isSaving = uiState.isSaving,
                serviceTypes = uiState.serviceTypes,
                temperaments = uiState.temperaments,
                onNameChange = viewModel::updateName,
                onBreedChange = viewModel::updateBreed,
                onColorChange = viewModel::updateColor,
                onAgeChange = viewModel::updateAge,
                onTemperamentChange = viewModel::updateTemperament,
                onServiceTypeChange = viewModel::updateDefaultServiceType,
                onUseDefaultCycleChange = viewModel::updateUseDefaultCycle,
                onCycleWeeksChange = viewModel::updateShoeingCycleWeeks,
                onMedicalNotesChange = viewModel::updateMedicalNotes,
                onSave = viewModel::validateAndSave,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun HorseFormContent(
    formState: HorseFormState,
    isSaving: Boolean,
    serviceTypes: List<String>,
    temperaments: List<HorseTemperament>,
    onNameChange: (String) -> Unit,
    onBreedChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onTemperamentChange: (String?) -> Unit,
    onServiceTypeChange: (String?) -> Unit,
    onUseDefaultCycleChange: (Boolean) -> Unit,
    onCycleWeeksChange: (Int) -> Unit,
    onMedicalNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Basic info section
        Text(
            text = "Basic Information",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = formState.name,
            onValueChange = onNameChange,
            label = { Text("Name *") },
            isError = formState.nameError != null,
            supportingText = formState.nameError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = formState.breed,
                onValueChange = onBreedChange,
                label = { Text("Breed") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            OutlinedTextField(
                value = formState.color,
                onValueChange = onColorChange,
                label = { Text("Color") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = formState.age,
                onValueChange = onAgeChange,
                label = { Text("Age (years)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            TemperamentDropdown(
                selectedTemperament = formState.temperament,
                temperaments = temperaments,
                onTemperamentChange = onTemperamentChange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Service settings section
        Text(
            text = "Service Settings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        ServiceTypeDropdown(
            selectedServiceType = formState.defaultServiceType,
            serviceTypes = serviceTypes,
            onServiceTypeChange = onServiceTypeChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Shoeing cycle
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Shoeing Cycle",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = formState.useDefaultCycle,
                        onCheckedChange = onUseDefaultCycleChange
                    )
                    Text(
                        text = "Use default cycle (6 weeks)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!formState.useDefaultCycle) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${formState.shoeingCycleWeeks} weeks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Slider(
                        value = formState.shoeingCycleWeeks.toFloat(),
                        onValueChange = { onCycleWeeksChange(it.toInt()) },
                        valueRange = 4f..16f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "4 weeks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "16 weeks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Medical notes section
        Text(
            text = "Medical Notes",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = formState.medicalNotes,
            onValueChange = onMedicalNotesChange,
            label = { Text("Medical Notes") },
            placeholder = { Text("Previous founder, lameness history, special care instructions...") },
            supportingText = { Text("${formState.medicalNotes.length}/2000") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Save button
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(20.dp)
                        .width(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(if (isSaving) "Saving..." else "Save Horse")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemperamentDropdown(
    selectedTemperament: String?,
    temperaments: List<HorseTemperament>,
    onTemperamentChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = temperaments.find { it.name == selectedTemperament }?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Temperament") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onTemperamentChange(null)
                    expanded = false
                }
            )
            temperaments.forEach { temperament ->
                DropdownMenuItem(
                    text = { Text(temperament.displayName) },
                    onClick = {
                        onTemperamentChange(temperament.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceTypeDropdown(
    selectedServiceType: String?,
    serviceTypes: List<String>,
    onServiceTypeChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedServiceType ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Default Service Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onServiceTypeChange(null)
                    expanded = false
                }
            )
            serviceTypes.forEach { serviceType ->
                DropdownMenuItem(
                    text = { Text(serviceType) },
                    onClick = {
                        onServiceTypeChange(serviceType)
                        expanded = false
                    }
                )
            }
        }
    }
}
