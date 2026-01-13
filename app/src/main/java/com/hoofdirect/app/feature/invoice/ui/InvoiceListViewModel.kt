package com.hoofdirect.app.feature.invoice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.dao.InvoiceDao
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceListUiState(
    val invoices: List<InvoiceEntity> = emptyList(),
    val clientNames: Map<String, String> = emptyMap(),
    val selectedFilter: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InvoiceListViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val clientRepository: ClientRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceListUiState())
    val uiState: StateFlow<InvoiceListUiState> = _uiState.asStateFlow()

    init {
        loadInvoices()
    }

    private fun loadInvoices() {
        val userId = tokenManager.getUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val flow = if (_uiState.value.selectedFilter != null) {
                invoiceDao.getInvoicesByStatus(userId, _uiState.value.selectedFilter!!)
            } else {
                invoiceDao.getAllInvoices(userId)
            }

            flow.collect { invoices ->
                val clientNames = mutableMapOf<String, String>()
                invoices.forEach { invoice ->
                    if (!clientNames.containsKey(invoice.clientId)) {
                        clientRepository.getClientByIdOnce(invoice.clientId)?.let { client ->
                            clientNames[client.id] = client.name
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        invoices = invoices,
                        clientNames = clientNames,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setFilter(filter: String?) {
        _uiState.update { it.copy(selectedFilter = filter) }
        loadInvoices()
    }
}
