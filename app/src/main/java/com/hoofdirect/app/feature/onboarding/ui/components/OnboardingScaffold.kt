package com.hoofdirect.app.feature.onboarding.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep

/**
 * Common scaffold for onboarding screens with progress indicator and navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScaffold(
    currentStep: OnboardingStep,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    primaryButtonText: String = "Continue",
    primaryButtonEnabled: Boolean = true,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    showProgress: Boolean = true,
    content: @Composable () -> Unit
) {
    val progress = calculateProgress(currentStep)
    val showBackButton = currentStep != OnboardingStep.WELCOME

    Scaffold(
        topBar = {
            if (showBackButton || onSkip != null || title != null) {
                TopAppBar(
                    title = { title?.let { Text(it) } ?: Unit },
                    navigationIcon = {
                        if (showBackButton && onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (onSkip != null) {
                            TextButton(onClick = onSkip) {
                                Text("Skip")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }

            // Bottom buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Button(
                    onClick = onPrimaryClick,
                    enabled = primaryButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(primaryButtonText)
                }

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(secondaryButtonText)
                    }
                }
            }
        }
    }
}

private fun calculateProgress(step: OnboardingStep): Float {
    val totalSteps = OnboardingStep.entries.size
    val currentIndex = OnboardingStep.entries.indexOf(step)
    return (currentIndex + 1).toFloat() / totalSteps.toFloat()
}
