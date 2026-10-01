package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTest {
  private fun createTransaction(
    type: TransactionType,
    amount: Long,
    categoryId: Long = 8L
  ): Transaction = Transaction(type = type, amount = amount, categoryId = categoryId, description = "test")

  private fun createLoan(
    type: LoanType,
    originalAmount: Long,
    remainingAmount: Long,
    isSettled: Boolean = false
  ): Loan =
    Loan(
      personName = "test",
      type = type,
      originalAmount = originalAmount,
      remainingAmount = remainingAmount,
      description = "test",
      isSettled = isSettled
    )

  private fun createInstallment(
    amount: Long,
    isPaid: Boolean = false
  ): Installment = Installment(title = "test", amount = amount, dueDate = System.currentTimeMillis(), isPaid = isPaid)

  @Test
  fun `balance calculation - income minus expense`() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000), // 10M Rial
        createTransaction(TransactionType.EXPENSE, 3_000_000), // 3M Rial
        createTransaction(TransactionType.INCOME, 5_000_000), // 5M Rial
        createTransaction(TransactionType.EXPENSE, 2_000_000) // 2M Rial
      )

    var totalIncome = 0L
    var totalExpense = 0L
    transactions.forEach {
      if (it.type == TransactionType.INCOME) {
        totalIncome += it.amount
      } else {
        totalExpense += it.amount
      }
    }

    assertEquals(15_000_000L, totalIncome)
    assertEquals(5_000_000L, totalExpense)
    assertEquals(10_000_000L, totalIncome - totalExpense)
  }

  @Test
  fun `balance calculation - all expenses`() {
    val transactions =
      listOf(
        createTransaction(TransactionType.EXPENSE, 1_000_000),
        createTransaction(TransactionType.EXPENSE, 2_000_000)
      )

    var totalIncome = 0L
    var totalExpense = 0L
    transactions.forEach {
      if (it.type == TransactionType.INCOME) {
        totalIncome += it.amount
      } else {
        totalExpense += it.amount
      }
    }

    assertEquals(0L, totalIncome)
    assertEquals(3_000_000L, totalExpense)
    assertEquals(-3_000_000L, totalIncome - totalExpense)
  }

  @Test
  fun `balance calculation - empty list`() {
    val transactions = emptyList<Transaction>()
    var totalIncome = 0L
    var totalExpense = 0L
    transactions.forEach {
      if (it.type == TransactionType.INCOME) {
        totalIncome += it.amount
      } else {
        totalExpense += it.amount
      }
    }
    assertEquals(0L, totalIncome - totalExpense)
  }

  @Test
  fun `category totals grouping`() {
    val foodCategoryId = 1L
    val transportCategoryId = 2L
    val incomeCategoryId = 7L

    val transactions =
      listOf(
        createTransaction(TransactionType.EXPENSE, 1_000_000, foodCategoryId),
        createTransaction(TransactionType.EXPENSE, 2_000_000, foodCategoryId),
        createTransaction(TransactionType.EXPENSE, 500_000, transportCategoryId),
        createTransaction(TransactionType.INCOME, 10_000_000, incomeCategoryId)
      )

    val categoryTotals =
      transactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { entry -> entry.value.sumOf { it.amount } }

    assertEquals(3_000_000L, categoryTotals[foodCategoryId])
    assertEquals(500_000L, categoryTotals[transportCategoryId])
    assertEquals(null, categoryTotals[incomeCategoryId])
  }

  @Test
  fun `loan balance - remaining amount`() {
    val loans =
      listOf(
        createLoan(LoanType.DEBTOR, 5_000_000, 3_000_000),
        createLoan(LoanType.CREDITOR, 10_000_000, 10_000_000),
        createLoan(LoanType.DEBTOR, 2_000_000, 0L, isSettled = true)
      )

    var debtorsTotal = 0L
    var creditorsTotal = 0L
    loans.filter { !it.isSettled }.forEach {
      if (it.type == LoanType.DEBTOR) {
        debtorsTotal += it.remainingAmount
      } else {
        creditorsTotal += it.remainingAmount
      }
    }

    assertEquals(3_000_000L, debtorsTotal)
    assertEquals(10_000_000L, creditorsTotal)
  }

  @Test
  fun `installment - unpaid count and total`() {
    val installments =
      listOf(
        createInstallment(1_000_000, isPaid = false),
        createInstallment(2_000_000, isPaid = false),
        createInstallment(3_000_000, isPaid = true)
      )

    val unpaid = installments.filter { !it.isPaid }
    assertEquals(2, unpaid.size)
    assertEquals(3_000_000L, unpaid.sumOf { it.amount })
  }

  @Test
  fun `no floating point in monetary values`() {
    val amount: Long = 5_500_000
    val toman = amount / 10
    assertEquals(550_000L, toman)

    val amount2: Long = 1_234_567
    val toman2 = amount2 / 10
    assertEquals(123_456L, toman2)
  }
}
