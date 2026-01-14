package com.hoofdirect.app.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep
import com.hoofdirect.app.feature.onboarding.ui.CalendarPermissionScreen
import com.hoofdirect.app.feature.onboarding.ui.CompletionScreen
import com.hoofdirect.app.feature.onboarding.ui.FirstAppointmentScreen
import com.hoofdirect.app.feature.onboarding.ui.FirstClientScreen
import com.hoofdirect.app.feature.onboarding.ui.FirstHorseScreen
import com.hoofdirect.app.feature.onboarding.ui.LocationPermissionScreen
import com.hoofdirect.app.feature.onboarding.ui.NotificationsPermissionScreen
import com.hoofdirect.app.feature.onboarding.ui.OnboardingViewModel
import com.hoofdirect.app.feature.onboarding.ui.ProfileSetupScreen
import com.hoofdirect.app.feature.onboarding.ui.WelcomeScreen

/**
 * Navigation routes for onboarding flow.
 */
object OnboardingRoutes {
    const val WELCOME = "onboarding/welcome"
    const val PROFILE_SETUP = "onboarding/profile"
    const val PERMISSION_LOCATION = "onboarding/permission/location"
    const val PERMISSION_CALENDAR = "onboarding/permission/calendar"
    const val PERMISSION_NOTIFICATIONS = "onboarding/permission/notifications"
    const val FIRST_CLIENT = "onboarding/first-client"
    const val FIRST_HORSE = "onboarding/first-horse"
    const val FIRST_APPOINTMENT = "onboarding/first-appointment"
    const val COMPLETION = "onboarding/completion"
}

/**
 * Main onboarding navigation host.
 */
@Composable
fun OnboardingNavHost(
    onOnboardingComplete: () -> Unit,
    navController: NavHostController = rememberNavController(),
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val onboardingState by viewModel.onboardingState.collectAsStateWithLifecycle()

    // Navigate when onboarding is complete
    LaunchedEffect(onboardingState.isComplete) {
        if (onboardingState.isComplete) {
            onOnboardingComplete()
        }
    }

    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.WELCOME
    ) {
        composable(OnboardingRoutes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.PROFILE_SETUP)
                },
                onSkip = {
                    viewModel.skipOnboarding()
                }
            )
        }

        composable(OnboardingRoutes.PROFILE_SETUP) {
            ProfileSetupScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.PERMISSION_LOCATION)
                }
            )
        }

        composable(OnboardingRoutes.PERMISSION_LOCATION) {
            LocationPermissionScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.PERMISSION_CALENDAR)
                }
            )
        }

        composable(OnboardingRoutes.PERMISSION_CALENDAR) {
            CalendarPermissionScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.PERMISSION_NOTIFICATIONS)
                }
            )
        }

        composable(OnboardingRoutes.PERMISSION_NOTIFICATIONS) {
            NotificationsPermissionScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.FIRST_CLIENT)
                }
            )
        }

        composable(OnboardingRoutes.FIRST_CLIENT) {
            FirstClientScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.FIRST_HORSE)
                }
            )
        }

        composable(OnboardingRoutes.FIRST_HORSE) {
            FirstHorseScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.FIRST_APPOINTMENT)
                }
            )
        }

        composable(OnboardingRoutes.FIRST_APPOINTMENT) {
            FirstAppointmentScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.goToPreviousStep()
                    navController.popBackStack()
                },
                onContinue = {
                    viewModel.goToNextStep()
                    navController.navigate(OnboardingRoutes.COMPLETION)
                }
            )
        }

        composable(OnboardingRoutes.COMPLETION) {
            CompletionScreen(
                viewModel = viewModel,
                onFinish = {
                    // onboardingComplete will be handled by LaunchedEffect
                }
            )
        }
    }
}

/**
 * Convert OnboardingStep to navigation route.
 */
fun OnboardingStep.toRoute(): String {
    return when (this) {
        OnboardingStep.WELCOME -> OnboardingRoutes.WELCOME
        OnboardingStep.PROFILE_SETUP -> OnboardingRoutes.PROFILE_SETUP
        OnboardingStep.PERMISSIONS_LOCATION -> OnboardingRoutes.PERMISSION_LOCATION
        OnboardingStep.PERMISSIONS_CALENDAR -> OnboardingRoutes.PERMISSION_CALENDAR
        OnboardingStep.PERMISSIONS_NOTIFICATIONS -> OnboardingRoutes.PERMISSION_NOTIFICATIONS
        OnboardingStep.FIRST_CLIENT -> OnboardingRoutes.FIRST_CLIENT
        OnboardingStep.FIRST_HORSE -> OnboardingRoutes.FIRST_HORSE
        OnboardingStep.FIRST_APPOINTMENT -> OnboardingRoutes.FIRST_APPOINTMENT
        OnboardingStep.COMPLETION -> OnboardingRoutes.COMPLETION
    }
}
