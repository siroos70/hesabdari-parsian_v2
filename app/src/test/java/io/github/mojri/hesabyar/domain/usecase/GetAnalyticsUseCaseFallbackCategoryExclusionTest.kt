package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GetAnalyticsUseCaseFallbackCategoryExclusionTest {
  private val useCase = GetAnalyticsUseCase()
  private val regularIncomeCategory = 10L
  private val regularExpenseCategory = 20L
  private val loansCategory = 999L

  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  private fun tx(
    type: TransactionType,
    amount: Long,
    categoryId: Long,
    date: Long,
  ) = Transaction(
    type = type,
    categoryId = categoryId,
    amount = amount,
    description = "test",
    date = date,
    accountId = 1L,
  )

  @Test
  fun computeFallbackAnalyticsEmptyExclusionIncludesLoansCategory() {
    val now = System.currentTimeMillis()
    val categories =
      listOf(
        analyticsCat(regularIncomeCategory, "Salary"),
        analyticsCat(regularExpenseCategory, "Food"),
        analyticsCat(loansCategory, "Loans"),
      )
    val transactions =
      listOf(
        tx(TransactionType.INCOME, 10_000_000L, regularIncomeCategory, now),
        tx(TransactionType.INCOME, 5_000_000L, loansCategory, now),
        tx(TransactionType.EXPENSE, 9_000_000L, regularExpenseCategory, now),
        tx(TransactionType.EXPENSE, 3_000_000L, loansCategory, now),
      )

    val result =
      useCase.computeAnalytics(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        categories = categories,
        excludedCategoryIds = emptyList(),
      )

    assertEquals(15_000_000L, result.monthlySpending.single().income)
    assertEquals(12_000_000L, result.monthlySpending.single().expense)
  }

  @Test
  fun computeFallbackAnalyticsPopulatedExclusionRemovesLoansTrends() {
    val now = System.currentTimeMillis()
    val categories =
      listOf(
        analyticsCat(regularIncomeCategory, "Salary"),
        analyticsCat(regularExpenseCategory, "Food"),
        analyticsCat(loansCategory, "Loans"),
      )
    val transactions =
      listOf(
        tx(TransactionType.INCOME, 10_000_000L, regularIncomeCategory, now),
        tx(TransactionType.INCOME, 5_000_000L, loansCategory, now),
        tx(TransactionType.EXPENSE, 9_000_000L, regularExpenseCategory, now),
        tx(TransactionType.EXPENSE, 3_000_000L, loansCategory, now),
      )

    val result =
      useCase.computeAnalytics(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        categories = categories,
        excludedCategoryIds = listOf(loansCategory),
      )

    assertEquals(10_000_000L, result.monthlySpending.single().income)
    assertEquals(9_000_000L, result.monthlySpending.single().expense)
    assertEquals(10_000_000L, result.monthlyIncome.single().income)
    assertTrue(result.categoryBreakdown.none { it.categoryId == loansCategory })
  }
}
