package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallmentViewModel
  @Inject
  constructor(
    private val manageInstallmentUseCase: ManageInstallmentUseCase
  ) : ViewModel() {
    val installments: StateFlow<List<Installment>> =
      manageInstallmentUseCase.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bankLoanFilter = MutableStateFlow<Long?>(null)
    val bankLoanFilter: StateFlow<Long?> = _bankLoanFilter.asStateFlow()

    val visibleInstallments: StateFlow<List<Installment>> =
      combine(installments, _bankLoanFilter) { list, activeFilter ->
        if (activeFilter == null) list else list.filter { it.bankLoanId == activeFilter }
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBankLoanFilter(bankLoanId: Long?) {
      _bankLoanFilter.value = bankLoanId
    }

    fun addInstallment(
      title: String,
      amount: Long,
      dueDate: Long,
      reminderEnabled: Boolean,
      notes: String
    ) {
      viewModelScope.launch {
        manageInstallmentUseCase.addInstallment(title, amount, dueDate, reminderEnabled, notes)
      }
    }

    fun toggleInstallmentPaid(installment: Installment) {
      viewModelScope.launch {
        manageInstallmentUseCase.toggleInstallmentPaid(installment)
      }
    }

    fun deleteInstallment(installment: Installment) {
      viewModelScope.launch {
        manageInstallmentUseCase.deleteInstallment(installment)
      }
    }
  }
