package com.hoofdirect.app.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.feature.auth.data.AuthRepository
import com.hoofdirect.app.feature.auth.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false,
    // Editable fields
    val businessName: String = "",
    val phone: String = "",
    val address: String = "",
    val serviceRadiusMiles: Int = 50,
    val defaultDurationMinutes: Int = 45,
    val defaultCycleWeeks: Int = 6
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update {
                    it.copy(
                        user = user,
                        isLoading = false,
                        businessName = user?.businessName ?: "",
                        phone = user?.phone ?: "",
                        address = user?.address ?: "",
                        serviceRadiusMiles = user?.serviceRadiusMiles ?: 50,
                        defaultDurationMinutes = user?.defaultDurationMinutes ?: 45,
                        defaultCycleWeeks = user?.defaultCycleWeeks ?: 6
                    )
                }
            }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun cancelEditing() {
        val user = _uiState.value.user
        _uiState.update {
            it.copy(
                isEditing = false,
                businessName = user?.businessName ?: "",
                phone = user?.phone ?: "",
                address = user?.address ?: "",
                serviceRadiusMiles = user?.serviceRadiusMiles ?: 50,
                defaultDurationMinutes = user?.defaultDurationMinutes ?: 45,
                defaultCycleWeeks = user?.defaultCycleWeeks ?: 6
            )
        }
    }

    fun updateBusinessName(value: String) {
        _uiState.update { it.copy(businessName = value) }
    }

    fun updatePhone(value: String) {
        _uiState.update { it.copy(phone = value) }
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    fun updateServiceRadius(value: Int) {
        _uiState.update { it.copy(serviceRadiusMiles = value) }
    }

    fun updateDefaultDuration(value: Int) {
        _uiState.update { it.copy(defaultDurationMinutes = value) }
    }

    fun updateDefaultCycle(value: Int) {
        _uiState.update { it.copy(defaultCycleWeeks = value) }
    }

    fun saveProfile() {
        val state = _uiState.value
        val currentUser = state.user ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val updatedUser = currentUser.copy(
                businessName = state.businessName.ifBlank { null },
                phone = state.phone.ifBlank { null },
                address = state.address.ifBlank { null },
                serviceRadiusMiles = state.serviceRadiusMiles,
                defaultDurationMinutes = state.defaultDurationMinutes,
                defaultCycleWeeks = state.defaultCycleWeeks
            )

            authRepository.updateProfile(updatedUser).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isEditing = false,
                            saveSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = error.message ?: "Failed to save profile"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
