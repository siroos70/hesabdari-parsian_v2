package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.api.LocalBudgetAdvice
import io.github.mojri.hesabyar.api.LocalBudgetMetrics
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Kotlin-fallback coverage for [BudgetAdvisor.calculateFinancialHealthScore].
 *
 * The @Before forces the Rust availability decision off, so these tests
 * genuinely exercise [BudgetAdvisor]'s local fallback path even though the
 * native `hesabyar_core` library sits on the test class path for every test
 * task (see the `rustJvmArgs` wiring in `app/build.gradle.kts`).
 *
 * This class is intentionally **not** tagged with `@Category(RustTest::class)`
 * — it belongs to the fast `testDebugUnitTest` suite alongside the other
 * fallback tests (e.g. [GetAnalyticsUseCaseFallbackTest]).
 * [BudgetAdvisorTest] is the native-path counterpart.
 */
class BudgetAdvisorFallbackTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  private fun createTransaction(
    type: TransactionType,
    amount: Long,
    categoryId: Long = 1L,
    date: Long = System.currentTimeMillis()
  ): Transaction = Transaction(type = type, amount = amount, categoryId = categoryId, description = "test", date = date)

  private fun createBankLoan(isSettled: Boolean): BankLoan =
    BankLoan(
      bankName = "بانک ملی",
      loanName = "وام خودرو",
      receivedAmount = 10_000_000,
      monthlyInstallmentAmount = 1_000_000,
      numberOfInstallments = 12,
      totalRepayableAmount = 12_000_000,
      totalInterest = 2_000_000,
      startDate = 1_700_000_000_000,
      description = "",
      isSettled = isSettled
    )

  private fun createSettledLoan(): Loan =
    Loan(
      personName = "test",
      type = LoanType.CREDITOR,
      originalAmount = 10_000_000,
      remainingAmount = 10_000_000,
      description = "test",
      isSettled = true
    )

  @Test
  fun calculatefinancialhealthscoreLocalFallbackWhenRustUnavailable() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 2_000_000)
      )
    val score =
      BudgetAdvisor.calculateFinancialHealthScore(
        transactions,
        emptyList(),
        emptyList(),
        emptyList()
      )
    // Deterministic local computation: savings rate 0.8 (+25) + no debt (+15) + 1 category (+0) = 90.
    assertEquals(90, score)
    assertTrue(score in 0..100)

    // Determinism: a second call yields the same result (no flaky time dependence).
    assertEquals(
      score,
      BudgetAdvisor.calculateFinancialHealthScore(transactions, emptyList(), emptyList(), emptyList())
    )
  }

  @Test
  fun calculatefinancialhealthscoreEmptyDataReturnsZeroViaLocalFallback() {
    assertEquals(
      0,
      BudgetAdvisor.calculateFinancialHealthScore(emptyList(), emptyList(), emptyList(), emptyList())
    )
  }

  @Test
  fun getofflineforecastSettledBankLoansStillNoDataViaLocalFallback() {
    // A settled bank loan is not an active obligation and must not suppress the
    // "no data" message — parity with the Rust get_offline_forecast guard.
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        emptyList(),
        listOf(createBankLoan(isSettled = true))
      )
    assertTrue(
      "settled bank loan must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastSettledLoansStillNoDataViaLocalFallback() {
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        listOf(createSettledLoan()),
        emptyList(),
        emptyList()
      )
    assertTrue(
      "settled loan must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastPaidInstallmentsOnlyNoDataViaLocalFallback() {
    // Only paid installments — no unpaid installments due within 30 days.
    // Parity with Rust get_offline_forecast: total_obligations only counts
    // unpaid upcoming installments, so paid installments must not suppress
    // the "no data" message.
    val paidInstallment =
      Installment(
        title = "قسط ماهانه",
        amount = 1_000_000,
        dueDate = 1_700_000_000_000L,
        isPaid = true
      )
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        listOf(paidInstallment),
        emptyList()
      )
    assertTrue(
      "paid installments must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastDebtorLoansOnlyNoDataViaLocalFallback() {
    // Only unsettled DEBTOR loans — Rust's total_obligations only counts
    // unsettled CREDITOR loans, so DEBTOR loans must not suppress the
    // "no data" message.
    val debtorLoan =
      Loan(
        personName = "طرف مقابل",
        type = LoanType.DEBTOR,
        originalAmount = 5_000_000,
        remainingAmount = 5_000_000,
        description = "وام شخصی",
        isSettled = false
      )
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        listOf(debtorLoan),
        emptyList(),
        emptyList()
      )
    assertTrue(
      "DEBTOR loan must not produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastOverflowingCreditorLoansProduceForecastViaLocalFallback() {
    // With enough large CREDITOR loans, sumOf { remainingAmount / 12 } would
    // overflow and wrap to 0, causing hasNoData to return true despite active
    // obligations. 24 loans of Long.MAX_VALUE each contribute
    // 768614336404564650 (MAX/12), summing to 18,446,744,073,709,551,600.
    // One more loan of 192 (192/12 = 16) brings the total to 2^64, which
    // wraps to 0 in Kotlin Long arithmetic. The saturating fold clamps to
    // Long.MAX_VALUE instead, keeping hasNoData false.
    val largeCreditorLoans =
      List(24) {
        Loan(
          personName = "creditor $it",
          type = LoanType.CREDITOR,
          originalAmount = Long.MAX_VALUE,
          remainingAmount = Long.MAX_VALUE,
          description = "test",
          isSettled = false
        )
      } +
        listOf(
          Loan(
            personName = "creditor 24",
            type = LoanType.CREDITOR,
            originalAmount = 192L,
            remainingAmount = 192L,
            description = "test",
            isSettled = false
          )
        )
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        largeCreditorLoans,
        emptyList(),
        emptyList()
      )
    assertFalse(
      "overflowing creditor loans must not trigger no-data, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastActiveBankLoanProducesForecastViaLocalFallback() {
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        emptyList(),
        listOf(createBankLoan(isSettled = false))
      )
    assertFalse(
      "active bank loan must produce a forecast, got: $result",
      result.contains("ثبت نشده")
    )
  }

  @Test
  fun localMonthlyIncomeBaselineMatchesRustAbove2Pow53() {
    // Parity with Rust monthly_income_baseline (BudgetAdvisor.kt:544). For sums
    // above 2^53, the old f64 path (sum.toDouble() / months) rounded sum to
    // 9_007_199_254_740_992, yielding 6_004_799_503_160_661 instead of the exact
    // 6_004_799_503_160_662. Integer arithmetic (sum * 30 / days) stays exact.
    val nowMs: Long = 1_700_000_000_000
    val dayMs: Long = 24 * 60 * 60 * 1000L
    val tx =
      createTransaction(
        TransactionType.INCOME,
        9_007_199_254_740_993L, // 2^53 + 1 — not exactly representable as f64.
        date = nowMs - 45 * dayMs
      )
    val result = LocalBudgetMetrics.monthlyIncomeBaseline(listOf(tx), nowMs)
    // Rust: (9_007_199_254_740_993 * 30) / 45 = 6_004_799_503_160_662.
    assertEquals(6_004_799_503_160_662L, result)
  }

  @Test
  fun localMonthlyIncomeBaselineMatchesRustAboveLongMaxDiv30() {
    // sum = 10^18 exceeds Long.MAX_VALUE / 30 (≈3.07e17). Rust computes
    // (10^18 * 30) / 45 = 666_666_666_666_666_666 with i128. BigInteger matches
    // this; the old clamp-to-Long.MAX_VALUE path underreported by ~3x.
    val nowMs: Long = 1_700_000_000_000
    val dayMs: Long = 24 * 60 * 60 * 1000L
    val tx =
      createTransaction(
        TransactionType.INCOME,
        1_000_000_000_000_000_000L, // 10^18 — exceeds Long.MAX_VALUE / 30.
        date = nowMs - 45 * dayMs
      )
    val result = LocalBudgetMetrics.monthlyIncomeBaseline(listOf(tx), nowMs)
    // Rust: (1_000_000_000_000_000_000 * 30) / 45 = 666_666_666_666_666_666.
    assertEquals(666_666_666_666_666_666L, result)
  }

  @Test
  fun localMonthlyIncomeBaselineHandlesSumOverflowingLong() {
    // Two income transactions whose sum exceeds Long.MAX_VALUE. The old
    // sumOf path silently wrapped to a negative Long. BigInteger
    // accumulation keeps the true sum and clamps the result to Long.MAX_VALUE.
    val nowMs: Long = 1_700_000_000_000
    val dayMs: Long = 24 * 60 * 60 * 1000L
    val halfMaxPlusOne = Long.MAX_VALUE / 2 + 1
    val txs =
      listOf(
        createTransaction(TransactionType.INCOME, halfMaxPlusOne, date = nowMs - 30 * dayMs),
        createTransaction(TransactionType.INCOME, halfMaxPlusOne, date = nowMs - 20 * dayMs),
      )
    val result = LocalBudgetMetrics.monthlyIncomeBaseline(txs, nowMs)
    // Sum = Long.MAX_VALUE + 1; baseline = (sum * 30) / 30 = Long.MAX_VALUE + 1,
    // clamped to Long.MAX_VALUE. It must never wrap to a negative value.
    assertEquals(Long.MAX_VALUE, result)
  }

  @Test
  fun localMonthlyIncomeBaselineSaturatesSumLikeRust() {
    // Two incomes whose sum exceeds Long.MAX_VALUE. Rust saturates the sum
    // via saturating_add, so the Kotlin fallback must do the same for parity.
    val nowMs: Long = 1_700_000_000_000
    val dayMs: Long = 24 * 60 * 60 * 1000L
    val halfMax = Long.MAX_VALUE / 2
    val txs =
      listOf(
        createTransaction(TransactionType.INCOME, halfMax + 500, date = nowMs - 60 * dayMs),
        createTransaction(TransactionType.INCOME, halfMax + 500, date = nowMs - 30 * dayMs),
      )
    val result = LocalBudgetMetrics.monthlyIncomeBaseline(txs, nowMs)
    val expected = Long.MAX_VALUE / 2
    assertEquals("Kotlin must saturate sum like Rust's saturating_add", expected, result)
  }

  @Test
  fun debtToIncomeRatioOverflowingObligationsWithZeroIncomeYieldsCappedRatio() {
    val largeInstallment =
      Installment(
        id = 1L,
        title = "inst",
        amount = Long.MAX_VALUE,
        dueDate = System.currentTimeMillis(),
        isPaid = false
      )
    val largeCreditorLoan =
      Loan(
        personName = "creditor",
        type = LoanType.CREDITOR,
        originalAmount = Long.MAX_VALUE,
        remainingAmount = Long.MAX_VALUE,
        description = "loan",
        isSettled = false
      )

    val ratio =
      LocalBudgetMetrics.debtToIncomeRatio(
        loans = listOf(largeCreditorLoan),
        installments = listOf(largeInstallment),
        monthlyIncome = 0L
      )
    assertEquals(
      "When debt obligations overflow Long.MAX_VALUE, zero income must still return capped ratio 1.0",
      1.0,
      ratio,
      1e-10
    )
  }

  @Test
  fun summarizeOverflowingTransactionsSaturatesIncomeAndExpense() {
    val tx1 = createTransaction(TransactionType.INCOME, Long.MAX_VALUE)
    val tx2 = createTransaction(TransactionType.INCOME, 100L)
    val tx3 = createTransaction(TransactionType.EXPENSE, Long.MAX_VALUE)
    val tx4 = createTransaction(TransactionType.EXPENSE, 200L)

    val summary = LocalBudgetAdvice.summarize(listOf(tx1, tx2, tx3, tx4))
    assertEquals("Income sum must clamp to Long.MAX_VALUE on overflow", Long.MAX_VALUE, summary.income)
    assertEquals("Expense sum must clamp to Long.MAX_VALUE on overflow", Long.MAX_VALUE, summary.expense)
    assertEquals("Saturated balance must be 0", 0L, summary.balance)
  }
}
