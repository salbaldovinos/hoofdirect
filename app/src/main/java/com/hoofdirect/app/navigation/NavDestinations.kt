package com.hoofdirect.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the app.
 */
sealed interface NavDestination {

    // Auth flow
    @Serializable
    data object SignIn : NavDestination

    // SignUp is handled via marketing website - kept for potential future use
    // @Serializable
    // data object SignUp : NavDestination

    @Serializable
    data object ForgotPassword : NavDestination

    @Serializable
    data class EmailVerification(val email: String) : NavDestination

    @Serializable
    data class ResetPassword(val token: String) : NavDestination

    @Serializable
    data object ProfileSetup : NavDestination

    // Main app with bottom navigation
    @Serializable
    data object MainApp : NavDestination

    // Main tabs (used internally by MainAppShell)
    @Serializable
    data object Schedule : NavDestination

    @Serializable
    data object Clients : NavDestination

    @Serializable
    data object Routes : NavDestination

    @Serializable
    data object Invoices : NavDestination

    @Serializable
    data object More : NavDestination

    // Detail screens
    @Serializable
    data class AppointmentDetail(val id: String) : NavDestination

    @Serializable
    data class AppointmentEdit(val id: String? = null) : NavDestination

    @Serializable
    data class ClientDetail(val id: String) : NavDestination

    @Serializable
    data class ClientEdit(val id: String? = null) : NavDestination

    @Serializable
    data class HorseDetail(val id: String) : NavDestination

    @Serializable
    data class HorseEdit(val clientId: String, val horseId: String? = null) : NavDestination

    @Serializable
    data class InvoiceDetail(val id: String) : NavDestination

    @Serializable
    data class InvoiceEdit(val id: String? = null, val appointmentId: String? = null) : NavDestination

    // More section
    @Serializable
    data object Settings : NavDestination

    @Serializable
    data object Profile : NavDestination

    @Serializable
    data object Mileage : NavDestination

    @Serializable
    data object Reports : NavDestination

    @Serializable
    data object Subscription : NavDestination

    @Serializable
    data object Help : NavDestination
}
