package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.BudgetAdviceGenerator
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Currency-scale accuracy tests for the offline budget advice path.
 *
 * The database stores every monetary amount in Rial. Any string that is labelled
 * "تومان" must therefore be divided by 10 before formatting, otherwise advice is
 * presented 10x larger than reality. These tests lock that invariant for the
 * offline advice generator (loans/installments and the data summary).
 *
 * Isolation is provided by [RustTest] category routing (forkEvery=1 in
 * testDebugUnitTestRust), NOT by [RustIsolationRule] which would disable the
 * native path this class needs to exercise.
 */
@Category(RustTest::class)
class BudgetAdviceGeneratorTest {
  private fun createInstallment(
    title: String = "test",
    amount: Long,
    isPaid: Boolean = false
  ): Installment =
    Installment(
      title = title,
      amount = amount,
      dueDate = System.currentTimeMillis() + 24L * 60L * 60L * 1000L,
      isPaid = isPaid
    )

  private fun createLoan(
    type: LoanType,
    originalAmount: Long,
    remainingAmount: Long,
    personName: String = "test"
  ): Loan =
    Loan(
      personName = personName,
      type = type,
      originalAmount = originalAmount,
      remainingAmount = remainingAmount,
      description = "test"
    )

  // ---------------------------------------------------------------------------
  // handleAdviceResult — AI validation failure paths
  // ---------------------------------------------------------------------------

  @Test
  fun handleAdviceResultInvalidAiAdviceFallsBackToOfflineWhenRustAvailable() =
    runTest {
      // "سلام" is too short for the native validator, so with Rust available the
      // result must be discarded and the offline advice returned (never the raw
      // unsanitized AI text).
      val rawAiText = "سلام"
      val expectedFallback =
        BudgetAdviceGenerator.getBudgetAdviceOffline(
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList()
        )
      val result =
        BudgetAdviceGenerator.handleAdviceResult(
          AiProvider.ApiResult.Success(rawAiText),
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList()
        )
      assertEquals("invalid AI advice must fall back to offline", expectedFallback, result)
      assertFalse("raw unsanitized AI text must not be returned", result == rawAiText)
    }

  @Test
  fun handleAdviceResultValidAiAdviceIsReturnedAsisWhenRustAvailable() =
    runTest {
      // A well-formed Persian response passes validation and is surfaced directly.
      val aiText = "شما در ماه گذشته بیست درصد از درآمد خود را پس انداز کرده اید."
      val result =
        BudgetAdviceGenerator.handleAdviceResult(
          AiProvider.ApiResult.Success(aiText),
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList()
        )
      assertEquals("valid AI advice must be returned unchanged", aiText, result)
    }

  @Test
  fun installmentAdviceDividesRialBy10AndLabelsToman() {
    // 10,000,000 Rial installment must read as "1,000,000 تومان".
    val installments = listOf(createInstallment("قسط ماشین", 10_000_000, isPaid = false))
    val result =
      BudgetAdviceGenerator.getBudgetAdviceOffline(
        emptyList(),
        emptyList(),
        installments,
        emptyList()
      )
    assertTrue(
      "expected Toman-scaled amount 1000000 تومان, got: $result",
      result.contains("1000000 تومان")
    )
    assertFalse(
      "must not expose raw Rial magnitude 10000000 تومان, got: $result",
      result.contains("10000000 تومان")
    )
  }

  @Test
  fun loanAdviceDividesRialBy10AndLabelsToman() {
    // The loan original/remaining amounts surface in the data summary that is
    // fed to the AI prompt. 20,000,000 Rial → "2,000,000 تومان",
    // 12,000,000 Rial → "1,200,000 تومان".
    val loans = listOf(createLoan(LoanType.CREDITOR, 20_000_000, 12_000_000, "علی"))
    val summary =
      BudgetAdviceGenerator.buildDataSummary(
        emptyList(),
        loans,
        emptyList(),
        emptyList()
      )
    assertTrue(
      "expected Toman-scaled original amount 2000000 تومان, got: $summary",
      summary.contains("2000000 تومان")
    )
    assertTrue(
      "expected Toman-scaled remaining amount 1200000 تومان, got: $summary",
      summary.contains("1200000 تومان")
    )
    assertFalse(
      "must not expose raw Rial magnitude 20000000 تومان, got: $summary",
      summary.contains("20000000 تومان")
    )
  }

  @Test
  fun transactionBasedAdviceSummaryDividesRialBy10AndLabelsToman() {
    // A 10,000,000 Rial income + 10,000,000 Rial expense must read as
    // "1,000,000 تومان" in the data summary (income/expense/balance).
    val transactions =
      listOf(
        io.github.mojri.hesabyar.data.Transaction(
          type = io.github.mojri.hesabyar.data.TransactionType.INCOME,
          amount = 10_000_000,
          categoryId = 1L,
          description = "حقوق"
        ),
        io.github.mojri.hesabyar.data.Transaction(
          type = io.github.mojri.hesabyar.data.TransactionType.EXPENSE,
          amount = 10_000_000,
          categoryId = 1L,
          description = "خرج"
        )
      )
    val summary =
      BudgetAdviceGenerator.buildDataSummary(
        transactions,
        emptyList(),
        emptyList(),
        emptyList()
      )
    assertTrue(
      "expected Toman-scaled income 1000000 تومان, got: $summary",
      summary.contains("1000000 تومان")
    )
    assertFalse(
      "must not expose raw Rial magnitude 10000000 تومان, got: $summary",
      summary.contains("10000000 تومان")
    )
  }

  @Test
  fun getBudgetAdviceExceptionDuringAiGenerateFallsBackToOffline() =
    runTest {
      val config =
        io.github.mojri.hesabyar.api
          .AiProviderConfig(apiKey = "valid_key")
      val tx =
        io.github.mojri.hesabyar.data.Transaction(
          type = io.github.mojri.hesabyar.data.TransactionType.INCOME,
          amount = 10_000_000L,
          categoryId = 1L,
          description = "income"
        )
      val expectedOffline =
        BudgetAdviceGenerator.getBudgetAdviceOffline(
          listOf(tx),
          emptyList(),
          emptyList(),
          emptyList()
        )
      val result =
        BudgetAdviceGenerator.getBudgetAdvice(
          transactions = listOf(tx),
          loans = emptyList(),
          installments = emptyList(),
          categories = emptyList(),
          config = config,
          aiGenerate = { _, _, _, _ -> throw java.io.IOException("Simulated network failure") }
        )
      assertEquals("exception in aiGenerate must trigger offline advice fallback", expectedOffline, result)
    }

  @Test
  fun getBudgetAdviceCancellationExceptionIsRethrown() =
    runTest {
      val config =
        io.github.mojri.hesabyar.api
          .AiProviderConfig(apiKey = "valid_key")
      val tx =
        io.github.mojri.hesabyar.data.Transaction(
          type = io.github.mojri.hesabyar.data.TransactionType.INCOME,
          amount = 10_000_000L,
          categoryId = 1L,
          description = "income"
        )
      try {
        BudgetAdviceGenerator.getBudgetAdvice(
          transactions = listOf(tx),
          loans = emptyList(),
          installments = emptyList(),
          categories = emptyList(),
          config = config,
          aiGenerate = { _, _, _, _ -> throw kotlinx.coroutines.CancellationException("Simulated cancellation") }
        )
        org.junit.Assert.fail("CancellationException must be rethrown")
      } catch (e: kotlinx.coroutines.CancellationException) {
        assertEquals("Simulated cancellation", e.message)
      }
    }
}
