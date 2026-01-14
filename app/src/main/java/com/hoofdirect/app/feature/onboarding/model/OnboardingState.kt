package com.hoofdirect.app.feature.onboarding.model

/**
 * Steps in the onboarding flow.
 */
enum class OnboardingStep {
    WELCOME,
    PROFILE_SETUP,
    PERMISSIONS_LOCATION,
    PERMISSIONS_CALENDAR,
    PERMISSIONS_NOTIFICATIONS,
    FIRST_CLIENT,
    FIRST_HORSE,
    FIRST_APPOINTMENT,
    COMPLETION
}

/**
 * Current state of the onboarding flow.
 */
data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isComplete: Boolean = false,
    val profileComplete: Boolean = false,
    val permissionsRequested: PermissionsState = PermissionsState(),
    val firstClientId: String? = null,
    val firstClientName: String? = null,
    val firstHorseId: String? = null,
    val firstAppointmentId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Tracks which permissions have been requested and granted.
 */
data class PermissionsState(
    val locationRequested: Boolean = false,
    val locationGranted: Boolean = false,
    val calendarRequested: Boolean = false,
    val calendarGranted: Boolean = false,
    val notificationsRequested: Boolean = false,
    val notificationsGranted: Boolean = false
)

/**
 * Permissions that can be requested during onboarding.
 */
enum class AppPermission {
    LOCATION,
    CALENDAR,
    NOTIFICATIONS
}
