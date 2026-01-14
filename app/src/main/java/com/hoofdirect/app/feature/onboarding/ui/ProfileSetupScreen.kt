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
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Home
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
import com.hoofdirect.app.feature.onboarding.model.ProfileFieldError
import com.hoofdirect.app.feature.onboarding.model.formatPhoneNumber
import com.hoofdirect.app.feature.onboarding.ui.components.OnboardingScaffold

@Composable
fun ProfileSetupScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profileData = uiState.profileData
    val validationErrors = uiState.profileValidationErrors

    OnboardingScaffold(
        currentStep = OnboardingStep.PROFILE_SETUP,
        title = "Your Profile",
        onBack = onBack,
        primaryButtonText = "Continue",
        primaryButtonEnabled = true,
        onPrimaryClick = {
            if (viewModel.validateAndSaveProfile()) {
                onContinue()
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Let's set up your business",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This information helps us personalize your experience and optimize routes from your home base.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Business Name
            OutlinedTextField(
                value = profileData.businessName,
                onValueChange = { viewModel.updateBusinessName(it) },
                label = { Text("Business Name") },
                placeholder = { Text("e.g., John's Farrier Service") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Business,
                        contentDescription = null
                    )
                },
                isError = validationErrors.contains(ProfileFieldError.BUSINESS_NAME_REQUIRED),
                supportingText = {
                    if (validationErrors.contains(ProfileFieldError.BUSINESS_NAME_REQUIRED)) {
                        Text(ProfileFieldError.BUSINESS_NAME_REQUIRED.message)
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

            // Phone Number
            OutlinedTextField(
                value = profileData.phone,
                onValueChange = { input ->
                    viewModel.updatePhone(formatPhoneNumber(input))
                },
                label = { Text("Phone Number") },
                placeholder = { Text("(555) 123-4567") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = null
                    )
                },
                isError = validationErrors.any {
                    it == ProfileFieldError.PHONE_REQUIRED || it == ProfileFieldError.PHONE_INVALID
                },
                supportingText = {
                    val phoneError = validationErrors.find {
                        it == ProfileFieldError.PHONE_REQUIRED || it == ProfileFieldError.PHONE_INVALID
                    }
                    phoneError?.let { Text(it.message) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Home Address
            OutlinedTextField(
                value = profileData.homeAddress,
                onValueChange = { viewModel.updateHomeAddress(it) },
                label = { Text("Home Address") },
                placeholder = { Text("Where you start your day") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = null
                    )
                },
                isError = validationErrors.any {
                    it == ProfileFieldError.ADDRESS_REQUIRED || it == ProfileFieldError.ADDRESS_NOT_GEOCODED
                },
                supportingText = {
                    val addressError = validationErrors.find {
                        it == ProfileFieldError.ADDRESS_REQUIRED || it == ProfileFieldError.ADDRESS_NOT_GEOCODED
                    }
                    if (addressError != null) {
                        Text(addressError.message)
                    } else {
                        Text("Used for route optimization")
                    }
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

            // Info card about why we need the address
            InfoCard(
                text = "Your home address is used to calculate optimal driving routes. We'll always start and end your daily route at this location."
            )
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
