package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
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
class DashboardViewModel
  @Inject
  constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase
  ) : ViewModel() {
    val transactions =
      getDashboardDataUseCase.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans =
      getDashboardDataUseCase.loans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments =
      getDashboardDataUseCase.installments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> =
      getDashboardDataUseCase.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankLoans: StateFlow<List<BankLoan>> =
      getDashboardDataUseCase.bankLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> =
      getDashboardDataUseCase.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()

    fun selectAccount(accountId: Long?) {
      _selectedAccountId.value = accountId
    }

    val dashboardState: StateFlow<DashboardData> =
      combine(
        combine(
          transactions,
          loans,
          installments,
          bankLoans,
          categories
        ) { trans, loanList, instList, bankLoanList, catList ->
          DashboardCoreData(trans, loanList, instList, bankLoanList, catList)
        },
        combine(accounts, _selectedAccountId) { accList, selectedId ->
          Pair(accList, selectedId)
        }
      ) { core, (accList, selectedId) ->
        val excludedCategoryIds =
          LoansCategoryExclusion.resolve(
            categories = core.categories,
            logTag = "DashboardViewModel"
          )
        getDashboardDataUseCase.computeDashboardData(
          core.transactions,
          core.loans,
          core.installments,
          core.bankLoans,
          accList,
          selectedId,
          includeArchived = false,
          excludedCategoryIds = excludedCategoryIds,
        )
      }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardData())

    private data class DashboardCoreData(
      val transactions: List<Transaction>,
      val loans: List<Loan>,
      val installments: List<Installment>,
      val bankLoans: List<BankLoan>,
      val categories: List<Category>
    )
  }
