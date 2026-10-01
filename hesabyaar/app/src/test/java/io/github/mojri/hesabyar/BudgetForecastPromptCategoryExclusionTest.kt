package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProvider
import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.api.ForecastFacts
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

/**
 * Plan 011 D2 mirror for the online forecast prompt. The prompt facts must
 * drop the dynamically resolved `"Loans"` category ids. When resolution
 * fails, `LoansCategoryExclusion.resolve` logs one warning and the facts
 * keep their full totals.
 */
class BudgetForecastPromptCategoryExclusionTest {
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

  /** Rewrites Persian digits and separators into Western form. */
  private fun normalize(input: String): String {
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    return buildString {
      input.forEach { char ->
        append(
          when {
            char in persian -> '0' + persian.indexOf(char)
            char in arabic -> '0' + arabic.indexOf(char)
            char == '٬' -> ','
            else -> char
          },
        )
      }
    }
  }

  @Test
  fun forecastFactsEmptyExclusionKeepsLoansTotals() {
    val facts = ForecastFacts.of(transactions(), emptyList(), emptyList(), categories(), emptyList())

    assertEquals("income total keeps the Loans inflow", 15_000_000L, facts.totalIncome)
    assertEquals("expense total keeps the Loans outflow", 13_000_000L, facts.totalExpense)
    assertTrue("expected Loans breakdown, got: ${facts.categoryReport}", facts.categoryReport.contains("وام"))
  }

  @Test
  fun forecastFactsPopulatedExclusionRemovesLoansTotals() {
    val facts =
      ForecastFacts.of(
        transactions(),
        emptyList(),
        emptyList(),
        categories(),
        emptyList(),
        excludedCategoryIds = listOf(loansCategory),
      )

    assertEquals("income total drops the Loans inflow", 10_000_000L, facts.totalIncome)
    assertEquals("expense total drops the Loans outflow", 4_000_000L, facts.totalExpense)
    assertFalse("Loans breakdown leaked", facts.categoryReport.contains("وام"))
    assertTrue("regular expense breakdown kept", facts.categoryReport.contains("خوراک"))
  }

  @Test
  fun getBudgetForecastOnlinePromptResolvesLoansCategoryDynamically() =
    runTest {
      var prompt = ""
      val aiText = "این یک پاسخ هوش مصنوعی معتبر برای آزمایش است."
      val result =
        BudgetAdvisor.getBudgetForecast(
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
      val normalized = normalize(prompt)
      assertTrue("expected filtered income in prompt, got: $normalized", normalized.contains("1,000,000 تومان"))
      assertTrue("expected filtered expense in prompt, got: $normalized", normalized.contains("400,000 تومان"))
      assertFalse("unfiltered income leaked to prompt", normalized.contains("1,500,000 تومان"))
      assertFalse("unfiltered expense leaked to prompt", normalized.contains("1,300,000 تومان"))
      assertFalse("Loans breakdown leaked to prompt", normalized.contains("وام:"))
      assertTrue("regular expense breakdown kept in prompt", normalized.contains("خوراک:"))
    }

  /**
   * Empty-exclusion mirror for the forecast prompt. When no category carries
   * the `"Loans"` key, `LoansCategoryExclusion.resolve` must log one warning
   * and produce an empty list, so the facts keep their full totals.
   */
  @Test
  fun getBudgetForecastOnlinePromptWithoutResolvableLoansKeepsFullTotalsAndLogsWarning() =
    runTest {
      AppLogger.clear()
      var prompt = ""
      val aiText = "این یک پاسخ هوش مصنوعی معتبر برای آزمایش است."
      val result =
        BudgetAdvisor.getBudgetForecast(
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
      val normalized = normalize(prompt)
      assertTrue("expected unfiltered income in prompt, got: $normalized", normalized.contains("1,500,000 تومان"))
      assertTrue("expected unfiltered expense in prompt, got: $normalized", normalized.contains("1,300,000 تومان"))
      assertTrue("expected Loans breakdown in prompt, got: $normalized", normalized.contains("وام:"))
      assertTrue("expected Loans amount in prompt, got: $normalized", normalized.contains("900,000 تومان"))
      assertTrue(
        "expected one resolution warning for the missing Loans category",
        AppLogger.getLogsForTag("BudgetAdvisor").any {
          it.level == "W" &&
            it.message == "Loans category not found by key; defaulting to empty exclusion list"
        },
      )
    }
}
