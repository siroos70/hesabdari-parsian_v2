package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.FakeRepository
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelCategoryExclusionTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var fakeRepo: TestDashboardRepository
  private lateinit var viewModel: DashboardViewModel

  private val regularIncomeCatId = 10L
  private val regularExpenseCatId = 20L
  private val loansCatId = 999L

  private val regularIncomeAmount = 10_000_000L
  private val loanIncomeAmount = 5_000_000L
  private val regularExpenseAmount = 4_000_000L
  private val loanExpenseAmount = 2_000_000L

  @Before
  fun setup() {
    HesabyarApp.setRustInitializedForTesting(false)
    Dispatchers.setMain(testDispatcher)
    fakeRepo = TestDashboardRepository()
    val useCase = GetDashboardDataUseCase(fakeRepo)
    viewModel = DashboardViewModel(useCase)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class TestDashboardRepository : HesabyarRepositoryInterface by FakeRepository() {
    val txFlow = MutableStateFlow<List<Transaction>>(emptyList())
    val catFlow = MutableStateFlow<List<Category>>(emptyList())
    val accFlow = MutableStateFlow<List<AccountEntity>>(emptyList())

    override val allTransactions = txFlow
    override val allCategories = catFlow
    override val allAccounts = accFlow
    override val allLoans = MutableStateFlow<List<Loan>>(emptyList())
    override val allInstallments = MutableStateFlow<List<Installment>>(emptyList())
    override val allBankLoans = MutableStateFlow<List<BankLoan>>(emptyList())
  }

  @Suppress("LongMethod")
  @Test
  fun dashboardStateExcludesLoansCategoryWhenCategoryIsEmitted() =
    runTest(testDispatcher) {
      val dateInMonth = System.currentTimeMillis()

      val account = AccountEntity(id = 1L, name = "Main", type = AccountType.BANK)
      fakeRepo.accFlow.value = listOf(account)

      fakeRepo.catFlow.value =
        listOf(
          Category(
            id = loansCatId,
            name = "Loans",
            key = LoansCategoryExclusion.CATEGORY_KEY,
            icon = "loan",
            color = 1L,
            type = CategoryType.BOTH
          ),
          Category(
            id = regularIncomeCatId,
            name = "Salary",
            key = "salary",
            icon = "money",
            color = 2L,
            type = CategoryType.INCOME
          ),
          Category(
            id = regularExpenseCatId,
            name = "Food",
            key = "food",
            icon = "food",
            color = 3L,
            type = CategoryType.EXPENSE
          )
        )

      fakeRepo.txFlow.value =
        listOf(
          Transaction(
            type = TransactionType.INCOME,
            categoryId = regularIncomeCatId,
            amount = regularIncomeAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "salary"
          ),
          Transaction(
            type = TransactionType.INCOME,
            categoryId = loansCatId,
            amount = loanIncomeAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "loan"
          ),
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = regularExpenseCatId,
            amount = regularExpenseAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "food"
          ),
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = loansCatId,
            amount = loanExpenseAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "installment"
          )
        )

      backgroundScope.launch { viewModel.dashboardState.collect {} }
      advanceUntilIdle()

      val state =
        viewModel.dashboardState
          .filter {
            it.accounts.isNotEmpty() &&
              it.monthlyIncome == regularIncomeAmount &&
              it.monthlyExpenses == regularExpenseAmount
          }.first()
      assertEquals("monthly income must exclude loans category", regularIncomeAmount, state.monthlyIncome)
      assertEquals("monthly expense must exclude loans category", regularExpenseAmount, state.monthlyExpenses)
    }

  @Suppress("LongMethod")
  @Test
  fun dashboardStateIncludesAllTransactionsWhenCategoriesEmpty() =
    runTest(testDispatcher) {
      val dateInMonth = System.currentTimeMillis()

      val account = AccountEntity(id = 1L, name = "Main", type = AccountType.BANK)
      fakeRepo.accFlow.value = listOf(account)

      fakeRepo.txFlow.value =
        listOf(
          Transaction(
            type = TransactionType.INCOME,
            categoryId = regularIncomeCatId,
            amount = regularIncomeAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "salary"
          ),
          Transaction(
            type = TransactionType.INCOME,
            categoryId = loansCatId,
            amount = loanIncomeAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "loan"
          ),
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = regularExpenseCatId,
            amount = regularExpenseAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "food"
          ),
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = loansCatId,
            amount = loanExpenseAmount,
            date = dateInMonth,
            accountId = 1L,
            description = "installment"
          )
        )

      // Empty categories -> no Loans category resolved -> all transactions included
      fakeRepo.catFlow.value = emptyList()

      backgroundScope.launch { viewModel.dashboardState.collect {} }
      advanceUntilIdle()

      val totalIncome = regularIncomeAmount + loanIncomeAmount
      val totalExpense = regularExpenseAmount + loanExpenseAmount
      val state =
        viewModel.dashboardState
          .filter {
            it.accounts.isNotEmpty() &&
              it.monthlyIncome == totalIncome &&
              it.monthlyExpenses == totalExpense
          }.first()
      assertEquals(
        "monthly income must include all transactions when no category excluded",
        totalIncome,
        state.monthlyIncome
      )
      assertEquals(
        "monthly expense must include all transactions when no category excluded",
        totalExpense,
        state.monthlyExpenses
      )
    }
}
