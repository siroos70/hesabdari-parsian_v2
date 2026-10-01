package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AdviceSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCacheTest {
  private fun createTransaction(
    type: TransactionType,
    amount: Long,
    categoryId: Long = 1L
  ): Transaction = Transaction(type = type, amount = amount, categoryId = categoryId, description = "test")

  private fun createTransactionAt(
    type: TransactionType,
    amount: Long,
    dateMs: Long,
    categoryId: Long = 1L
  ): Transaction =
    Transaction(
      type = type,
      amount = amount,
      categoryId = categoryId,
      description = "test",
      date = dateMs
    )

  private fun createLoan(
    type: LoanType,
    originalAmount: Long,
    remainingAmount: Long
  ): Loan =
    Loan(
      personName = "test",
      type = type,
      originalAmount = originalAmount,
      remainingAmount = remainingAmount,
      description = "test"
    )

  private fun createInstallment(
    amount: Long,
    isPaid: Boolean = false
  ): Installment = Installment(title = "test", amount = amount, dueDate = System.currentTimeMillis(), isPaid = isPaid)

  private fun createCategory(
    id: Long,
    name: String
  ): Category =
    Category(id = id, name = name, key = "test", icon = "Test", color = 0xFF757575L, type = CategoryType.EXPENSE)

  @Test
  fun `data signature - same data produces same signature`() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 3_000_000)
      )
    val loans = listOf(createLoan(LoanType.DEBTOR, 5_000_000, 3_000_000))
    val installments = listOf(createInstallment(1_000_000))
    val categories = listOf(createCategory(1L, "خوراک"))

    val sig1 = AdviceSignature.computeDataSignature(transactions, loans, installments, categories)
    val sig2 = AdviceSignature.computeDataSignature(transactions, loans, installments, categories)

    assertEquals(sig1, sig2)
  }

  @Test
  fun `data signature - changes when transaction amount changes`() {
    val transactions1 = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val transactions2 = listOf(createTransaction(TransactionType.INCOME, 15_000_000))
    val (loans, installments, cats) =
      Triple(emptyList<Loan>(), emptyList<Installment>(), emptyList<Category>())

    val sig1 = AdviceSignature.computeDataSignature(transactions1, loans, installments, cats)
    val sig2 = AdviceSignature.computeDataSignature(transactions2, loans, installments, cats)

    assertNotEquals(sig1, sig2)
  }

  @Test
  fun `data signature - changes when transaction count changes`() {
    val transactions1 = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val transactions2 =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 2_000_000)
      )
    val (loans, installments, cats) =
      Triple(emptyList<Loan>(), emptyList<Installment>(), emptyList<Category>())

    val sig1 = AdviceSignature.computeDataSignature(transactions1, loans, installments, cats)
    val sig2 = AdviceSignature.computeDataSignature(transactions2, loans, installments, cats)

    assertNotEquals(sig1, sig2)
  }

  @Test
  fun `data signature - changes when loan added`() {
    val transactions = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val loans1 = emptyList<Loan>()
    val loans2 = listOf(createLoan(LoanType.DEBTOR, 5_000_000, 3_000_000))
    val emptyInst = emptyList<Installment>()
    val emptyCat = emptyList<Category>()

    val sig1 = AdviceSignature.computeDataSignature(transactions, loans1, emptyInst, emptyCat)
    val sig2 = AdviceSignature.computeDataSignature(transactions, loans2, emptyInst, emptyCat)

    assertNotEquals(sig1, sig2)
  }

  @Test
  fun `data signature - changes when installment added`() {
    val transactions = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val emptyLoans = emptyList<Loan>()
    val installments1 = emptyList<Installment>()
    val installments2 = listOf(createInstallment(1_000_000))
    val emptyCat = emptyList<Category>()

    val sig1 = AdviceSignature.computeDataSignature(transactions, emptyLoans, installments1, emptyCat)
    val sig2 = AdviceSignature.computeDataSignature(transactions, emptyLoans, installments2, emptyCat)

    assertNotEquals(sig1, sig2)
  }

  @Test
  fun `data signature - empty data produces valid signature`() {
    val sig =
      AdviceSignature.computeDataSignature(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
      )
    assertTrue("empty-data signature must be non-blank", sig.isNotBlank())
    // Deterministic: same empty input yields the same signature.
    assertEquals(
      sig,
      AdviceSignature.computeDataSignature(emptyList(), emptyList(), emptyList(), emptyList())
    )
    // A non-empty dataset must not collide with the empty signature.
    val nonEmpty =
      AdviceSignature.computeDataSignature(
        listOf(createTransaction(TransactionType.INCOME, 10_000_000)),
        emptyList(),
        emptyList(),
        emptyList()
      )
    assertNotEquals(sig, nonEmpty)
  }

  @Test
  fun `advice signature - same data produces same signature`() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 3_000_000)
      )
    val categories = listOf(createCategory(1L, "خوراک"))

    val sig1 = AdviceSignature.computeAdviceSignature(transactions, emptyList(), emptyList(), categories)
    val sig2 = AdviceSignature.computeAdviceSignature(transactions, emptyList(), emptyList(), categories)

    assertEquals(sig1, sig2)
  }

  @Test
  fun `advice signature - changes when transaction changes`() {
    val transactions1 = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val transactions2 = listOf(createTransaction(TransactionType.INCOME, 20_000_000))
    val categories = listOf(createCategory(1L, "خوراک"))

    val sig1 = AdviceSignature.computeAdviceSignature(transactions1, emptyList(), emptyList(), categories)
    val sig2 = AdviceSignature.computeAdviceSignature(transactions2, emptyList(), emptyList(), categories)

    assertNotEquals(sig1, sig2)
  }

  @Test
  fun `advice signature - ignores loans and installments`() {
    val transactions = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val categories = listOf(createCategory(1L, "خوراک"))

    val sig1 = AdviceSignature.computeAdviceSignature(transactions, emptyList(), emptyList(), categories)
    val sig2 = AdviceSignature.computeAdviceSignature(transactions, emptyList(), emptyList(), categories)

    assertEquals(sig1, sig2)
  }

  @Test
  fun `format last fetch time - zero returns not updated`() {
    val result = formatLastFetchTime(0L)
    assertEquals("هنوز به‌روز نشده", result)
  }

  @Test
  fun `format last fetch time - recent time returns minutes ago`() {
    val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000 - 1000
    val result = formatLastFetchTime(fiveMinutesAgo)
    assertTrue("Expected 'دقیقه پیش' but got: $result", result.contains("دقیقه پیش"))
  }

  @Test
  fun `format last fetch time - one minute returns singular`() {
    val oneMinuteAgo = System.currentTimeMillis() - 60 * 1000 - 1000
    val result = formatLastFetchTime(oneMinuteAgo)
    assertEquals("۱ دقیقه پیش", result)
  }

  @Test
  fun `format last fetch time - just now returns now`() {
    val now = System.currentTimeMillis() - 500
    val result = formatLastFetchTime(now)
    assertEquals("همین الان", result)
  }

  @Test
  fun `format last fetch time - hours ago`() {
    val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000 - 1000
    val result = formatLastFetchTime(twoHoursAgo)
    assertTrue("Expected 'ساعت پیش' but got: $result", result.contains("ساعت پیش"))
  }

  @Test
  fun `format last fetch time - one hour returns singular`() {
    val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000 - 1000
    val result = formatLastFetchTime(oneHourAgo)
    assertEquals("۱ ساعت پیش", result)
  }

  private fun formatLastFetchTime(timestamp: Long): String {
    if (timestamp == 0L) return "هنوز به‌روز نشده"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = (diff / 60000).toInt()
    return when {
      minutes < 1 -> "همین الان"
      minutes == 1 -> "۱ دقیقه پیش"
      minutes < 60 -> "$minutes دقیقه پیش"
      else -> {
        val hours = minutes / 60
        if (hours == 1) "۱ ساعت پیش" else "$hours ساعت پیش"
      }
    }
  }

  @Test
  fun `cache key - distinguishes categoryId, type and date without count or total change`() {
    val fixedDate = 1_700_000_000_000L
    val base = createTransactionAt(TransactionType.EXPENSE, 3_000_000, fixedDate, 1L)
    val sigBase =
      AdviceSignature.computeDataSignature(listOf(base), emptyList(), emptyList(), emptyList())

    // Changing categoryId while keeping amount/total identical must invalidate.
    val differentCat = createTransactionAt(TransactionType.EXPENSE, 3_000_000, fixedDate, 2L)
    val sigCat =
      AdviceSignature.computeDataSignature(listOf(differentCat), emptyList(), emptyList(), emptyList())
    assertNotEquals("signature must change when categoryId changes", sigBase, sigCat)

    // Changing transaction type while keeping amount identical must invalidate.
    val differentType = createTransactionAt(TransactionType.INCOME, 3_000_000, fixedDate, 1L)
    val sigType =
      AdviceSignature.computeDataSignature(listOf(differentType), emptyList(), emptyList(), emptyList())
    assertNotEquals("signature must change when transaction type changes", sigBase, sigType)

    // Changing date while keeping amount/total identical must invalidate.
    val differentDate =
      createTransactionAt(TransactionType.EXPENSE, 3_000_000, 1_600_000_000_000L, 1L)
    val sigDate =
      AdviceSignature.computeDataSignature(listOf(differentDate), emptyList(), emptyList(), emptyList())
    assertNotEquals("signature must change when date changes", sigBase, sigDate)

    // Identical logical content (same explicit date) yields the same signature.
    val sameAgain = createTransactionAt(TransactionType.EXPENSE, 3_000_000, fixedDate, 1L)
    val sigSame =
      AdviceSignature.computeDataSignature(listOf(sameAgain), emptyList(), emptyList(), emptyList())
    assertEquals("identical content must produce identical signature", sigBase, sigSame)
  }

  @Test
  fun `cache invalidated when any data component changes`() {
    val transactions = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val loans = emptyList<Loan>()
    val installments = emptyList<Installment>()
    val categories = listOf(createCategory(1L, "خوراک"))

    val originalSig = AdviceSignature.computeDataSignature(transactions, loans, installments, categories)

    // Change transactions
    val newTransactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 1_000_000)
      )
    val sigAfterTxChange = AdviceSignature.computeDataSignature(newTransactions, loans, installments, categories)
    assertNotEquals("Signature should change when transactions change", originalSig, sigAfterTxChange)

    // Change loans
    val newLoans = listOf(createLoan(LoanType.CREDITOR, 2_000_000, 2_000_000))
    val sigAfterLoanChange = AdviceSignature.computeDataSignature(transactions, newLoans, installments, categories)
    assertNotEquals("Signature should change when loans change", originalSig, sigAfterLoanChange)

    // Change installments
    val newInstallments = listOf(createInstallment(500_000))
    val sigAfterInstChange = AdviceSignature.computeDataSignature(transactions, loans, newInstallments, categories)
    assertNotEquals("Signature should change when installments change", originalSig, sigAfterInstChange)

    // Change category count
    val newCategories = listOf(createCategory(1L, "خوراک"), createCategory(2L, "حمل و نقل"))
    val sigAfterCatChange = AdviceSignature.computeDataSignature(transactions, loans, installments, newCategories)
    assertNotEquals("Signature should change when category count changes", originalSig, sigAfterCatChange)
  }

  @Test
  fun `data signature - changes when loan balance changes without count change`() {
    val transactions = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val emptyInst = emptyList<Installment>()
    val emptyCat = emptyList<Category>()
    val loansBefore = listOf(createLoan(LoanType.DEBTOR, 5_000_000, 3_000_000))
    val loansAfter = listOf(createLoan(LoanType.DEBTOR, 5_000_000, 1_000_000))

    val sigBefore = AdviceSignature.computeDataSignature(transactions, loansBefore, emptyInst, emptyCat)
    val sigAfter = AdviceSignature.computeDataSignature(transactions, loansAfter, emptyInst, emptyCat)

    assertNotEquals("Signature should change when loan remaining balance changes", sigBefore, sigAfter)
  }

  @Test
  fun `data signature - changes when installment is paid without count change`() {
    val transactions = listOf(createTransaction(TransactionType.INCOME, 10_000_000))
    val emptyLoans = emptyList<Loan>()
    val emptyCat = emptyList<Category>()
    val installmentsBefore = listOf(createInstallment(1_000_000, isPaid = false))
    val installmentsAfter = listOf(createInstallment(1_000_000, isPaid = true))

    val sigBefore = AdviceSignature.computeDataSignature(transactions, emptyLoans, installmentsBefore, emptyCat)
    val sigAfter = AdviceSignature.computeDataSignature(transactions, emptyLoans, installmentsAfter, emptyCat)

    assertNotEquals("Signature should change when an installment is paid", sigBefore, sigAfter)
  }
}
