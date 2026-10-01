package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.usecase.GetAnalyticsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel
  @Inject
  constructor(
    private val repository: HesabyarRepositoryInterface,
    private val getAnalyticsUseCase: GetAnalyticsUseCase,
    private val manageLoanUseCase: ManageLoanUseCase,
    private val manageInstallmentUseCase: ManageInstallmentUseCase
  ) : ViewModel() {
    private val _selectedJalaliMonth = MutableStateFlow<Pair<Int, Int>?>(null)
    val selectedJalaliMonth: StateFlow<Pair<Int, Int>?> = _selectedJalaliMonth.asStateFlow()

    val transactions: StateFlow<List<Transaction>> =
      repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<Loan>> =
      manageLoanUseCase.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> =
      manageInstallmentUseCase.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> =
      repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankLoans: StateFlow<List<BankLoan>> =
      repository.allBankLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> =
      repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()

    fun selectAccount(accountId: Long?) {
      _selectedAccountId.value = accountId
    }

    val analyticsData: StateFlow<AnalyticsData> =
      combine(
        combine(
          transactions,
          loans,
          installments,
          categories,
          bankLoans
        ) { trans, loanList, instList, catList, bankLoanList ->
          Quintuple(trans, loanList, instList, catList, bankLoanList)
        },
        combine(accounts, _selectedAccountId) { accList, selectedId ->
          Pair(accList, selectedId)
        }
      ) { data, (accList, selectedId) ->
        val excludedCategoryIds =
          LoansCategoryExclusion.resolve(
            categories = data.fourth,
            logTag = "AnalyticsViewModel"
          )
        getAnalyticsUseCase.computeAnalytics(
          data.first,
          data.second,
          data.third,
          data.fourth,
          data.fifth,
          accList,
          selectedId,
          excludedCategoryIds = excludedCategoryIds,
        )
      }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsData())

    fun setSelectedJalaliMonth(
      year: Int?,
      month: Int?
    ) {
      _selectedJalaliMonth.value = if (year != null && month != null) year to month else null
    }

    private data class Quintuple<A, B, C, D, E>(
      val first: A,
      val second: B,
      val third: C,
      val fourth: D,
      val fifth: E
    )
  }
