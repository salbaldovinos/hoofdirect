package com.hoofdirect.app.feature.client.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.location.GeocodingService
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ClientFormState(
    val firstName: String = "",
    val lastName: String = "",
    val businessName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val zipCode: String? = null,
    val accessNotes: String = "",
    val notes: String = "",
    val reminderPreference: String = "SMS",
    val reminderHours: Int = 24,
    // Validation errors
    val firstNameError: String? = null,
    val phoneError: String? = null,
    val emailError: String? = null
) {
    val isValid: Boolean
        get() = firstName.isNotBlank() &&
                phone.isNotBlank() &&
                isValidPhone(phone) &&
                (email.isBlank() || isValidEmail(email))

    val displayName: String
        get() = if (lastName.isNotBlank()) "$firstName $lastName" else firstName

    companion object {
        fun isValidPhone(phone: String): Boolean {
            val digitsOnly = phone.filter { it.isDigit() }
            return digitsOnly.length >= 10
        }

        fun isValidEmail(email: String): Boolean {
            return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }
    }
}

data class ClientFormUiState(
    val formState: ClientFormState = ClientFormState(),
    val isEditMode: Boolean = false,
    val clientId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ClientFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val tokenManager: TokenManager,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val clientId: String? = savedStateHandle.get<String>("clientId")?.takeIf { it.isNotBlank() }
    private val isEditMode = clientId != null

    private val _uiState = MutableStateFlow(ClientFormUiState(isEditMode = isEditMode, clientId = clientId))
    val uiState: StateFlow<ClientFormUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode && clientId != null) {
            loadClient(clientId)
        }
    }

    private fun loadClient(clientId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            clientRepository.getClientById(clientId).collect { client ->
                if (client != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            formState = ClientFormState(
                                firstName = client.firstName,
                                lastName = client.lastName ?: "",
                                businessName = client.businessName ?: "",
                                phone = client.phone,
                                email = client.email ?: "",
                                address = client.address ?: "",
                                latitude = client.latitude,
                                longitude = client.longitude,
                                city = client.city,
                                state = client.state,
                                zipCode = client.zipCode,
                                accessNotes = client.accessNotes ?: "",
                                notes = client.notes ?: "",
                                reminderPreference = client.reminderPreference,
                                reminderHours = client.reminderHours
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Client not found") }
                }
            }
        }
    }

    fun updateFirstName(value: String) {
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    firstName = value,
                    firstNameError = if (value.isBlank()) "First name is required" else null
                )
            )
        }
    }

    fun updateLastName(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(lastName = value))
        }
    }

    fun updateBusinessName(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(businessName = value))
        }
    }

    fun updatePhone(value: String) {
        val phoneError = when {
            value.isBlank() -> "Phone number is required"
            !ClientFormState.isValidPhone(value) -> "Please enter a valid phone number"
            else -> null
        }
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    phone = value,
                    phoneError = phoneError
                )
            )
        }
    }

    fun updateEmail(value: String) {
        val emailError = when {
            value.isBlank() -> null // Optional
            !ClientFormState.isValidEmail(value) -> "Please enter a valid email address"
            else -> null
        }
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    email = value,
                    emailError = emailError
                )
            )
        }
    }

    fun updateAddress(value: String) {
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    address = value,
                    // Clear geocoding data when address changes manually
                    latitude = null,
                    longitude = null,
                    city = null,
                    state = null,
                    zipCode = null
                )
            )
        }
    }

    fun updateAddressWithGeocoding(
        address: String,
        latitude: Double?,
        longitude: Double?,
        city: String?,
        state: String?,
        zipCode: String?
    ) {
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    city = city,
                    state = state,
                    zipCode = zipCode
                )
            )
        }
    }

    fun updateAccessNotes(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(accessNotes = value.take(500)))
        }
    }

    fun updateNotes(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(notes = value.take(2000)))
        }
    }

    fun updateReminderPreference(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(reminderPreference = value))
        }
    }

    fun updateReminderHours(value: Int) {
        _uiState.update {
            it.copy(formState = it.formState.copy(reminderHours = value))
        }
    }

    fun validateAndSave() {
        val formState = _uiState.value.formState

        // Validate
        val firstNameError = if (formState.firstName.isBlank()) "First name is required" else null
        val phoneError = when {
            formState.phone.isBlank() -> "Phone number is required"
            !ClientFormState.isValidPhone(formState.phone) -> "Please enter a valid phone number"
            else -> null
        }
        val emailError = when {
            formState.email.isBlank() -> null
            !ClientFormState.isValidEmail(formState.email) -> "Please enter a valid email address"
            else -> null
        }

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    firstNameError = firstNameError,
                    phoneError = phoneError,
                    emailError = emailError
                )
            )
        }

        if (firstNameError != null || phoneError != null || emailError != null) {
            return
        }

        saveClient()
    }

    private fun saveClient() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(error = "Session expired. Please sign in again.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            var formState = _uiState.value.formState

            // Geocode address if we don't have coordinates but have an address
            if (formState.address.isNotBlank() && (formState.latitude == null || formState.longitude == null)) {
                val geocodingResult = geocodingService.geocodeAddress(formState.address)
                if (geocodingResult != null) {
                    formState = formState.copy(
                        latitude = geocodingResult.latitude,
                        longitude = geocodingResult.longitude,
                        city = geocodingResult.city ?: formState.city,
                        state = geocodingResult.state ?: formState.state,
                        zipCode = geocodingResult.zipCode ?: formState.zipCode
                    )
                    // Update UI state with geocoded data
                    _uiState.update { it.copy(formState = formState) }
                }
            }

            val client = ClientEntity(
                id = clientId ?: UUID.randomUUID().toString(),
                userId = userId,
                name = formState.displayName,
                firstName = formState.firstName,
                lastName = formState.lastName.ifBlank { null },
                businessName = formState.businessName.ifBlank { null },
                phone = formState.phone,
                email = formState.email.ifBlank { null },
                address = formState.address.ifBlank { null },
                latitude = formState.latitude,
                longitude = formState.longitude,
                city = formState.city,
                state = formState.state,
                zipCode = formState.zipCode,
                accessNotes = formState.accessNotes.ifBlank { null },
                notes = formState.notes.ifBlank { null },
                reminderPreference = formState.reminderPreference,
                reminderHours = formState.reminderHours
            )

            val result = if (isEditMode) {
                clientRepository.updateClient(client)
            } else {
                clientRepository.createClient(client)
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = e.message ?: "Failed to save client"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
