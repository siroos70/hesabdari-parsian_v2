package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoanViewModel
  @Inject
  constructor(
    private val manageLoanUseCase: ManageLoanUseCase
  ) : ViewModel() {
    val loans: StateFlow<List<Loan>> =
      manageLoanUseCase.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLoan(
      personName: String,
      type: LoanType,
      amount: Long,
      description: String,
      customDate: Long? = null
    ) {
      viewModelScope.launch {
        manageLoanUseCase.addLoan(personName, type, amount, description, customDate)
      }
    }

    // Safety net: a Room/repository failure must reach the UI callback so the
    // dialog is released, instead of crashing via an unhandled exception.
    // Cancellation is rethrown to keep structured concurrency intact.
    @Suppress("TooGenericExceptionCaught")
    fun makeRepayment(
      loanId: Long,
      amount: Long,
      notes: String,
      customDate: Long? = null,
      onResult: (Boolean) -> Unit = {}
    ) {
      viewModelScope.launch {
        try {
          onResult(manageLoanUseCase.makeRepayment(loanId, amount, notes, customDate))
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          AppLogger.w("LoanViewModel", "makeRepayment failed for loan=$loanId: ${e.message}")
          onResult(false)
        }
      }
    }

    fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> = manageLoanUseCase.getPaymentHistory(loanId)

    fun updateLoan(loan: Loan) {
      viewModelScope.launch {
        manageLoanUseCase.updateLoan(loan)
      }
    }

    fun deleteLoan(loan: Loan) {
      viewModelScope.launch {
        manageLoanUseCase.deleteLoan(loan)
      }
    }
  }
