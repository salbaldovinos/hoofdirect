package com.hoofdirect.app.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.feature.auth.data.AuthRepository
import com.hoofdirect.app.feature.auth.data.BiometricManager
import com.hoofdirect.app.feature.auth.data.BiometricStatus
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.auth.domain.EmailValidator
import com.hoofdirect.app.feature.auth.domain.PasswordValidator
import com.hoofdirect.app.feature.auth.domain.model.AuthError
import com.hoofdirect.app.feature.auth.domain.model.AuthUiState
import com.hoofdirect.app.feature.auth.domain.model.PasswordStrength
import com.hoofdirect.app.feature.auth.domain.model.SignInUiState
import com.hoofdirect.app.feature.auth.domain.model.SignUpUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val biometricManager: BiometricManager
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _signInState = MutableStateFlow(SignInUiState())
    val signInState: StateFlow<SignInUiState> = _signInState.asStateFlow()

    private val _signUpState = MutableStateFlow(SignUpUiState())
    val signUpState: StateFlow<SignUpUiState> = _signUpState.asStateFlow()

    private val maxFailedAttempts = 5
    private val lockoutDurationMs = 15 * 60 * 1000L // 15 minutes

    init {
        checkExistingSession()
        checkBiometricAvailability()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _authState.update {
                    it.copy(
                        isAuthenticated = user != null,
                        hasCompletedProfile = user?.profileCompleted == true,
                        user = user
                    )
                }
            }
        }
    }

    private fun checkBiometricAvailability() {
        val canUseBiometric = biometricManager.canAuthenticate() == BiometricStatus.AVAILABLE
                && tokenManager.isBiometricEnabled()
                && tokenManager.getSavedEmail() != null
        _signInState.update { it.copy(canUseBiometric = canUseBiometric) }
    }

    // Sign In methods
    fun updateSignInEmail(email: String) {
        _signInState.update {
            it.copy(
                email = email,
                emailError = EmailValidator.getError(email),
                error = null
            )
        }
    }

    fun updateSignInPassword(password: String) {
        _signInState.update {
            it.copy(
                password = password,
                passwordError = null,
                error = null
            )
        }
    }

    fun updateRememberMe(enabled: Boolean) {
        _signInState.update { it.copy(rememberMe = enabled) }
        tokenManager.setRememberMe(enabled)
    }

    fun toggleSignInPasswordVisibility() {
        _signInState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun signIn() {
        val state = _signInState.value

        // Check lockout
        if (isLockedOut()) {
            return
        }

        // Validate inputs
        val emailError = if (!EmailValidator.validate(state.email)) {
            "Please enter a valid email address"
        } else null

        val passwordError = if (state.password.isBlank()) {
            "Please enter your password"
        } else null

        if (emailError != null || passwordError != null) {
            _signInState.update {
                it.copy(emailError = emailError, passwordError = passwordError)
            }
            return
        }

        viewModelScope.launch {
            _signInState.update { it.copy(isLoading = true, error = null) }

            val result = authRepository.signIn(state.email, state.password)

            result.fold(
                onSuccess = { user ->
                    _signInState.update {
                        it.copy(
                            isLoading = false,
                            failedAttempts = 0,
                            lockoutEndTime = null
                        )
                    }
                    _authState.update {
                        it.copy(
                            isAuthenticated = true,
                            hasCompletedProfile = user.profileCompleted,
                            user = user
                        )
                    }
                },
                onFailure = { error ->
                    val authError = error as? AuthError ?: AuthError.Unknown(error.message ?: "Sign in failed")
                    val newFailedAttempts = state.failedAttempts + 1
                    val lockoutTime = if (newFailedAttempts >= maxFailedAttempts) {
                        System.currentTimeMillis() + lockoutDurationMs
                    } else null

                    _signInState.update {
                        it.copy(
                            isLoading = false,
                            error = if (lockoutTime != null) AuthError.AccountLocked else authError,
                            password = "", // Clear password on failure
                            failedAttempts = newFailedAttempts,
                            lockoutEndTime = lockoutTime
                        )
                    }
                }
            )
        }
    }

    fun signInWithBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val savedEmail = tokenManager.getSavedEmail()
        val savedPassword = tokenManager.getSavedPassword()

        if (savedEmail != null && savedPassword != null) {
            viewModelScope.launch {
                _signInState.update { it.copy(isLoading = true) }

                val result = authRepository.signIn(savedEmail, savedPassword)

                result.fold(
                    onSuccess = { user ->
                        _signInState.update { it.copy(isLoading = false) }
                        _authState.update {
                            it.copy(
                                isAuthenticated = true,
                                hasCompletedProfile = user.profileCompleted,
                                user = user
                            )
                        }
                        onSuccess()
                    },
                    onFailure = { error ->
                        _signInState.update { it.copy(isLoading = false) }
                        onError((error as? AuthError)?.toUserMessage() ?: "Authentication failed")
                    }
                )
            }
        } else {
            onError("No saved credentials")
        }
    }

    private fun isLockedOut(): Boolean {
        val lockoutEnd = _signInState.value.lockoutEndTime ?: return false
        if (System.currentTimeMillis() >= lockoutEnd) {
            _signInState.update {
                it.copy(lockoutEndTime = null, failedAttempts = 0)
            }
            return false
        }
        return true
    }

    fun getLockoutRemainingSeconds(): Int {
        val lockoutEnd = _signInState.value.lockoutEndTime ?: return 0
        val remaining = lockoutEnd - System.currentTimeMillis()
        return (remaining / 1000).toInt().coerceAtLeast(0)
    }

    // Sign Up methods
    fun updateSignUpEmail(email: String) {
        _signUpState.update {
            it.copy(
                email = email,
                emailError = EmailValidator.getError(email),
                error = null
            )
        }
    }

    fun updateSignUpPassword(password: String) {
        val validationResult = PasswordValidator.validate(password)
        _signUpState.update {
            it.copy(
                password = password,
                passwordErrors = if (password.isNotEmpty()) validationResult.errors else emptyList(),
                passwordStrength = validationResult.strength,
                error = null
            )
        }
        // Also validate confirm password match
        if (_signUpState.value.confirmPassword.isNotEmpty()) {
            updateConfirmPassword(_signUpState.value.confirmPassword)
        }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _signUpState.update {
            it.copy(
                confirmPassword = confirmPassword,
                confirmPasswordError = if (confirmPassword.isNotEmpty() && confirmPassword != it.password) {
                    "Passwords do not match"
                } else null,
                error = null
            )
        }
    }

    fun updateAcceptedTerms(accepted: Boolean) {
        _signUpState.update {
            it.copy(
                acceptedTerms = accepted,
                termsError = null,
                error = null
            )
        }
    }

    fun toggleSignUpPasswordVisibility() {
        _signUpState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun signUp() {
        val state = _signUpState.value

        // Validate all inputs
        val emailError = if (!EmailValidator.validate(state.email)) {
            "Please enter a valid email address"
        } else null

        val passwordValidation = PasswordValidator.validate(state.password)

        val confirmPasswordError = if (state.confirmPassword != state.password) {
            "Passwords do not match"
        } else null

        val termsError = if (!state.acceptedTerms) {
            "You must accept the Terms of Service"
        } else null

        if (emailError != null || !passwordValidation.isValid ||
            confirmPasswordError != null || termsError != null) {
            _signUpState.update {
                it.copy(
                    emailError = emailError,
                    passwordErrors = passwordValidation.errors,
                    confirmPasswordError = confirmPasswordError,
                    termsError = termsError
                )
            }
            return
        }

        viewModelScope.launch {
            _signUpState.update { it.copy(isLoading = true, error = null) }

            val result = authRepository.signUp(state.email, state.password)

            result.fold(
                onSuccess = { user ->
                    _signUpState.update { it.copy(isLoading = false) }
                    _authState.update {
                        it.copy(
                            isAuthenticated = true,
                            hasCompletedProfile = false,
                            user = user
                        )
                    }
                },
                onFailure = { error ->
                    val authError = error as? AuthError ?: AuthError.Unknown(error.message ?: "Sign up failed")
                    _signUpState.update {
                        it.copy(isLoading = false, error = authError)
                    }
                }
            )
        }
    }

    // Password Reset
    fun sendPasswordResetEmail(email: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            onComplete(result.isSuccess)
        }
    }

    // Session management
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.update {
                AuthUiState()
            }
            _signInState.update { SignInUiState() }
            _signUpState.update { SignUpUiState() }
        }
    }

    fun clearError() {
        _signInState.update { it.copy(error = null) }
        _signUpState.update { it.copy(error = null) }
    }

    // Profile Update
    fun updateUserProfile(
        user: com.hoofdirect.app.feature.auth.domain.model.User,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.updateProfile(user)
            result.fold(
                onSuccess = { updatedUser ->
                    _authState.update {
                        it.copy(
                            hasCompletedProfile = updatedUser.profileCompleted,
                            user = updatedUser
                        )
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    onError(error.message ?: "Failed to save profile")
                }
            )
        }
    }
}
