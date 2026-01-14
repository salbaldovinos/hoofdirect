package com.hoofdirect.app.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.dao.UserDao
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import com.hoofdirect.app.feature.horse.data.HorseRepository
import com.hoofdirect.app.feature.onboarding.data.OnboardingPreferencesManager
import com.hoofdirect.app.feature.onboarding.model.AppPermission
import com.hoofdirect.app.feature.onboarding.model.OnboardingState
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep
import com.hoofdirect.app.feature.onboarding.model.PermissionsState
import com.hoofdirect.app.feature.onboarding.model.ProfileFieldError
import com.hoofdirect.app.feature.onboarding.model.ProfileSetupData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the onboarding flow.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferencesManager: OnboardingPreferencesManager,
    private val tokenManager: TokenManager,
    private val userDao: UserDao,
    private val clientRepository: ClientRepository,
    private val horseRepository: HorseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _onboardingState = MutableStateFlow(OnboardingState())
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    init {
        loadSavedProgress()
    }

    private fun loadSavedProgress() {
        viewModelScope.launch {
            // Load saved profile data if any
            val savedProfile = onboardingPreferencesManager.savedProfileData.first()
            if (!savedProfile.isEmpty) {
                _uiState.update { it.copy(
                    profileData = ProfileSetupData(
                        businessName = savedProfile.businessName,
                        phone = savedProfile.phone,
                        homeAddress = savedProfile.address
                    )
                )}
            }

            // Load last step if resuming
            val lastStep = onboardingPreferencesManager.lastStepCompleted.first()
            if (lastStep != null) {
                val step = OnboardingStep.entries.find { it.name == lastStep }
                if (step != null) {
                    _onboardingState.update { it.copy(currentStep = step) }
                }
            }
        }
    }

    // ==================== Navigation ====================

    fun goToNextStep() {
        val currentStep = _onboardingState.value.currentStep
        val nextStep = getNextStep(currentStep)

        if (nextStep != null) {
            viewModelScope.launch {
                onboardingPreferencesManager.saveLastStep(nextStep.name)
            }
            _onboardingState.update { it.copy(currentStep = nextStep) }
        }
    }

    fun goToPreviousStep() {
        val currentStep = _onboardingState.value.currentStep
        val previousStep = getPreviousStep(currentStep)

        if (previousStep != null) {
            _onboardingState.update { it.copy(currentStep = previousStep) }
        }
    }

    private fun getNextStep(current: OnboardingStep): OnboardingStep? {
        return when (current) {
            OnboardingStep.WELCOME -> OnboardingStep.PROFILE_SETUP
            OnboardingStep.PROFILE_SETUP -> OnboardingStep.PERMISSIONS_LOCATION
            OnboardingStep.PERMISSIONS_LOCATION -> OnboardingStep.PERMISSIONS_CALENDAR
            OnboardingStep.PERMISSIONS_CALENDAR -> OnboardingStep.PERMISSIONS_NOTIFICATIONS
            OnboardingStep.PERMISSIONS_NOTIFICATIONS -> OnboardingStep.FIRST_CLIENT
            OnboardingStep.FIRST_CLIENT -> OnboardingStep.FIRST_HORSE
            OnboardingStep.FIRST_HORSE -> OnboardingStep.FIRST_APPOINTMENT
            OnboardingStep.FIRST_APPOINTMENT -> OnboardingStep.COMPLETION
            OnboardingStep.COMPLETION -> null
        }
    }

    private fun getPreviousStep(current: OnboardingStep): OnboardingStep? {
        return when (current) {
            OnboardingStep.WELCOME -> null
            OnboardingStep.PROFILE_SETUP -> OnboardingStep.WELCOME
            OnboardingStep.PERMISSIONS_LOCATION -> OnboardingStep.PROFILE_SETUP
            OnboardingStep.PERMISSIONS_CALENDAR -> OnboardingStep.PERMISSIONS_LOCATION
            OnboardingStep.PERMISSIONS_NOTIFICATIONS -> OnboardingStep.PERMISSIONS_CALENDAR
            OnboardingStep.FIRST_CLIENT -> OnboardingStep.PERMISSIONS_NOTIFICATIONS
            OnboardingStep.FIRST_HORSE -> OnboardingStep.FIRST_CLIENT
            OnboardingStep.FIRST_APPOINTMENT -> OnboardingStep.FIRST_HORSE
            OnboardingStep.COMPLETION -> OnboardingStep.FIRST_APPOINTMENT
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            onboardingPreferencesManager.incrementSkipCount()
            onboardingPreferencesManager.setOnboardingComplete()
            _onboardingState.update { it.copy(isComplete = true) }
        }
    }

    // ==================== Profile Setup ====================

    fun updateBusinessName(name: String) {
        _uiState.update { it.copy(
            profileData = it.profileData.copy(businessName = name)
        )}
        saveProfileDataAsync()
    }

    fun updatePhone(phone: String) {
        _uiState.update { it.copy(
            profileData = it.profileData.copy(phone = phone)
        )}
        saveProfileDataAsync()
    }

    fun updateHomeAddress(address: String, latitude: Double? = null, longitude: Double? = null) {
        _uiState.update { it.copy(
            profileData = it.profileData.copy(
                homeAddress = address,
                homeLatitude = latitude,
                homeLongitude = longitude
            )
        )}
        saveProfileDataAsync()
    }

    private fun saveProfileDataAsync() {
        viewModelScope.launch {
            val profile = _uiState.value.profileData
            onboardingPreferencesManager.saveProfileData(
                businessName = profile.businessName,
                phone = profile.phone,
                address = profile.homeAddress
            )
        }
    }

    fun validateAndSaveProfile(): Boolean {
        val profileData = _uiState.value.profileData

        if (!profileData.isValid) {
            _uiState.update { it.copy(
                profileValidationErrors = profileData.validationErrors
            )}
            return false
        }

        // Check if address is geocoded
        if (profileData.homeLatitude == null || profileData.homeLongitude == null) {
            _uiState.update { it.copy(
                profileValidationErrors = listOf(ProfileFieldError.ADDRESS_NOT_GEOCODED)
            )}
            return false
        }

        // Save profile to user entity
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            val user = userDao.getUserByIdOnce(userId) ?: return@launch

            val updatedUser = user.copy(
                businessName = profileData.businessName,
                phone = profileData.phone,
                address = profileData.homeAddress,
                homeLatitude = profileData.homeLatitude,
                homeLongitude = profileData.homeLongitude,
                profileCompleted = true,
                updatedAt = Instant.now()
            )
            userDao.updateUser(updatedUser)
        }

        _uiState.update { it.copy(profileValidationErrors = emptyList()) }
        _onboardingState.update { it.copy(profileComplete = true) }
        return true
    }

    // ==================== Permissions ====================

    fun onPermissionResult(permission: AppPermission, granted: Boolean) {
        val currentPermissions = _onboardingState.value.permissionsRequested

        val updatedPermissions = when (permission) {
            AppPermission.LOCATION -> currentPermissions.copy(
                locationRequested = true,
                locationGranted = granted
            )
            AppPermission.CALENDAR -> currentPermissions.copy(
                calendarRequested = true,
                calendarGranted = granted
            )
            AppPermission.NOTIFICATIONS -> currentPermissions.copy(
                notificationsRequested = true,
                notificationsGranted = granted
            )
        }

        _onboardingState.update { it.copy(permissionsRequested = updatedPermissions) }
    }

    fun skipPermission(permission: AppPermission) {
        onPermissionResult(permission, granted = false)
        goToNextStep()
    }

    // ==================== First Client ====================

    fun updateFirstClientName(name: String) {
        _uiState.update { it.copy(firstClientName = name) }
    }

    fun updateFirstClientPhone(phone: String) {
        _uiState.update { it.copy(firstClientPhone = phone) }
    }

    fun updateFirstClientAddress(address: String, latitude: Double? = null, longitude: Double? = null) {
        _uiState.update { it.copy(
            firstClientAddress = address,
            firstClientLatitude = latitude,
            firstClientLongitude = longitude
        )}
    }

    fun createFirstClient(): Boolean {
        val state = _uiState.value

        if (state.firstClientName.isBlank()) {
            _uiState.update { it.copy(firstClientError = "Client name is required") }
            return false
        }

        viewModelScope.launch {
            _onboardingState.update { it.copy(isLoading = true) }

            val userId = tokenManager.getUserId() ?: return@launch

            val clientId = UUID.randomUUID().toString()
            val nameParts = state.firstClientName.trim().split(" ", limit = 2)
            val firstName = nameParts.first()
            val lastName = nameParts.getOrNull(1)

            val client = ClientEntity(
                id = clientId,
                userId = userId,
                name = state.firstClientName.trim(),
                firstName = firstName,
                lastName = lastName,
                phone = state.firstClientPhone.ifBlank { "" },
                address = state.firstClientAddress.ifBlank { null },
                latitude = state.firstClientLatitude,
                longitude = state.firstClientLongitude
            )

            val result = clientRepository.createClient(client)

            if (result.isSuccess) {
                _onboardingState.update { it.copy(
                    firstClientId = clientId,
                    firstClientName = state.firstClientName.trim(),
                    isLoading = false
                )}
                _uiState.update { it.copy(firstClientError = null) }
            } else {
                _onboardingState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )}
            }
        }

        return true
    }

    fun skipFirstClient() {
        goToNextStep()
    }

    // ==================== First Horse ====================

    fun updateFirstHorseName(name: String) {
        _uiState.update { it.copy(firstHorseName = name) }
    }

    fun updateFirstHorseBreed(breed: String) {
        _uiState.update { it.copy(firstHorseBreed = breed) }
    }

    fun createFirstHorse(): Boolean {
        val state = _uiState.value
        val clientId = _onboardingState.value.firstClientId

        if (state.firstHorseName.isBlank()) {
            _uiState.update { it.copy(firstHorseError = "Horse name is required") }
            return false
        }

        if (clientId == null) {
            _uiState.update { it.copy(firstHorseError = "Please create a client first") }
            return false
        }

        viewModelScope.launch {
            _onboardingState.update { it.copy(isLoading = true) }

            val userId = tokenManager.getUserId() ?: return@launch
            val horseId = UUID.randomUUID().toString()

            val horse = HorseEntity(
                id = horseId,
                userId = userId,
                clientId = clientId,
                name = state.firstHorseName.trim(),
                breed = state.firstHorseBreed.ifBlank { null }
            )

            val result = horseRepository.createHorse(horse)

            if (result.isSuccess) {
                _onboardingState.update { it.copy(
                    firstHorseId = horseId,
                    isLoading = false
                )}
                _uiState.update { it.copy(firstHorseError = null) }
            } else {
                _onboardingState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )}
            }
        }

        return true
    }

    fun skipFirstHorse() {
        goToNextStep()
    }

    // ==================== First Appointment ====================

    fun skipFirstAppointment() {
        goToNextStep()
    }

    // ==================== Completion ====================

    fun completeOnboarding() {
        viewModelScope.launch {
            onboardingPreferencesManager.setOnboardingComplete()
            _onboardingState.update { it.copy(isComplete = true) }
        }
    }

    fun clearError() {
        _onboardingState.update { it.copy(error = null) }
    }
}

/**
 * UI state for the onboarding screens.
 */
data class OnboardingUiState(
    // Profile Setup
    val profileData: ProfileSetupData = ProfileSetupData(),
    val profileValidationErrors: List<ProfileFieldError> = emptyList(),

    // First Client
    val firstClientName: String = "",
    val firstClientPhone: String = "",
    val firstClientAddress: String = "",
    val firstClientLatitude: Double? = null,
    val firstClientLongitude: Double? = null,
    val firstClientError: String? = null,

    // First Horse
    val firstHorseName: String = "",
    val firstHorseBreed: String = "",
    val firstHorseError: String? = null,

    // First Appointment
    val firstAppointmentDate: String = "",
    val firstAppointmentTime: String = "",
    val firstAppointmentError: String? = null
)
