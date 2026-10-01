package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.BudgetAdviceGenerator
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BudgetAdviceGeneratorCategoryExclusionTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  private val regularIncomeCategory = 10L
  private val regularExpenseCategory = 20L
  private val loansCategory = 999L

  private fun tx(
    type: TransactionType,
    amount: Long,
    categoryId: Long,
  ) = Transaction(
    type = type,
    categoryId = categoryId,
    amount = amount,
    description = "test",
  )

  private fun category(
    id: Long,
    key: String,
    name: String,
  ) = Category(
    id = id,
    name = name,
    key = key,
    icon = "",
    color = 0xFF000000L,
    type = CategoryType.BOTH,
  )

  private fun categories() =
    listOf(
      category(regularIncomeCategory, "Income", "درآمد"),
      category(regularExpenseCategory, "Food", "خوراک"),
      category(loansCategory, "Loans", "وام"),
    )

  /** Same rows, but no row carries the resolvable `"Loans"` key. */
  private fun categoriesWithoutLoansKey() =
    listOf(
      category(regularIncomeCategory, "Income", "درآمد"),
      category(regularExpenseCategory, "Food", "خوراک"),
      category(loansCategory, "test-loans", "وام"),
    )

  private fun transactions() =
    listOf(
      tx(TransactionType.INCOME, 10_000_000L, regularIncomeCategory),
      tx(TransactionType.INCOME, 5_000_000L, loansCategory),
      tx(TransactionType.EXPENSE, 4_000_000L, regularExpenseCategory),
      tx(TransactionType.EXPENSE, 9_000_000L, loansCategory),
    )

  @Test
  fun buildDataSummaryEmptyExclusionIncludesLoansTotals() {
    val summary =
      BudgetAdviceGenerator.buildDataSummary(
        transactions = transactions(),
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
      )

    assertTrue("expected unfiltered income, got: $summary", summary.contains("1500000 تومان"))
    assertTrue("expected unfiltered expense, got: $summary", summary.contains("1300000 تومان"))
    assertTrue("expected Loans breakdown, got: $summary", summary.contains("وام: 900000 تومان"))
  }

  @Test
  fun buildDataSummaryPopulatedExclusionRemovesLoansTotals() {
    val summary =
      BudgetAdviceGenerator.buildDataSummary(
        transactions = transactions(),
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
        excludedCategoryIds = listOf(loansCategory),
      )

    assertTrue("expected filtered income, got: $summary", summary.contains("1000000 تومان"))
    assertTrue("expected filtered expense, got: $summary", summary.contains("400000 تومان"))
    assertFalse("unfiltered income leaked, got: $summary", summary.contains("1500000 تومان"))
    assertFalse("unfiltered expense leaked, got: $summary", summary.contains("1300000 تومان"))
    assertFalse("Loans breakdown leaked, got: $summary", summary.contains("وام:"))
  }

  @Test
  fun getBudgetAdviceOfflineEmptyExclusionIdentifiesLoansAsHighestExpense() {
    val advice =
      BudgetAdviceGenerator.getBudgetAdviceOffline(
        transactions = transactions(),
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
      )

    assertTrue("expected Loans category advice, got: $advice", advice.contains("**وام**"))
  }

  @Test
  fun getBudgetAdviceOfflinePopulatedExclusionIdentifiesRegularExpense() {
    val advice =
      BudgetAdviceGenerator.getBudgetAdviceOffline(
        transactions = transactions(),
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
        excludedCategoryIds = listOf(loansCategory),
      )

    assertTrue("expected regular expense advice, got: $advice", advice.contains("**خوراک**"))
    assertFalse("Loans category leaked, got: $advice", advice.contains("**وام**"))
  }

  @Test
  fun getBudgetAdviceOnlinePromptResolvesLoansCategoryDynamically() =
    runTest {
      var prompt = ""
      val aiText = "این یک پاسخ هوش مصنوعی معتبر برای آزمایش است."
      val result =
        BudgetAdvisor.getBudgetAdvice(
          transactions = transactions(),
          loans = emptyList(),
          installments = emptyList(),
          categories = categories(),
          config = AiProviderConfig(providerType = AiProviderType.GEMINI, apiKey = "fake-key"),
          bankLoans = emptyList(),
          aiGenerate = { _, capturedPrompt, _, _ ->
            prompt = capturedPrompt
            AiProvider.ApiResult.Success(aiText)
          },
        )

      assertEquals("expected AI result when Rust validation is unavailable", aiText, result)
      assertTrue("expected filtered income in prompt, got: $prompt", prompt.contains("1000000 تومان"))
      assertTrue("expected filtered expense in prompt, got: $prompt", prompt.contains("400000 تومان"))
      assertFalse("unfiltered income leaked to prompt", prompt.contains("1500000 تومان"))
      assertFalse("unfiltered expense leaked to prompt", prompt.contains("1300000 تومان"))
      assertFalse("Loans breakdown leaked to prompt", prompt.contains("وام:"))
    }

  /**
   * Empty-exclusion mirror for the online advice prompt. When no category
   * carries the `"Loans"` key, `LoansCategoryExclusion.resolve` must log one
   * warning and produce an empty list, so the summary keeps its full totals.
   */
  @Test
  fun getBudgetAdviceOnlinePromptWithoutResolvableLoansKeepsFullTotalsAndLogsWarning() =
    runTest {
      AppLogger.clear()
      var prompt = ""
      val aiText = "این یک پاسخ هوش مصنوعی معتبر برای آزمایش است."
      val result =
        BudgetAdvisor.getBudgetAdvice(
          transactions = transactions(),
          loans = emptyList(),
          installments = emptyList(),
          categories = categoriesWithoutLoansKey(),
          config = AiProviderConfig(providerType = AiProviderType.GEMINI, apiKey = "fake-key"),
          bankLoans = emptyList(),
          aiGenerate = { _, capturedPrompt, _, _ ->
            prompt = capturedPrompt
            AiProvider.ApiResult.Success(aiText)
          },
        )

      assertEquals("expected AI result when Rust validation is unavailable", aiText, result)
      assertTrue("expected unfiltered income in prompt, got: $prompt", prompt.contains("1500000 تومان"))
      assertTrue("expected unfiltered expense in prompt, got: $prompt", prompt.contains("1300000 تومان"))
      assertTrue("expected Loans breakdown in prompt, got: $prompt", prompt.contains("وام: 900000 تومان"))
      assertTrue(
        "expected one resolution warning for the missing Loans category",
        AppLogger.getLogsForTag("BudgetAdviceGenerator").any {
          it.level == "W" &&
            it.message == "Loans category not found by key; defaulting to empty exclusion list"
        },
      )
    }

  @Test
  fun getBudgetAdviceOfflineAllTransactionsExcludedShowsEmptyTransactionsMessage() {
    val onlyLoansTransactions =
      listOf(
        tx(TransactionType.INCOME, 5_000_000L, loansCategory),
        tx(TransactionType.EXPENSE, 9_000_000L, loansCategory),
      )
    val advice =
      BudgetAdviceGenerator.getBudgetAdviceOffline(
        transactions = onlyLoansTransactions,
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
        excludedCategoryIds = listOf(loansCategory),
      )

    assertTrue(
      "expected empty transactions message when all tx are excluded",
      advice.contains("هنوز هیچ تراکنشی ثبت نکرده‌اید")
    )
  }
}
