package com.hoofdirect.app.feature.appointment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.AppointmentHorseEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class HorseServiceItem(
    val horse: HorseEntity,
    val isSelected: Boolean = false,
    val serviceType: String = "",
    val price: String = "0.00",
    val notes: String = ""
)

data class AppointmentFormState(
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val durationMinutes: Int = 45,
    val notes: String = "",
    val address: String = "",
    // Validation
    val clientError: String? = null,
    val dateError: String? = null,
    val horseError: String? = null
)

data class AppointmentFormUiState(
    val formState: AppointmentFormState = AppointmentFormState(),
    val isEditMode: Boolean = false,
    val appointmentId: String? = null,
    val selectedClient: ClientEntity? = null,
    val availableClients: List<ClientEntity> = emptyList(),
    val horseServices: List<HorseServiceItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingHorses: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
) {
    val totalPrice: String
        get() {
            val sum = horseServices
                .filter { it.isSelected }
                .sumOf { it.price.toBigDecimalOrNull()?.toDouble() ?: 0.0 }
            return "%.2f".format(sum)
        }

    val selectedHorsesCount: Int
        get() = horseServices.count { it.isSelected }

    val isValid: Boolean
        get() = selectedClient != null &&
                horseServices.any { it.isSelected && it.serviceType.isNotBlank() }
}

@HiltViewModel
class AppointmentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val horseRepository: HorseRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val appointmentId: String? = savedStateHandle.get<String>("appointmentId")?.takeIf { it.isNotBlank() }
    private val preselectedClientId: String? = savedStateHandle.get<String>("clientId")?.takeIf { it.isNotBlank() }
    private val isEditMode = appointmentId != null

    private val _uiState = MutableStateFlow(AppointmentFormUiState(isEditMode = isEditMode, appointmentId = appointmentId))
    val uiState: StateFlow<AppointmentFormUiState> = _uiState.asStateFlow()

    init {
        loadClients()
        if (isEditMode && appointmentId != null) {
            loadAppointment(appointmentId)
        } else if (preselectedClientId != null) {
            viewModelScope.launch {
                val client = clientRepository.getClientByIdOnce(preselectedClientId)
                if (client != null) {
                    selectClient(client)
                }
            }
        }
    }

    private fun loadClients() {
        val userId = tokenManager.getUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            clientRepository.getClients(userId).collect { clients ->
                _uiState.update {
                    it.copy(
                        availableClients = clients,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadAppointment(appointmentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val appointment = appointmentRepository.getAppointmentByIdOnce(appointmentId)
            if (appointment != null) {
                // Load client
                val client = clientRepository.getClientByIdOnce(appointment.clientId)

                // Load horses for this appointment
                val appointmentHorses = appointmentRepository.getHorsesForAppointment(appointmentId).first()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedClient = client,
                        formState = AppointmentFormState(
                            date = appointment.date,
                            startTime = appointment.startTime,
                            durationMinutes = appointment.durationMinutes,
                            notes = appointment.notes ?: "",
                            address = appointment.address ?: client?.address ?: ""
                        )
                    )
                }

                // Load horses for client and mark selected ones
                if (client != null) {
                    loadHorsesForClient(client.id, appointmentHorses)
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Appointment not found") }
            }
        }
    }

    fun selectClient(client: ClientEntity) {
        _uiState.update {
            it.copy(
                selectedClient = client,
                formState = it.formState.copy(
                    clientError = null,
                    address = client.address ?: ""
                ),
                horseServices = emptyList()
            )
        }
        loadHorsesForClient(client.id)
    }

    private fun loadHorsesForClient(clientId: String, preselectedHorses: List<AppointmentHorseEntity> = emptyList()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHorses = true) }

            horseRepository.getHorsesForClient(clientId).collect { horses ->
                val horseServiceItems = horses.map { horse ->
                    val preselected = preselectedHorses.find { it.horseId == horse.id }
                    HorseServiceItem(
                        horse = horse,
                        isSelected = preselected != null,
                        serviceType = preselected?.serviceType ?: horse.defaultServiceType ?: "",
                        price = preselected?.price ?: "0.00",
                        notes = preselected?.notes ?: ""
                    )
                }

                _uiState.update {
                    it.copy(
                        horseServices = horseServiceItems,
                        isLoadingHorses = false
                    )
                }
            }
        }
    }

    fun toggleHorseSelection(horseId: String) {
        _uiState.update { state ->
            state.copy(
                horseServices = state.horseServices.map { item ->
                    if (item.horse.id == horseId) {
                        item.copy(isSelected = !item.isSelected)
                    } else {
                        item
                    }
                },
                formState = state.formState.copy(horseError = null)
            )
        }
    }

    fun updateHorseServiceType(horseId: String, serviceType: String) {
        _uiState.update { state ->
            state.copy(
                horseServices = state.horseServices.map { item ->
                    if (item.horse.id == horseId) {
                        item.copy(serviceType = serviceType)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun updateHorsePrice(horseId: String, price: String) {
        _uiState.update { state ->
            state.copy(
                horseServices = state.horseServices.map { item ->
                    if (item.horse.id == horseId) {
                        item.copy(price = price)
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update {
            it.copy(formState = it.formState.copy(date = date, dateError = null))
        }
    }

    fun updateStartTime(time: LocalTime) {
        _uiState.update {
            it.copy(formState = it.formState.copy(startTime = time))
        }
    }

    fun updateDuration(minutes: Int) {
        _uiState.update {
            it.copy(formState = it.formState.copy(durationMinutes = minutes))
        }
    }

    fun updateNotes(notes: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(notes = notes.take(2000)))
        }
    }

    fun updateAddress(address: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(address = address))
        }
    }

    fun validateAndSave() {
        val state = _uiState.value

        // Validate
        val clientError = if (state.selectedClient == null) "Please select a client" else null
        val horseError = if (!state.horseServices.any { it.isSelected && it.serviceType.isNotBlank() }) {
            "Please select at least one horse with a service"
        } else null

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    clientError = clientError,
                    horseError = horseError
                )
            )
        }

        if (clientError != null || horseError != null) {
            return
        }

        saveAppointment()
    }

    private fun saveAppointment() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(error = "Session expired. Please sign in again.") }
            return
        }
        val state = _uiState.value
        val client = state.selectedClient
        if (client == null) {
            _uiState.update { it.copy(error = "No client selected. Please try again.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val appointmentIdToUse = appointmentId ?: UUID.randomUUID().toString()

            val appointment = AppointmentEntity(
                id = appointmentIdToUse,
                userId = userId,
                clientId = client.id,
                date = state.formState.date,
                startTime = state.formState.startTime,
                durationMinutes = state.formState.durationMinutes,
                notes = state.formState.notes.ifBlank { null },
                address = state.formState.address.ifBlank { client.address },
                latitude = client.latitude,
                longitude = client.longitude,
                totalPrice = state.totalPrice
            )

            val appointmentHorses = state.horseServices
                .filter { it.isSelected && it.serviceType.isNotBlank() }
                .map { item ->
                    AppointmentHorseEntity(
                        appointmentId = appointmentIdToUse,
                        horseId = item.horse.id,
                        serviceType = item.serviceType,
                        price = item.price,
                        notes = item.notes.ifBlank { null }
                    )
                }

            val result = if (isEditMode) {
                appointmentRepository.updateAppointment(appointment, appointmentHorses)
            } else {
                appointmentRepository.createAppointment(appointment, appointmentHorses)
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = e.message ?: "Failed to save appointment"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
