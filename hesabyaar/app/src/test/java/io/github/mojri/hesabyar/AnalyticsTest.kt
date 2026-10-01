package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.CategoryBreakdown
import io.github.mojri.hesabyar.ui.DebtSummary
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsTest {
  private fun createTransaction(
    type: TransactionType,
    amount: Long,
    categoryId: Long = 1L,
    date: Long = System.currentTimeMillis()
  ): Transaction = Transaction(type = type, amount = amount, categoryId = categoryId, description = "test", date = date)

  private fun createLoan(
    type: LoanType,
    originalAmount: Long,
    remainingAmount: Long,
    personName: String = "test",
    isSettled: Boolean = false
  ): Loan =
    Loan(
      personName = personName,
      type = type,
      originalAmount = originalAmount,
      remainingAmount = remainingAmount,
      description = "test",
      isSettled = isSettled
    )

  private fun createInstallment(
    title: String = "test",
    amount: Long,
    isPaid: Boolean = false,
    dueDate: Long = System.currentTimeMillis()
  ): Installment = Installment(title = title, amount = amount, dueDate = dueDate, isPaid = isPaid)

  private fun createCategory(
    id: Long,
    name: String,
    color: Long = 0xFF757575L
  ): Category = Category(id = id, name = name, key = "test", icon = "Test", color = color, type = CategoryType.EXPENSE)

  @Test
  fun `monthly spending grouping by jalali month`() {
    // Create transactions in different months
    val now = System.currentTimeMillis()
    val oneMonthMs = 45L * 24 * 60 * 60 * 1000

    val transactions =
      listOf(
        createTransaction(TransactionType.EXPENSE, 1_000_000, date = now),
        createTransaction(TransactionType.EXPENSE, 2_000_000, date = now),
        createTransaction(TransactionType.EXPENSE, 3_000_000, date = now - oneMonthMs)
      )

    val monthlyMap = mutableMapOf<String, Long>()
    transactions.filter { it.type == TransactionType.EXPENSE }.forEach { t ->
      val jd = JalaliCalendarHelper.gregorianToJalali(t.date)
      val key = "${jd.year}_${jd.month}"
      monthlyMap[key] = (monthlyMap[key] ?: 0L) + t.amount
    }

    // Should have 2 months
    assertEquals(2, monthlyMap.size)
  }

  @Test
  fun `category breakdown calculation`() {
    val categories =
      listOf(
        createCategory(1L, "خوراک"),
        createCategory(2L, "حمل و نقل")
      )

    val transactions =
      listOf(
        createTransaction(TransactionType.EXPENSE, 1_000_000, categoryId = 1L),
        createTransaction(TransactionType.EXPENSE, 2_000_000, categoryId = 1L),
        createTransaction(TransactionType.EXPENSE, 500_000, categoryId = 2L)
      )

    val categoryTotals = mutableMapOf<Long, Long>()
    transactions.filter { it.type == TransactionType.EXPENSE }.forEach { t ->
      categoryTotals[t.categoryId] = (categoryTotals[t.categoryId] ?: 0L) + t.amount
    }

    val totalExpense = categoryTotals.values.sum()
    val breakdown =
      categoryTotals
        .map { (catId, total) ->
          val cat = categories.find { it.id == catId }
          CategoryBreakdown(
            categoryId = catId,
            categoryName = cat?.name ?: "سایر",
            color = cat?.color ?: 0xFF757575L,
            total = total,
            percentage = if (totalExpense > 0) total.toFloat() / totalExpense else 0f
          )
        }.sortedByDescending { it.total }

    assertEquals(2, breakdown.size)
    assertEquals("خوراک", breakdown[0].categoryName)
    assertEquals(3_000_000L, breakdown[0].total)
    assertEquals(3_000_000L + 500_000L, totalExpense)
  }

  @Test
  fun `debt summary progress calculation`() {
    val loans =
      listOf(
        createLoan(LoanType.DEBTOR, 5_000_000, 3_000_000, "علی"),
        createLoan(LoanType.CREDITOR, 10_000_000, 5_000_000, "محمد")
      )

    val debtors =
      loans.filter { it.type == LoanType.DEBTOR && !it.isSettled }.map { loan ->
        DebtSummary(
          personName = loan.personName,
          originalAmount = loan.originalAmount,
          remainingAmount = loan.remainingAmount,
          type = loan.type.name,
          progress =
            if (loan.originalAmount > 0) {
              1f - loan.remainingAmount.toFloat() / loan.originalAmount
            } else {
              0f
            }
        )
      }

    assertEquals(1, debtors.size)
    assertEquals("علی", debtors[0].personName)
    // Progress should be 40% (2M paid out of 5M)
    assertEquals(0.4f, debtors[0].progress, 0.01f)
  }

  @Test
  fun `installment progress calculation`() {
    val installments =
      listOf(
        createInstallment("قسط ۱", 1_000_000, isPaid = true),
        createInstallment("قسط ۲", 1_000_000, isPaid = false),
        createInstallment("قسط ۳", 1_000_000, isPaid = true)
      )

    val paidCount = installments.count { it.isPaid }
    val totalCount = installments.size

    assertEquals(2, paidCount)
    assertEquals(3, totalCount)
    assertEquals(2f / 3f, paidCount.toFloat() / totalCount, 0.01f)
  }

  @Test
  fun `empty data handling`() {
    val transactions = emptyList<Transaction>()
    val loans = emptyList<Loan>()
    val installments = emptyList<Installment>()

    val monthlyMap = mutableMapOf<String, Long>()
    val categoryTotals = mutableMapOf<Long, Long>()
    val unsettledLoans = loans.filter { !it.isSettled }

    assertEquals(0, monthlyMap.size)
    assertEquals(0, categoryTotals.size)
    assertEquals(0, unsettledLoans.size)
    assertEquals(0, installments.size)
  }

  @Test
  fun `jalali month name mapping`() {
    val monthNames =
      mapOf(
        1 to "فروردین",
        2 to "اردیبهشت",
        3 to "خرداد",
        4 to "تیر",
        5 to "مرداد",
        6 to "شهریور",
        7 to "مهر",
        8 to "آبان",
        9 to "آذر",
        10 to "دی",
        11 to "بهمن",
        12 to "اسفند"
      )

    assertEquals("فروردین", monthNames[1])
    assertEquals("اسفند", monthNames[12])
    assertEquals(12, monthNames.size)
  }

  @Test
  fun `total debt and credit calculation`() {
    val loans =
      listOf(
        createLoan(LoanType.DEBTOR, 5_000_000, 3_000_000, "علی"),
        createLoan(LoanType.DEBTOR, 2_000_000, 1_000_000, "حسن"),
        createLoan(LoanType.CREDITOR, 10_000_000, 5_000_000, "محمد"),
        createLoan(LoanType.CREDITOR, 3_000_000, 0L, "رضا", isSettled = true)
      )

    val unsettledLoans = loans.filter { !it.isSettled }
    val totalDebt = unsettledLoans.filter { it.type == LoanType.DEBTOR }.sumOf { it.remainingAmount }
    val totalCredit = unsettledLoans.filter { it.type == LoanType.CREDITOR }.sumOf { it.remainingAmount }

    assertEquals(4_000_000L, totalDebt) // 3M + 1M
    assertEquals(5_000_000L, totalCredit) // Only unsettled
  }

  @Test
  fun `loan progress percentage`() {
    val loan = createLoan(LoanType.DEBTOR, 10_000_000, 4_000_000)

    val progress =
      if (loan.originalAmount > 0) {
        1f - loan.remainingAmount.toFloat() / loan.originalAmount
      } else {
        0f
      }

    // 6M paid out of 10M = 60%
    assertEquals(0.6f, progress, 0.01f)
  }

  @Test
  fun `category percentage calculation`() {
    val expenses =
      listOf(
        createTransaction(TransactionType.EXPENSE, 600_000, categoryId = 1L),
        createTransaction(TransactionType.EXPENSE, 400_000, categoryId = 2L)
      )

    val total = expenses.sumOf { it.amount }
    val cat1Percentage = expenses.filter { it.categoryId == 1L }.sumOf { it.amount }.toFloat() / total

    assertEquals(1_000_000L, total)
    assertEquals(0.6f, cat1Percentage, 0.01f)
  }
}
