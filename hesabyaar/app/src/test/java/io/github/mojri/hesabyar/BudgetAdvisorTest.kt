package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.BudgetAdviceGenerator
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.api.LocalBudgetMetrics
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
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

@org.junit.experimental.categories.Category(RustTest::class)
class BudgetAdvisorTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    // These tests assert the native offline-advice output, so force the Rust
    // availability decision explicitly. (RustIsolationRule restores the prior
    // override after the class, so no state leaks to later classes.)
    HesabyarApp.setRustInitializedForTesting(true)
  }

  private val dayMs = 24L * 60 * 60 * 1000

  // Normalize Persian/Arabic digits and the Persian thousands separator to their
  // Western forms so assertions are locale-rendering agnostic.
  private fun normalizeDigits(input: String): String {
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val sb = StringBuilder(input.length)
    for (c in input) {
      sb.append(
        when {
          c in persian -> '0' + persian.indexOf(c)
          c in arabic -> '0' + arabic.indexOf(c)
          c == '٬' -> ','
          else -> c
        }
      )
    }
    return sb.toString()
  }

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

  private fun createCategory(
    id: Long,
    name: String,
    key: String = "test"
  ): Category =
    Category(id = id, name = name, key = key, icon = "Test", color = 0xFF757575L, type = CategoryType.EXPENSE)

  @Test
  fun getofflineadviceEmptyTransactions() {
    val result = BudgetAdvisor.getOfflineAdvice(emptyList(), emptyList())
    assertTrue(result.contains("نکردهاید"))
  }

  @Test
  fun getbudgetadviceofflineEmptyTransactionsStillSurfacesUnpaidInstallment() {
    val installments =
      listOf(
        createInstallment("قسط ماشین", 2_000_000, isPaid = false)
      )
    val result =
      BudgetAdviceGenerator.getBudgetAdviceOffline(
        emptyList(),
        emptyList(),
        installments,
        emptyList()
      )
    // Empty-ledger message must be retained...
    assertTrue(result.contains("نکرده"))
    // ...while the upcoming obligation is still surfaced (offline, no AI/network).
    assertTrue(result.contains("اقساط"))
  }

  @Test
  fun getbudgetadviceDelegatesToLoansslashinstallmentsawareOfflineAdvice() =
    kotlinx.coroutines.test.runTest {
      // The production entry point (BudgetAdvisor.getBudgetAdvice) must route
      // through BudgetAdviceGenerator so unpaid obligations ship in the advice,
      // even with no AI config (offline path). Regression guard for the
      // previously-unreachable generator wiring.
      val installments = listOf(createInstallment("قسط ماشین", 2_000_000, isPaid = false))
      val result =
        BudgetAdvisor.getBudgetAdvice(
          emptyList(),
          emptyList(),
          installments,
          emptyList(),
          null
        )
      assertTrue(result.contains("اقساط"))
    }

  @Test
  fun getbudgetadviceWithConfiguredProviderAndEmptyLedgerReturnsEmptystateWithoutAiCall() =
    kotlinx.coroutines.test.runTest {
      // A configured provider must not trigger a paid/network AI request for an
      // account with no transactions and no unpaid obligations. The regression
      // guard asserts directly on the call/no-call decision (not just the output
      // shape): if the early-return were removed, the injected generator would be
      // invoked and this test would fail — an AI failure would otherwise fall back
      // to the same offline message and mask the leak.
      val config = AiProviderConfig(providerType = AiProviderType.GEMINI, apiKey = "fake-key")
      assertTrue(config.isConfigured)
      var aiCallCount = 0
      val recordingGenerate: suspend (AiProviderConfig, String, String?, Double) -> AiProvider.ApiResult =
        { _, _, _, _ ->
          aiCallCount++
          AiProvider.ApiResult.Success("شما در ماه گذشته بیست درصد از درآمد خود را پس انداز کرده اید.")
        }
      val result =
        BudgetAdvisor.getBudgetAdvice(
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList(),
          config,
          emptyList(),
          recordingGenerate
        )
      assertEquals("AI must not be called for an empty ledger", 0, aiCallCount)
      assertTrue(result.contains("نکرده‌اید"))
    }

  @Test
  fun getbudgetadviceWithConfiguredProviderStillCallsAiWhenUnpaidObligationsExist() =
    kotlinx.coroutines.test.runTest {
      // When unpaid obligations exist, the generator path must still be used even
      // with an empty transaction list. The guard asserts the AI generator is
      // actually invoked (not merely that offline text leaked through when the
      // call fails), and that the AI-produced result — not the offline fallback —
      // is what gets returned.
      val config = AiProviderConfig(providerType = AiProviderType.GEMINI, apiKey = "fake-key")
      assertTrue(config.isConfigured)
      var aiCallCount = 0
      val aiText = "شما در ماه گذشته بیست درصد از درآمد خود را پس انداز کرده اید."
      val recordingGenerate: suspend (AiProviderConfig, String, String?, Double) -> AiProvider.ApiResult =
        { _, _, _, _ ->
          aiCallCount++
          AiProvider.ApiResult.Success(aiText)
        }
      val installments = listOf(createInstallment("قسط ماشین", 2_000_000, isPaid = false))
      val result =
        BudgetAdvisor.getBudgetAdvice(
          emptyList(),
          emptyList(),
          installments,
          emptyList(),
          config,
          emptyList(),
          recordingGenerate
        )
      assertEquals("AI must be called when unpaid obligations exist", 1, aiCallCount)
      // The AI-produced text (validated and returned as-is) must be the result,
      // proving the offline fallback was bypassed.
      assertEquals("expected AI output, got: $result", aiText, result)
    }

  @Test
  fun getofflineadviceHighSpendingRatioWarns() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 9_500_000)
      )
    val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
    assertTrue(
      result.contains("کسری") || result.contains("مخارج") || result.contains("بیش از درآمد")
    )
  }

  @Test
  fun getofflineadviceLowSpendingRatioCongratulates() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 2_000_000)
      )
    val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
    assertTrue(result.contains("عملکرد") || result.contains("فوق‌العاده") || result.contains("۸۰٪"))
  }

  @Test
  fun getofflineadviceBalancedRatio() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 6_000_000)
      )
    val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
    assertTrue(
      result.contains("پس‌انداز") || result.contains("۴۰٪")
    )
  }

  @Test
  fun getofflineadviceMentionsHighestSpendingCategory() {
    val categories =
      listOf(
        createCategory(1L, "خوراک", "Food"),
        createCategory(2L, "حمل و نقل", "Transportation")
      )
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 3_000_000, 1L),
        createTransaction(TransactionType.EXPENSE, 1_000_000, 2L)
      )
    val result = BudgetAdvisor.getOfflineAdvice(transactions, categories)
    assertTrue(result.contains("خوراک"))
  }

  @Test
  fun getofflineadviceContainsFinancialAdvice() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 5_000_000)
      )
    val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
    assertTrue(result.contains("بودجه") || result.contains("پس‌انداز") || result.contains("هزینه"))
  }

  @Test
  fun getofflineforecastMentionsActiveInstallmentsInTomanNotRial() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 5_000_000)
      )
    val installments =
      listOf(
        createInstallment("قسط ماشین", 2_000_000, isPaid = false)
      )
    val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), installments)
    // 2,000,000 Rial must render as Toman (200,000), never the raw Rial value.
    assertTrue(result.contains("اقساط"))
    assertTrue(result.contains("تومان"))
    // Positive guard: the installment amount must actually be rendered (in Toman),
    // so the negative Rial check below cannot pass vacuously if formatting changes.
    // Normalize digits so the assertion is robust to Persian/Western rendering.
    assertTrue(
      "forecast must render the Toman-converted installment amount, got: $result",
      normalizeDigits(result).contains("200,000")
    )
    assertFalse(
      "forecast must not expose raw Rial magnitude 2,000,000, got: $result",
      result.contains("2,000,000")
    )
  }

  @Test
  fun getofflineforecastEmptyData() {
    val result = BudgetAdvisor.getOfflineForecast(emptyList(), emptyList(), emptyList())
    assertTrue(result.contains("هنوز اطلاعات") || result.contains("ثبت نشده"))
  }

  @Test
  fun getofflineforecastSettledBankLoansStillNoData() {
    // A settled bank loan is not an active obligation and must not suppress the
    // "no data" message (parity with the Kotlin fallback guard).
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        emptyList(),
        emptyList(),
        listOf(createBankLoan(isSettled = true))
      )
    assertTrue(
      "settled bank loan must not produce a forecast, got: $result",
      result.contains("هنوز اطلاعات") || result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastNoDataWithOnlyDebtorLoan() {
    // DEBTOR loans must not suppress the "no data" message in the Kotlin
    // fallback (parity with Rust get_offline_forecast, which only counts
    // unsettled CREDITOR loans toward total_obligations).
    HesabyarApp.setRustInitializedForTesting(false)
    val result =
      BudgetAdvisor.getOfflineForecast(
        emptyList(),
        listOf(createLoan(LoanType.DEBTOR, 1_000_000, 500_000)),
        emptyList()
      )
    assertTrue(
      "DEBTOR loan must not suppress no-data message in Kotlin fallback, got: $result",
      result.contains("هنوز اطلاعات") || result.contains("ثبت نشده")
    )
  }

  @Test
  fun getofflineforecastNegativeBalanceWarns() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 5_000_000),
        createTransaction(TransactionType.EXPENSE, 6_000_000)
      )
    val installments =
      listOf(
        createInstallment("قسط", 1_000_000, isPaid = false)
      )
    val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), installments)
    assertTrue(result.contains("کسری") || result.contains("ریسک") || result.contains("هشدار"))
  }

  @Test
  fun getofflineforecastPositiveBalanceStable() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 3_000_000)
      )
    val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), emptyList())
    assertTrue(result.contains("پایدار") || result.contains("سبز") || result.contains("مازاد"))
  }

  @Test
  fun getofflineforecastMentionsInstallmentAmounts() {
    val transactions =
      listOf(
        createTransaction(TransactionType.INCOME, 10_000_000),
        createTransaction(TransactionType.EXPENSE, 3_000_000)
      )
    val installments =
      listOf(
        createInstallment("قسط ماشین", 2_000_000, isPaid = false),
        createInstallment("قسط خانه", 1_000_000, isPaid = false)
      )
    val result = BudgetAdvisor.getOfflineForecast(transactions, emptyList(), installments)
    assertTrue(result.contains("اقساط") || result.contains("تومان"))
  }

  @Test
  fun getpersiancategorynameMapsCategoryKeysToProductionPersianNames() {
    // Exercises the real production mapping (Category.DEFAULTS) instead of a
    // locally declared copy that could drift from the shipped defaults.
    assertEquals("خوراک", BudgetAdvisor.getPersianCategoryName("Food"))
    assertEquals("حمل و نقل", BudgetAdvisor.getPersianCategoryName("Transportation"))
    assertEquals("خرید", BudgetAdvisor.getPersianCategoryName("Shopping"))
    assertEquals("قبوض", BudgetAdvisor.getPersianCategoryName("Bills"))
    assertEquals("اقساط", BudgetAdvisor.getPersianCategoryName("Installments"))
    assertEquals("وام و قرض", BudgetAdvisor.getPersianCategoryName("Loans"))
    assertEquals("درآمد", BudgetAdvisor.getPersianCategoryName("Income"))
    assertEquals("سایر", BudgetAdvisor.getPersianCategoryName("Other"))
    // Unknown keys fall back to themselves (no false translation).
    assertEquals("UnknownKey", BudgetAdvisor.getPersianCategoryName("UnknownKey"))
    // Coverage: every default category must expose a non-blank Persian name.
    Category.DEFAULTS.forEach { assertTrue(it.name.isNotBlank()) }
  }

  @Test
  fun noIncomeExpenseOnlyGivesCorrectRatio() {
    val transactions =
      listOf(
        createTransaction(TransactionType.EXPENSE, 5_000_000)
      )
    val result = BudgetAdvisor.getOfflineAdvice(transactions, emptyList())
    assertTrue(
      result.contains("درآمد") || result.contains("ثبت نکرده") || result.contains("هزینه")
    )
  }

  // ---------------------------------------------------------------------------
  // localMonthlyIncomeBaseline (trailing-90-day income baseline)
  // ---------------------------------------------------------------------------

  @Test
  fun localmonthlyincomebaselineEmptyListReturnsZero() {
    val now = 1_700_000_000_000L
    assertEquals(0L, LocalBudgetMetrics.monthlyIncomeBaseline(emptyList(), now))
  }

  @Test
  fun localmonthlyincomebaselineSingleIncomeTransactionNormalizesToMonthly() {
    val now = 1_700_000_000_000L
    // 30 days ago -> spans exactly 1 month -> baseline equals the amount.
    val tx = createTransactionAt(TransactionType.INCOME, 3_000_000, now - 30 * dayMs)
    assertEquals(3_000_000L, LocalBudgetMetrics.monthlyIncomeBaseline(listOf(tx), now))
  }

  @Test
  fun localmonthlyincomebaselineTypicalCaseSumsAndNormalizesMultipleIncomes() {
    val now = 1_700_000_000_000L
    val tx1 = createTransactionAt(TransactionType.INCOME, 1_500_000, now - 15 * dayMs)
    val tx2 = createTransactionAt(TransactionType.INCOME, 1_500_000, now - 45 * dayMs)
    // oldest is 45 days ago -> 45/30 = 1.5 months; 3,000,000 / 1.5 = 2,000,000.
    assertEquals(2_000_000L, LocalBudgetMetrics.monthlyIncomeBaseline(listOf(tx1, tx2), now))
  }

  @Test
  fun localmonthlyincomebaselineBoundaryStrictlyFiltersOutside90Days() {
    val now = 1_700_000_000_000L
    val within89 = createTransactionAt(TransactionType.INCOME, 4_000_000, now - 89 * dayMs)
    val outside91 = createTransactionAt(TransactionType.INCOME, 9_000_000, now - 91 * dayMs)

    // A transaction older than 90 days must be excluded entirely.
    assertEquals(0L, LocalBudgetMetrics.monthlyIncomeBaseline(listOf(outside91), now))

    // A transaction inside the window is counted, and an out-of-window one must
    // not change the result (proving strict trailing-90-day filtering).
    val onlyWithin = LocalBudgetMetrics.monthlyIncomeBaseline(listOf(within89), now)
    val withOutside = LocalBudgetMetrics.monthlyIncomeBaseline(listOf(within89, outside91), now)
    assertEquals(onlyWithin, withOutside)
    assertTrue(onlyWithin > 0)

    // The inclusive boundary (exactly 90 days ago) is still inside the window.
    val atBoundary = createTransactionAt(TransactionType.INCOME, 3_000_000, now - 90 * dayMs)
    assertTrue(LocalBudgetMetrics.monthlyIncomeBaseline(listOf(atBoundary), now) > 0)

    // Expenses are never counted as income.
    val expense = createTransactionAt(TransactionType.EXPENSE, 8_000_000, now - 10 * dayMs)
    assertEquals(0L, LocalBudgetMetrics.monthlyIncomeBaseline(listOf(expense), now))
  }

  // calculateFinancialHealthScore local-fallback coverage moved to
  // [BudgetAdvisorFallbackTest] (forces the Kotlin path via override + rule).
}
