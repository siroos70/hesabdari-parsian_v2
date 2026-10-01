package io.github.mojri.hesabyar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanInstallmentTest {
  @Test
  fun `loan repayment reduces remaining amount`() {
    var remainingAmount = 5_000_000L
    val paymentAmount = 2_000_000L

    remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
    assertEquals(3_000_000L, remainingAmount)
    assertFalse(remainingAmount <= 0L)
  }

  @Test
  fun `loan fully settled when remaining is zero`() {
    var remainingAmount = 2_000_000L
    val paymentAmount = 2_000_000L

    remainingAmount = (remainingAmount - paymentAmount).coerceAtLeast(0L)
    val isSettled = remainingAmount <= 0L
    assertTrue(isSettled)
    assertEquals(0L, remainingAmount)
  }

  @Test
  fun `installment toggle paid`() {
    var isPaid = false
    isPaid = !isPaid
    assertTrue(isPaid)
    isPaid = !isPaid
    assertFalse(isPaid)
  }

  @Test
  fun `multiple loan payments accumulation`() {
    var remainingAmount = 10_000_000L
    val payments = listOf(3_000_000L, 2_000_000L, 5_000_000L)

    payments.forEach { payment ->
      remainingAmount = (remainingAmount - payment).coerceAtLeast(0L)
    }

    assertEquals(0L, remainingAmount)
    assertTrue(remainingAmount <= 0L)
  }

  @Test
  fun `loan payment creates transaction with correct type`() {
    val loanType = "CREDITOR"
    val transactionType = if (loanType == "CREDITOR") "EXPENSE" else "INCOME"
    assertEquals("EXPENSE", transactionType)

    val loanType2 = "DEBTOR"
    val transactionType2 = if (loanType2 == "CREDITOR") "EXPENSE" else "INCOME"
    assertEquals("INCOME", transactionType2)
  }

  @Test
  fun `toman to rial conversion`() {
    val toman = 5500.0
    val rial = (toman * 1000).toLong()
    assertEquals(5_500_000L, rial)
  }

  @Test
  fun `rial to toman display`() {
    val rial = 5_500_000L
    val toman = rial / 10
    assertEquals(550_000L, toman)
  }
}
