package com.hoofdirect.app.feature.pricing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.ServicePriceEntity
import com.hoofdirect.app.core.database.entity.ServiceTypes
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.pricing.data.ServicePriceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ServicePricesUiState(
    val services: List<ServicePriceEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showAddSheet: Boolean = false,
    val editingService: ServicePriceEntity? = null,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ServicePricesViewModel @Inject constructor(
    private val servicePriceRepository: ServicePriceRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServicePricesUiState())
    val uiState: StateFlow<ServicePricesUiState> = _uiState.asStateFlow()

    init {
        loadServices()
    }

    private fun loadServices() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Session expired") }
            return
        }

        viewModelScope.launch {
            // Create default services if none exist
            servicePriceRepository.createDefaultServicesIfNeeded(userId)

            servicePriceRepository.getAllServices(userId).collect { services ->
                _uiState.update {
                    it.copy(
                        services = services,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun showAddSheet() {
        _uiState.update { it.copy(showAddSheet = true) }
    }

    fun dismissAddSheet() {
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun editService(service: ServicePriceEntity) {
        _uiState.update { it.copy(editingService = service) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editingService = null) }
    }

    fun toggleActive(service: ServicePriceEntity) {
        viewModelScope.launch {
            servicePriceRepository.toggleActive(service.id, !service.isActive)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to update service") }
                }
        }
    }

    fun saveService(
        name: String,
        price: String,
        durationMinutes: Int,
        description: String?
    ) {
        val userId = tokenManager.getUserId() ?: return
        val editingService = _uiState.value.editingService

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val result = if (editingService != null) {
                // Update existing service
                val updated = editingService.copy(
                    name = name,
                    price = price,
                    durationMinutes = durationMinutes,
                    description = description?.ifBlank { null }
                )
                servicePriceRepository.updateService(updated)
            } else {
                // Create new custom service
                val newService = ServicePriceEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    serviceType = ServiceTypes.CUSTOM,
                    name = name,
                    price = price,
                    durationMinutes = durationMinutes,
                    description = description?.ifBlank { null },
                    isBuiltIn = false
                )
                servicePriceRepository.createService(newService)
            }

            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showAddSheet = false,
                            editingService = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = e.message ?: "Failed to save service"
                        )
                    }
                }
        }
    }

    fun deleteService(service: ServicePriceEntity) {
        viewModelScope.launch {
            servicePriceRepository.deleteService(service.id)
                .onSuccess {
                    _uiState.update { it.copy(editingService = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to delete service") }
                }
        }
    }

    fun reorderServices(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.services.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)

        _uiState.update { it.copy(services = currentList) }

        // Save new order
        viewModelScope.launch {
            val orders = currentList.mapIndexed { index, service ->
                service.id to index
            }
            servicePriceRepository.updateDisplayOrders(orders)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
