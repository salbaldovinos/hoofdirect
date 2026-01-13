package com.hoofdirect.app.feature.auth.domain.model

import com.hoofdirect.app.core.database.entity.UserEntity

data class User(
    val id: String,
    val email: String,
    val emailVerified: Boolean = false,
    val businessName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val serviceRadiusMiles: Int = 50,
    val defaultDurationMinutes: Int = 45,
    val defaultCycleWeeks: Int = 6,
    val subscriptionTier: String = "free",
    val profilePhotoUrl: String? = null,
    val profileCompleted: Boolean = false
) {
    fun toEntity(): UserEntity = UserEntity(
        id = id,
        email = email,
        emailVerified = emailVerified,
        businessName = businessName,
        phone = phone,
        address = address,
        homeLatitude = homeLatitude,
        homeLongitude = homeLongitude,
        serviceRadiusMiles = serviceRadiusMiles,
        defaultDurationMinutes = defaultDurationMinutes,
        defaultCycleWeeks = defaultCycleWeeks,
        subscriptionTier = subscriptionTier,
        profilePhotoUrl = profilePhotoUrl,
        profileCompleted = profileCompleted
    )

    companion object {
        fun fromEntity(entity: UserEntity): User = User(
            id = entity.id,
            email = entity.email,
            emailVerified = entity.emailVerified,
            businessName = entity.businessName,
            phone = entity.phone,
            address = entity.address,
            homeLatitude = entity.homeLatitude,
            homeLongitude = entity.homeLongitude,
            serviceRadiusMiles = entity.serviceRadiusMiles,
            defaultDurationMinutes = entity.defaultDurationMinutes,
            defaultCycleWeeks = entity.defaultCycleWeeks,
            subscriptionTier = entity.subscriptionTier,
            profilePhotoUrl = entity.profilePhotoUrl,
            profileCompleted = entity.profileCompleted
        )
    }
}

sealed class AuthState {
    data object Initial : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val error: AuthError) : AuthState()
}

sealed class AuthError(message: String) : Exception(message) {
    data object InvalidCredentials : AuthError("Invalid email or password")
    data object EmailNotVerified : AuthError("Please verify your email before signing in")
    data object EmailAlreadyRegistered : AuthError("This email is already registered. Try signing in instead.")
    data object WeakPassword : AuthError("Password doesn't meet requirements")
    data object AccountLocked : AuthError("Too many failed attempts. Please try again later.")
    data object NetworkError : AuthError("Unable to connect. Check your internet connection and try again.")
    data object SessionExpired : AuthError("Your session has expired. Please sign in again.")
    data object NoSession : AuthError("Please sign in to continue.")
    data object RateLimited : AuthError("Too many attempts. Please wait a few minutes.")
    data class Unknown(override val message: String) : AuthError(message)

    fun toUserMessage(): String = message ?: "Unknown error"
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val hasCompletedProfile: Boolean = false,
    val user: User? = null,
    val error: AuthError? = null,
    val failedAttempts: Int = 0,
    val lockoutEndTime: Long? = null
)

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = true,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: AuthError? = null,
    val failedAttempts: Int = 0,
    val lockoutEndTime: Long? = null,
    val canUseBiometric: Boolean = false
)

data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedTerms: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordErrors: List<String> = emptyList(),
    val confirmPasswordError: String? = null,
    val termsError: String? = null,
    val error: AuthError? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK
)

enum class PasswordStrength {
    WEAK,
    FAIR,
    GOOD,
    STRONG
}

data class PasswordValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val strength: PasswordStrength
)
