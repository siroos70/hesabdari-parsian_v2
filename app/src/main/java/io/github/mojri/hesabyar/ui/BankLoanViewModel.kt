package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.domain.usecase.ManageBankLoanUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankLoanViewModel
  @Inject
  constructor(
    private val manageBankLoanUseCase: ManageBankLoanUseCase
  ) : ViewModel() {
    val bankLoans: StateFlow<List<BankLoan>> =
      manageBankLoanUseCase.allBankLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    fun addBankLoan(
      bankName: String,
      loanName: String,
      receivedAmount: Long,
      monthlyInstallmentAmount: Long,
      numberOfInstallments: Int,
      startDate: Long,
      description: String
    ) {
      viewModelScope.launch {
        manageBankLoanUseCase.addBankLoan(
          bankName,
          loanName,
          receivedAmount,
          monthlyInstallmentAmount,
          numberOfInstallments,
          startDate,
          description
        )
      }
    }

    fun deleteBankLoan(bankLoan: BankLoan) {
      viewModelScope.launch { manageBankLoanUseCase.deleteBankLoan(bankLoan) }
    }

    fun toggleSettled(id: Long) {
      viewModelScope.launch { manageBankLoanUseCase.toggleSettled(id) }
    }

    fun installmentsByLoan(bankLoanId: Long): Flow<List<Installment>> =
      manageBankLoanUseCase.installmentsByBankLoan(bankLoanId)

    private companion object {
      const val SUBSCRIBE_TIMEOUT_MS = 5000L
    }
  }
