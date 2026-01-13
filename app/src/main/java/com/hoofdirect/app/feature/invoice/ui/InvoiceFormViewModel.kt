package com.hoofdirect.app.feature.invoice.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceItemEntity
import com.hoofdirect.app.core.database.entity.InvoiceStatus
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import com.hoofdirect.app.feature.invoice.data.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class InvoiceLineItem(
    val id: String = UUID.randomUUID().toString(),
    val horseName: String = "",
    val serviceType: String = "",
    val description: String = "",
    val quantity: Int = 1,
    val unitPrice: String = "0.00"
) {
    val total: String
        get() {
            val price = unitPrice.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            return price.multiply(java.math.BigDecimal(quantity)).toString()
        }
}

data class InvoiceFormState(
    val invoiceNumber: String = "",
    val invoiceDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate = LocalDate.now().plusDays(30),
    val notes: String = "",
    val taxRate: Double = 0.0,
    // Validation
    val clientError: String? = null,
    val itemsError: String? = null
)

data class InvoiceFormUiState(
    val formState: InvoiceFormState = InvoiceFormState(),
    val isEditMode: Boolean = false,
    val invoiceId: String? = null,
    val selectedClient: ClientEntity? = null,
    val availableClients: List<ClientEntity> = emptyList(),
    val lineItems: List<InvoiceLineItem> = listOf(InvoiceLineItem()),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
) {
    val subtotal: String
        get() {
            val sum = lineItems.sumOf { item ->
                item.total.toBigDecimalOrNull()?.toDouble() ?: 0.0
            }
            return "%.2f".format(sum)
        }

    val tax: String
        get() {
            val sub = subtotal.toDoubleOrNull() ?: 0.0
            return "%.2f".format(sub * (formState.taxRate / 100))
        }

    val total: String
        get() {
            val sub = subtotal.toDoubleOrNull() ?: 0.0
            val t = tax.toDoubleOrNull() ?: 0.0
            return "%.2f".format(sub + t)
        }

    val isValid: Boolean
        get() = selectedClient != null &&
                lineItems.any { it.horseName.isNotBlank() && it.serviceType.isNotBlank() }
}

@HiltViewModel
class InvoiceFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val invoiceRepository: InvoiceRepository,
    private val clientRepository: ClientRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val invoiceId: String? = savedStateHandle.get<String>("invoiceId")?.takeIf { it.isNotBlank() }
    private val isEditMode = invoiceId != null

    private val _uiState = MutableStateFlow(InvoiceFormUiState(isEditMode = isEditMode, invoiceId = invoiceId))
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    init {
        loadClients()
        if (isEditMode && invoiceId != null) {
            loadInvoice(invoiceId)
        } else {
            generateInvoiceNumber()
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

    private fun generateInvoiceNumber() {
        val userId = tokenManager.getUserId() ?: return

        viewModelScope.launch {
            val invoiceNumber = invoiceRepository.getNextInvoiceNumber(userId)
            _uiState.update {
                it.copy(formState = it.formState.copy(invoiceNumber = invoiceNumber))
            }
        }
    }

    private fun loadInvoice(invoiceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val invoice = invoiceRepository.getInvoiceByIdOnce(invoiceId)
            if (invoice != null) {
                val client = clientRepository.getClientByIdOnce(invoice.clientId)
                val items = invoiceRepository.getItemsForInvoiceOnce(invoiceId)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedClient = client,
                        formState = InvoiceFormState(
                            invoiceNumber = invoice.invoiceNumber,
                            invoiceDate = invoice.invoiceDate,
                            dueDate = invoice.dueDate,
                            notes = invoice.notes ?: ""
                        ),
                        lineItems = items.map { item ->
                            InvoiceLineItem(
                                id = item.id,
                                horseName = item.horseName,
                                serviceType = item.serviceType,
                                description = item.description ?: "",
                                quantity = item.quantity,
                                unitPrice = item.unitPrice
                            )
                        }.ifEmpty { listOf(InvoiceLineItem()) }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Invoice not found") }
            }
        }
    }

    fun selectClient(client: ClientEntity) {
        _uiState.update {
            it.copy(
                selectedClient = client,
                formState = it.formState.copy(clientError = null)
            )
        }
    }

    fun updateInvoiceDate(date: LocalDate) {
        _uiState.update {
            it.copy(formState = it.formState.copy(invoiceDate = date))
        }
    }

    fun updateDueDate(date: LocalDate) {
        _uiState.update {
            it.copy(formState = it.formState.copy(dueDate = date))
        }
    }

    fun updateNotes(notes: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(notes = notes.take(2000)))
        }
    }

    fun updateLineItem(itemId: String, updatedItem: InvoiceLineItem) {
        _uiState.update { state ->
            state.copy(
                lineItems = state.lineItems.map { item ->
                    if (item.id == itemId) updatedItem else item
                },
                formState = state.formState.copy(itemsError = null)
            )
        }
    }

    fun addLineItem() {
        _uiState.update { state ->
            state.copy(lineItems = state.lineItems + InvoiceLineItem())
        }
    }

    fun removeLineItem(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.lineItems.filter { it.id != itemId }
            state.copy(lineItems = updatedItems.ifEmpty { listOf(InvoiceLineItem()) })
        }
    }

    fun validateAndSave() {
        val state = _uiState.value

        // Validate
        val clientError = if (state.selectedClient == null) "Please select a client" else null
        val itemsError = if (!state.lineItems.any { it.horseName.isNotBlank() && it.serviceType.isNotBlank() }) {
            "Please add at least one line item"
        } else null

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    clientError = clientError,
                    itemsError = itemsError
                )
            )
        }

        if (clientError != null || itemsError != null) {
            return
        }

        saveInvoice()
    }

    private fun saveInvoice() {
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

            val invoiceIdToUse = invoiceId ?: UUID.randomUUID().toString()

            val invoice = InvoiceEntity(
                id = invoiceIdToUse,
                userId = userId,
                clientId = client.id,
                invoiceNumber = state.formState.invoiceNumber,
                invoiceDate = state.formState.invoiceDate,
                dueDate = state.formState.dueDate,
                subtotal = state.subtotal,
                tax = state.tax,
                total = state.total,
                status = if (isEditMode) {
                    invoiceRepository.getInvoiceByIdOnce(invoiceIdToUse)?.status ?: InvoiceStatus.DRAFT.name
                } else {
                    InvoiceStatus.DRAFT.name
                },
                notes = state.formState.notes.ifBlank { null }
            )

            val invoiceItems = state.lineItems
                .filter { it.horseName.isNotBlank() && it.serviceType.isNotBlank() }
                .map { item ->
                    InvoiceItemEntity(
                        id = if (isEditMode) item.id else UUID.randomUUID().toString(),
                        invoiceId = invoiceIdToUse,
                        horseName = item.horseName,
                        serviceType = item.serviceType,
                        description = item.description.ifBlank { null },
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        total = item.total
                    )
                }

            val result = if (isEditMode) {
                invoiceRepository.updateInvoice(invoice, invoiceItems)
            } else {
                invoiceRepository.createInvoice(invoice, invoiceItems)
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = e.message ?: "Failed to save invoice"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
