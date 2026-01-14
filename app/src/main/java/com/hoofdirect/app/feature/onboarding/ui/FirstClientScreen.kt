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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep
import com.hoofdirect.app.feature.onboarding.model.formatPhoneNumber
import com.hoofdirect.app.feature.onboarding.ui.components.OnboardingScaffold

@Composable
fun FirstClientScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScaffold(
        currentStep = OnboardingStep.FIRST_CLIENT,
        title = "Add Your First Client",
        onBack = onBack,
        primaryButtonText = "Add Client",
        primaryButtonEnabled = uiState.firstClientName.isNotBlank(),
        onPrimaryClick = {
            if (viewModel.createFirstClient()) {
                onContinue()
            }
        },
        secondaryButtonText = "Skip for Now",
        onSecondaryClick = {
            viewModel.skipFirstClient()
            onContinue()
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Let's add your first client",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can add more details later. For now, just enter a name to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Client Name (required)
            OutlinedTextField(
                value = uiState.firstClientName,
                onValueChange = { viewModel.updateFirstClientName(it) },
                label = { Text("Client Name *") },
                placeholder = { Text("e.g., Jane Smith") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                },
                isError = uiState.firstClientError != null && uiState.firstClientName.isBlank(),
                supportingText = {
                    if (uiState.firstClientError != null && uiState.firstClientName.isBlank()) {
                        Text(uiState.firstClientError!!)
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

            // Phone (optional)
            OutlinedTextField(
                value = uiState.firstClientPhone,
                onValueChange = { viewModel.updateFirstClientPhone(formatPhoneNumber(it)) },
                label = { Text("Phone (optional)") },
                placeholder = { Text("(555) 123-4567") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = null
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Address (optional)
            OutlinedTextField(
                value = uiState.firstClientAddress,
                onValueChange = { viewModel.updateFirstClientAddress(it) },
                label = { Text("Address (optional)") },
                placeholder = { Text("Where you visit this client") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = null
                    )
                },
                singleLine = false,
                maxLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info text
            Text(
                text = "Don't worry! You can add more clients and edit details anytime from the Clients tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
