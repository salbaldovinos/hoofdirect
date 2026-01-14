package com.hoofdirect.app.feature.appointment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.AppointmentHorseEntity
import com.hoofdirect.app.core.database.entity.AppointmentStatus
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.feature.appointment.data.AppointmentRepository
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import com.hoofdirect.app.feature.horse.data.HorseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppointmentHorseDetail(
    val appointmentHorse: AppointmentHorseEntity,
    val horse: HorseEntity?
)

data class AppointmentDetailUiState(
    val appointment: AppointmentEntity? = null,
    val client: ClientEntity? = null,
    val horseDetails: List<AppointmentHorseDetail> = emptyList(),
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val deleted: Boolean = false,
    val showCancelDialog: Boolean = false,
    val showDeleteDialog: Boolean = false
)

@HiltViewModel
class AppointmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val horseRepository: HorseRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val appointmentId: String = savedStateHandle.get<String>("appointmentId") ?: ""

    private val _uiState = MutableStateFlow(AppointmentDetailUiState())
    val uiState: StateFlow<AppointmentDetailUiState> = _uiState.asStateFlow()

    init {
        loadAppointment()
    }

    private fun loadAppointment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            appointmentRepository.getAppointmentById(appointmentId).collect { appointment ->
                if (appointment != null) {
                    // Load client
                    val client = clientRepository.getClientByIdOnce(appointment.clientId)

                    // Load horses with details
                    appointmentRepository.getHorsesForAppointment(appointmentId).collect { appointmentHorses ->
                        val horseDetails = appointmentHorses.map { ah ->
                            val horse = horseRepository.getHorseByIdOnce(ah.horseId)
                            AppointmentHorseDetail(ah, horse)
                        }

                        _uiState.update {
                            it.copy(
                                appointment = appointment,
                                client = client,
                                horseDetails = horseDetails,
                                isLoading = false
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Appointment not found")
                    }
                }
            }
        }
    }

    fun confirmAppointment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            appointmentRepository.updateStatus(appointmentId, AppointmentStatus.CONFIRMED)
                .onSuccess {
                    _uiState.update {
                        it.copy(isProcessing = false, successMessage = "Appointment confirmed")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message)
                    }
                }
        }
    }

    fun startAppointment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            appointmentRepository.updateStatus(appointmentId, AppointmentStatus.IN_PROGRESS)
                .onSuccess {
                    _uiState.update {
                        it.copy(isProcessing = false, successMessage = "Service started")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message)
                    }
                }
        }
    }

    fun completeAppointment() {
        val defaultCycleWeeks = 6 // Could come from user settings

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            appointmentRepository.completeAppointment(appointmentId, defaultCycleWeeks)
                .onSuccess {
                    _uiState.update {
                        it.copy(isProcessing = false, successMessage = "Appointment completed")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message)
                    }
                }
        }
    }

    fun showCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = true) }
    }

    fun hideCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = false) }
    }

    fun cancelAppointment(reason: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showCancelDialog = false) }

            appointmentRepository.cancelAppointment(appointmentId, reason)
                .onSuccess {
                    _uiState.update {
                        it.copy(isProcessing = false, successMessage = "Appointment cancelled")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message)
                    }
                }
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteAppointment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showDeleteDialog = false) }

            appointmentRepository.deleteAppointment(appointmentId)
                .onSuccess {
                    _uiState.update { it.copy(isProcessing = false, deleted = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message)
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
