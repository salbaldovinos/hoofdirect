package com.hoofdirect.app.feature.client.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.feature.appointment.data.AppointmentRepository
import com.hoofdirect.app.feature.client.data.ClientRepository
import com.hoofdirect.app.feature.horse.data.HorseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ClientDetailUiState(
    val client: ClientEntity? = null,
    val horses: List<HorseEntity> = emptyList(),
    val upcomingAppointments: List<AppointmentEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showArchiveDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val actionResult: ActionResult? = null
)

sealed class ActionResult {
    data object ClientArchived : ActionResult()
    data object ClientRestored : ActionResult()
    data object ClientDeleted : ActionResult()
    data class Error(val message: String) : ActionResult()
}

@HiltViewModel
class ClientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientRepository: ClientRepository,
    private val horseRepository: HorseRepository,
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {

    private val clientId: String = savedStateHandle.get<String>("clientId") ?: ""

    private val _uiState = MutableStateFlow(ClientDetailUiState())
    val uiState: StateFlow<ClientDetailUiState> = _uiState.asStateFlow()

    init {
        loadClientDetails()
    }

    private fun loadClientDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            combine(
                clientRepository.getClientById(clientId),
                horseRepository.getHorsesForClient(clientId),
                appointmentRepository.getUpcomingAppointmentsForClient(clientId)
            ) { client, horses, appointments ->
                Triple(client, horses, appointments)
            }.collect { (client, horses, appointments) ->
                _uiState.update {
                    it.copy(
                        client = client,
                        horses = horses,
                        upcomingAppointments = appointments,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun showArchiveDialog() {
        _uiState.update { it.copy(showArchiveDialog = true) }
    }

    fun dismissArchiveDialog() {
        _uiState.update { it.copy(showArchiveDialog = false) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun archiveClient() {
        viewModelScope.launch {
            _uiState.update { it.copy(showArchiveDialog = false) }

            clientRepository.archiveClient(clientId)
                .onSuccess {
                    _uiState.update { it.copy(actionResult = ActionResult.ClientArchived) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionResult = ActionResult.Error(e.message ?: "Failed to archive client"))
                    }
                }
        }
    }

    fun restoreClient() {
        viewModelScope.launch {
            clientRepository.restoreClient(clientId)
                .onSuccess {
                    _uiState.update { it.copy(actionResult = ActionResult.ClientRestored) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionResult = ActionResult.Error(e.message ?: "Failed to restore client"))
                    }
                }
        }
    }

    fun deleteClient() {
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteDialog = false) }

            clientRepository.deleteClient(clientId)
                .onSuccess {
                    _uiState.update { it.copy(actionResult = ActionResult.ClientDeleted) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionResult = ActionResult.Error(e.message ?: "Failed to delete client"))
                    }
                }
        }
    }

    fun clearActionResult() {
        _uiState.update { it.copy(actionResult = null) }
    }

    fun callClient(context: Context) {
        uiState.value.client?.phone?.let { phone ->
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            context.startActivity(intent)
        }
    }

    fun textClient(context: Context) {
        uiState.value.client?.phone?.let { phone ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:$phone")
            }
            context.startActivity(intent)
        }
    }

    fun emailClient(context: Context) {
        uiState.value.client?.email?.let { email ->
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
            }
            context.startActivity(intent)
        }
    }

    fun navigateToClient(context: Context) {
        uiState.value.client?.let { client ->
            val address = client.address ?: return
            val gmmIntentUri = if (client.latitude != null && client.longitude != null) {
                Uri.parse("google.navigation:q=${client.latitude},${client.longitude}")
            } else {
                Uri.parse("google.navigation:q=${Uri.encode(address)}")
            }
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(mapIntent)
        }
    }
}
