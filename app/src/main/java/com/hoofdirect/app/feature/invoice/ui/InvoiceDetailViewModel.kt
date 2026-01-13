package com.hoofdirect.app.feature.invoice.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceItemEntity
import com.hoofdirect.app.core.database.entity.InvoiceStatus
import com.hoofdirect.app.feature.client.data.ClientRepository
import com.hoofdirect.app.feature.invoice.data.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceDetailUiState(
    val invoice: InvoiceEntity? = null,
    val client: ClientEntity? = null,
    val items: List<InvoiceItemEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val deleted: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showMarkPaidDialog: Boolean = false
)

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val invoiceRepository: InvoiceRepository,
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val invoiceId: String = savedStateHandle.get<String>("invoiceId") ?: ""

    private val _uiState = MutableStateFlow(InvoiceDetailUiState())
    val uiState: StateFlow<InvoiceDetailUiState> = _uiState.asStateFlow()

    init {
        loadInvoice()
    }

    private fun loadInvoice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            invoiceRepository.getInvoiceById(invoiceId).collect { invoice ->
                if (invoice != null) {
                    val client = clientRepository.getClientByIdOnce(invoice.clientId)
                    val items = invoiceRepository.getItemsForInvoiceOnce(invoiceId)

                    _uiState.update {
                        it.copy(
                            invoice = invoice,
                            client = client,
                            items = items,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Invoice not found")
                    }
                }
            }
        }
    }

    fun markAsSent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            invoiceRepository.markAsSent(invoiceId)
                .onSuccess {
                    _uiState.update { it.copy(isProcessing = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isProcessing = false, error = e.message)
                    }
                }
        }
    }

    fun showMarkPaidDialog() {
        _uiState.update { it.copy(showMarkPaidDialog = true) }
    }

    fun hideMarkPaidDialog() {
        _uiState.update { it.copy(showMarkPaidDialog = false) }
    }

    fun markAsPaid(paymentMethod: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showMarkPaidDialog = false) }

            invoiceRepository.markAsPaid(invoiceId, paymentMethod)
                .onSuccess {
                    _uiState.update { it.copy(isProcessing = false) }
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

    fun deleteInvoice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, showDeleteDialog = false) }

            invoiceRepository.deleteInvoice(invoiceId)
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
}
