package com.hoofdirect.app.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.R
import com.hoofdirect.app.feature.auth.domain.PhoneValidator
import com.hoofdirect.app.feature.auth.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onProfileComplete: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val focusManager = LocalFocusManager.current

    var businessName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var serviceRadius by remember { mutableFloatStateOf(50f) }
    var defaultDuration by remember { mutableFloatStateOf(45f) }
    var defaultCycle by remember { mutableFloatStateOf(6f) }

    var businessNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_setup)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tell us about your business",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "This information helps us optimize your routes and generate invoices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Business Name
            OutlinedTextField(
                value = businessName,
                onValueChange = {
                    businessName = it
                    businessNameError = if (it.isBlank()) "Business name is required" else null
                },
                label = { Text(stringResource(R.string.business_name)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null
                    )
                },
                isError = businessNameError != null,
                supportingText = businessNameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    phoneError = PhoneValidator.getError(it)
                },
                label = { Text(stringResource(R.string.phone_number)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null
                    )
                },
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Address
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.home_address)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null
                    )
                },
                placeholder = { Text("Start typing to search...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Your home address is used as the starting point for route optimization.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Service Defaults section
            Text(
                text = "Service Defaults",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Service Radius slider
            Text(
                text = stringResource(R.string.service_radius),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = serviceRadius,
                    onValueChange = { serviceRadius = it },
                    valueRange = 10f..100f,
                    steps = 17, // 5-mile increments
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.miles, serviceRadius.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Default Duration slider
            Text(
                text = stringResource(R.string.default_duration),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = defaultDuration,
                    onValueChange = { defaultDuration = it },
                    valueRange = 30f..90f,
                    steps = 3, // 30, 45, 60, 90
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.minutes, defaultDuration.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Default Cycle slider
            Text(
                text = stringResource(R.string.default_cycle),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = defaultCycle,
                    onValueChange = { defaultCycle = it },
                    valueRange = 4f..12f,
                    steps = 7, // 1-week increments
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.weeks, defaultCycle.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    // Validate
                    var hasError = false

                    if (businessName.isBlank()) {
                        businessNameError = "Business name is required"
                        hasError = true
                    }

                    if (phone.isNotBlank() && !PhoneValidator.validate(phone)) {
                        phoneError = "Please enter a valid phone number"
                        hasError = true
                    }

                    if (!hasError) {
                        isLoading = true
                        saveError = null
                        // Update user profile
                        val currentUser = authState.user
                        if (currentUser != null) {
                            val updatedUser = currentUser.copy(
                                businessName = businessName,
                                phone = PhoneValidator.format(phone),
                                address = address,
                                serviceRadiusMiles = serviceRadius.toInt(),
                                defaultDurationMinutes = defaultDuration.toInt(),
                                defaultCycleWeeks = defaultCycle.toInt(),
                                profileCompleted = true
                            )
                            viewModel.updateUserProfile(
                                user = updatedUser,
                                onSuccess = {
                                    isLoading = false
                                    onProfileComplete()
                                },
                                onError = { error ->
                                    isLoading = false
                                    saveError = error
                                }
                            )
                        } else {
                            isLoading = false
                            saveError = "No user session found. Please sign in again."
                        }
                    }
                },
                enabled = !isLoading && businessName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.save_profile))
                }
            }

            // Show error message if save failed
            if (saveError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = saveError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
