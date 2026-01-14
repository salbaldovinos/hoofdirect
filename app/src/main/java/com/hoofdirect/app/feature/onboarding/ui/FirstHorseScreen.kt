package com.hoofdirect.app.feature.onboarding.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep
import com.hoofdirect.app.feature.onboarding.ui.components.OnboardingScaffold

@Composable
fun FirstHorseScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by viewModel.onboardingState.collectAsStateWithLifecycle()

    // If no client was created, skip this step
    val hasClient = onboardingState.firstClientId != null
    val clientName = onboardingState.firstClientName ?: "your client"

    OnboardingScaffold(
        currentStep = OnboardingStep.FIRST_HORSE,
        title = "Add a Horse",
        onBack = onBack,
        primaryButtonText = if (hasClient) "Add Horse" else "Continue",
        primaryButtonEnabled = !hasClient || uiState.firstHorseName.isNotBlank(),
        onPrimaryClick = {
            if (hasClient) {
                if (viewModel.createFirstHorse()) {
                    onContinue()
                }
            } else {
                onContinue()
            }
        },
        secondaryButtonText = if (hasClient) "Skip for Now" else null,
        onSecondaryClick = if (hasClient) {
            {
                viewModel.skipFirstHorse()
                onContinue()
            }
        } else null
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            if (hasClient) {
                Text(
                    text = "Add a horse for $clientName",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Horses belong to clients. Add your first horse to see how the app tracks their service history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Horse Name (required)
                OutlinedTextField(
                    value = uiState.firstHorseName,
                    onValueChange = { viewModel.updateFirstHorseName(it) },
                    label = { Text("Horse Name *") },
                    placeholder = { Text("e.g., Thunder") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Pets,
                            contentDescription = null
                        )
                    },
                    isError = uiState.firstHorseError != null && uiState.firstHorseName.isBlank(),
                    supportingText = {
                        if (uiState.firstHorseError != null && uiState.firstHorseName.isBlank()) {
                            Text(uiState.firstHorseError!!)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Breed (optional)
                OutlinedTextField(
                    value = uiState.firstHorseBreed,
                    onValueChange = { viewModel.updateFirstHorseBreed(it) },
                    label = { Text("Breed (optional)") },
                    placeholder = { Text("e.g., Quarter Horse") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "You can add more horses and details later. Each horse tracks their own service schedule and history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // No client was created, explain what happens
                Text(
                    text = "Horses Need a Client",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Since you skipped adding a client, you'll need to add one first before adding horses. Horses belong to clients in Hoof Direct.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "No worries! You can add clients and horses from the main app after setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
