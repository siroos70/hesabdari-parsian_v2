package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.RustTest
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests the native FFI plumbing for the dynamic Loans-category KPI exclusion. */
@org.junit.experimental.categories.Category(RustTest::class)
class RustBridgeCategoryExclusionTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  private val regularIncomeCategory = 10L
  private val regularExpenseCategory = 20L
  private val loansCategory = 999L

  private fun account() =
    AccountEntity(
      id = 1L,
      name = "Main",
      type = AccountType.BANK,
    )

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

  private fun category(id: Long) =
    Category(
      id = id,
      name = "Category $id",
      key = "test-$id",
      icon = "",
      color = 0xFF000000L,
      type = CategoryType.BOTH,
    )

  private fun sampleTransactions(date: Long): List<Transaction> =
    listOf(
      tx(TransactionType.INCOME, 10_000_000L, regularIncomeCategory, date),
      tx(TransactionType.INCOME, 5_000_000L, loansCategory, date),
      tx(TransactionType.EXPENSE, 9_000_000L, regularExpenseCategory, date),
      tx(TransactionType.EXPENSE, 3_000_000L, loansCategory, date),
    )

  private fun sampleCategories(): List<Category> =
    listOf(
      category(regularIncomeCategory),
      category(regularExpenseCategory),
      category(loansCategory)
    )

  @Test
  fun computeDashboardDataForwardsExcludedCategoryIds() {
    val now = System.currentTimeMillis()
    val transactions = sampleTransactions(now)
    val accounts = listOf(account())

    val unfiltered =
      RustBridge.computeDashboardDataSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapLoans(emptyList()),
        RustMappers.mapInstallments(emptyList()),
        bankLoans = emptyList(),
        accounts = accounts,
        includeArchived = true,
        nowMs = now,
      )!!
    val filtered =
      RustBridge.computeDashboardDataSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapLoans(emptyList()),
        RustMappers.mapInstallments(emptyList()),
        bankLoans = emptyList(),
        accounts = accounts,
        includeArchived = true,
        nowMs = now,
        excludedCategoryIds = listOf(loansCategory),
      )!!

    assertEquals(15_000_000L, unfiltered.monthlyIncome)
    assertEquals(12_000_000L, unfiltered.monthlyExpenses)
    assertEquals(10_000_000L, filtered.monthlyIncome)
    assertEquals(9_000_000L, filtered.monthlyExpenses)
    assertEquals(3_000_000L, filtered.currentBalance)
    assertEquals(10_000_000L, filtered.accounts.single().monthlyIncome)
    assertEquals(9_000_000L, filtered.accounts.single().monthlyExpenses)
  }

  @Test
  fun computeAnalyticsForwardsExcludedCategoryIds() {
    val now = System.currentTimeMillis()
    val transactions = sampleTransactions(now)
    val categories = sampleCategories()

    val unfiltered =
      RustBridge.computeAnalyticsSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapLoans(emptyList()),
        RustMappers.mapInstallments(emptyList()),
        RustMappers.mapCategories(categories),
        accounts = listOf(account()),
        includeArchived = true,
      )!!
    val filtered =
      RustBridge.computeAnalyticsSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapLoans(emptyList()),
        RustMappers.mapInstallments(emptyList()),
        RustMappers.mapCategories(categories),
        accounts = listOf(account()),
        includeArchived = true,
        excludedCategoryIds = listOf(loansCategory),
      )!!

    assertTrue(unfiltered.monthlySpending.isNotEmpty())
    assertTrue(filtered.monthlySpending.isNotEmpty())
    assertEquals(15_000_000L, unfiltered.monthlySpending.first().income)
    assertEquals(12_000_000L, unfiltered.monthlySpending.first().expense)
    assertEquals(10_000_000L, filtered.monthlySpending.first().income)
    assertEquals(9_000_000L, filtered.monthlySpending.first().expense)
  }

  @Test
  fun offlineBudgetAdviceForwardsExcludedCategoryIds() {
    val transactions = sampleTransactions(0L)
    val categories = sampleCategories()

    val unfiltered =
      RustBridge.getOfflineBudgetAdviceSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapCategories(categories),
      )
    val filtered =
      RustBridge.getOfflineBudgetAdviceSync(
        RustMappers.mapTransactions(transactions),
        RustMappers.mapCategories(categories),
        listOf(loansCategory),
      )

    assertTrue("unfiltered advice must differ", unfiltered != filtered)
  }
}
