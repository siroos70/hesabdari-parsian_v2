package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBudgetForecastTest {
  @Test
  fun forecastReturnsNoDataMessageWhenEverythingEmpty() {
    val report =
      LocalBudgetForecast.forecast(
        transactions = emptyList(),
        loans = emptyList(),
        installments = emptyList(),
        bankLoans = emptyList()
      )
    assertTrue(report.contains("تراکنش یا قسطی برای پیش‌بینی ثبت نشده است"))
  }

  @Test
  fun forecastReturnsWarningWhenObligationsCauseSaturatingUnderflow() {
    val now = System.currentTimeMillis()
    val transactions =
      listOf(
        Transaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 1_000_000L,
          description = "income"
        )
      )
    val hugeLoans =
      listOf(
        Loan(
          personName = "Lender",
          type = LoanType.CREDITOR,
          originalAmount = Long.MAX_VALUE,
          remainingAmount = Long.MAX_VALUE,
          description = "huge loan",
          date = now
        )
      )
    val hugeInstallments =
      listOf(
        Installment(
          title = "Huge installment",
          amount = Long.MAX_VALUE,
          dueDate = now + 100_000L,
          isPaid = false
        )
      )

    val report =
      LocalBudgetForecast.forecast(
        transactions = transactions,
        loans = hugeLoans,
        installments = hugeInstallments,
        bankLoans = emptyList()
      )

    // With saturating subtraction, huge obligations clamp to Long.MIN_VALUE (< 0)
    // rather than wrapping around to a positive balance
    assertTrue(report.contains("هشدار هوشمند"))
    assertFalse(report.contains("وضعیت پایدار"))
  }

  @Test
  fun forecastReturnsStableStatusWhenBalancePositive() {
    val now = System.currentTimeMillis()
    val transactions =
      listOf(
        Transaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 10_000_000L,
          description = "income"
        ),
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = 2L,
          amount = 2_000_000L,
          description = "expense"
        )
      )
    val installments =
      listOf(
        Installment(
          title = "Installment",
          amount = 1_000_000L,
          dueDate = now + 100_000L,
          isPaid = false
        )
      )

    val report =
      LocalBudgetForecast.forecast(
        transactions = transactions,
        loans = emptyList(),
        installments = installments,
        bankLoans = emptyList()
      )

    assertTrue(report.contains("وضعیت پایدار"))
    assertFalse(report.contains("هشدار هوشمند"))
  }
}
