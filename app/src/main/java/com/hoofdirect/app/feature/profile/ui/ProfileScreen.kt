package com.hoofdirect.app.feature.profile.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Profile saved successfully")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!uiState.isEditing && !uiState.isLoading) {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit"
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (uiState.isEditing) {
                    EditProfileContent(
                        uiState = uiState,
                        onBusinessNameChange = viewModel::updateBusinessName,
                        onPhoneChange = viewModel::updatePhone,
                        onAddressChange = viewModel::updateAddress,
                        onServiceRadiusChange = viewModel::updateServiceRadius,
                        onDefaultDurationChange = viewModel::updateDefaultDuration,
                        onDefaultCycleChange = viewModel::updateDefaultCycle,
                        onSave = viewModel::saveProfile,
                        onCancel = viewModel::cancelEditing
                    )
                } else {
                    ViewProfileContent(uiState = uiState)
                }
            }
        }
    }
}

@Composable
private fun ViewProfileContent(uiState: ProfileUiState) {
    val user = uiState.user

    // Account Info Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileInfoRow(
                icon = Icons.Default.Email,
                label = "Email",
                value = user?.email ?: "Not set"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ProfileInfoRow(
                icon = Icons.Default.Person,
                label = "Subscription",
                value = user?.subscriptionTier?.replaceFirstChar { it.uppercase() } ?: "Free"
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Business Info Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Business Information",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileInfoRow(
                icon = Icons.Default.Business,
                label = "Business Name",
                value = user?.businessName ?: "Not set"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ProfileInfoRow(
                icon = Icons.Default.Phone,
                label = "Phone",
                value = user?.phone ?: "Not set"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ProfileInfoRow(
                icon = Icons.Default.LocationOn,
                label = "Address",
                value = user?.address ?: "Not set"
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Service Defaults Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Service Defaults",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Service Radius",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${user?.serviceRadiusMiles ?: 50} miles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Default Duration",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${user?.defaultDurationMinutes ?: 45} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Default Shoeing Cycle",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${user?.defaultCycleWeeks ?: 6} weeks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EditProfileContent(
    uiState: ProfileUiState,
    onBusinessNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onServiceRadiusChange: (Int) -> Unit,
    onDefaultDurationChange: (Int) -> Unit,
    onDefaultCycleChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Text(
        text = "Business Information",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.businessName,
        onValueChange = onBusinessNameChange,
        label = { Text("Business Name") },
        leadingIcon = {
            Icon(Icons.Default.Business, contentDescription = null)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.phone,
        onValueChange = onPhoneChange,
        label = { Text("Phone Number") },
        leadingIcon = {
            Icon(Icons.Default.Phone, contentDescription = null)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.address,
        onValueChange = onAddressChange,
        label = { Text("Home Address") },
        leadingIcon = {
            Icon(Icons.Default.LocationOn, contentDescription = null)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Service Defaults",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Service Radius
    Text(
        text = "Service Radius",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = uiState.serviceRadiusMiles.toFloat(),
            onValueChange = { onServiceRadiusChange(it.toInt()) },
            valueRange = 10f..100f,
            steps = 17,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${uiState.serviceRadiusMiles} mi",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Default Duration
    Text(
        text = "Default Appointment Duration",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = uiState.defaultDurationMinutes.toFloat(),
            onValueChange = { onDefaultDurationChange(it.toInt()) },
            valueRange = 30f..90f,
            steps = 3,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${uiState.defaultDurationMinutes} min",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Default Cycle
    Text(
        text = "Default Shoeing Cycle",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = uiState.defaultCycleWeeks.toFloat(),
            onValueChange = { onDefaultCycleChange(it.toInt()) },
            valueRange = 4f..12f,
            steps = 7,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${uiState.defaultCycleWeeks} wks",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Action Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            enabled = !uiState.isSaving
        ) {
            Text("Cancel")
        }

        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Save")
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
}
