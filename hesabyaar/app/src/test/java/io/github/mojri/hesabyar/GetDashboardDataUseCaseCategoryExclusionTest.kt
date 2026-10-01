package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class GetDashboardDataUseCaseCategoryExclusionTest {
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

  private fun sampleTransactions(date: Long): List<Transaction> =
    listOf(
      tx(TransactionType.INCOME, 10_000_000L, regularIncomeCategory, date),
      tx(TransactionType.INCOME, 5_000_000L, loansCategory, date),
      tx(TransactionType.EXPENSE, 9_000_000L, regularExpenseCategory, date),
      tx(TransactionType.EXPENSE, 3_000_000L, loansCategory, date),
    )

  @Test
  fun computeFallbackDashboardDataEmptyExclusionIncludesLoansCategory() {
    val now = System.currentTimeMillis()
    val (monthStart, _) = JalaliCalendarHelper.getUtcJalaliMonthBoundaries(now)
    val date = monthStart + 1L
    val transactions = sampleTransactions(date)

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        accounts = listOf(account()),
        now = now,
        excludedCategoryIds = emptyList(),
      )

    assertEquals(15_000_000L, result.monthlyIncome)
    assertEquals(12_000_000L, result.monthlyExpenses)
    assertEquals(1.0 / 5.0, result.savingsRate, 1e-10)
    assertEquals(3_000_000L, result.currentBalance)
    assertEquals(15_000_000L, result.accounts.single().monthlyIncome)
    assertEquals(12_000_000L, result.accounts.single().monthlyExpenses)
  }

  @Test
  fun computeFallbackDashboardDataPopulatedExclusionRemovesLoansFromKpisOnly() {
    val now = System.currentTimeMillis()
    val (monthStart, _) = JalaliCalendarHelper.getUtcJalaliMonthBoundaries(now)
    val date = monthStart + 1L
    val transactions = sampleTransactions(date)

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        accounts = listOf(account()),
        now = now,
        excludedCategoryIds = listOf(loansCategory),
      )

    assertEquals(10_000_000L, result.monthlyIncome)
    assertEquals(9_000_000L, result.monthlyExpenses)
    assertEquals(0.1, result.savingsRate, 1e-10)
    assertEquals("Lifetime account balance keeps loan movements", 3_000_000L, result.currentBalance)
    assertEquals(10_000_000L, result.accounts.single().monthlyIncome)
    assertEquals(9_000_000L, result.accounts.single().monthlyExpenses)
    assertEquals("Account balance keeps loan movements", 3_000_000L, result.accounts.single().balance)
  }
}
