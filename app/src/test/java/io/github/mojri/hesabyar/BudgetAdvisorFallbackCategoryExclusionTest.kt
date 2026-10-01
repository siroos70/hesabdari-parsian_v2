package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.LoansCategoryExclusion
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BudgetAdvisorFallbackCategoryExclusionTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  private var previousUnit: CurrencyUnit? = null

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
    previousUnit = CurrencyFormatter.currentUnit
    // CurrencyFormatter.currentUnit is a mutable global; pin it so the
    // Toman-scaled literals below never depend on test ordering.
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    previousUnit?.let { CurrencyFormatter.setUnit(it) }
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
    date = 1_700_000_000_000L,
  )

  private fun categoryWithKey(
    id: Long,
    key: String,
  ) = Category(
    id = id,
    name = "Category $id",
    key = key,
    icon = "",
    color = 0xFF000000L,
    type = CategoryType.BOTH,
  )

  private fun category(id: Long) = categoryWithKey(id, "test-$id")

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

  private fun transactions() =
    listOf(
      tx(TransactionType.INCOME, 10_000_000L, regularIncomeCategory),
      tx(TransactionType.INCOME, 5_000_000L, loansCategory),
      tx(TransactionType.EXPENSE, 9_000_000L, regularExpenseCategory),
      tx(TransactionType.EXPENSE, 3_000_000L, loansCategory),
    )

  private fun categories() =
    listOf(
      category(regularIncomeCategory),
      category(regularExpenseCategory),
      category(loansCategory),
    )

  /** Same ids, but the loans id now carries the resolvable `"Loans"` key. */
  private fun categoriesWithLoansKey() =
    listOf(
      category(regularIncomeCategory),
      category(regularExpenseCategory),
      categoryWithKey(loansCategory, LoansCategoryExclusion.CATEGORY_KEY),
    )

  @Test
  fun getOfflineAdviceEmptyExclusionIncludesLoansTotals() {
    val result = normalize(BudgetAdvisor.getOfflineAdvice(transactions(), categories()))

    assertTrue("expected total income, got: $result", result.contains("1,500,000"))
    assertTrue("expected total expense, got: $result", result.contains("1,200,000"))
  }

  @Test
  fun getOfflineAdvicePopulatedExclusionRemovesLoansTotals() {
    val result =
      normalize(
        BudgetAdvisor.getOfflineAdvice(
          transactions = transactions(),
          categories = categories(),
          excludedCategoryIds = listOf(loansCategory),
        )
      )

    assertTrue("expected regular income, got: $result", result.contains("1,000,000"))
    assertTrue("expected regular expense, got: $result", result.contains("900,000"))
    assertFalse("loan income leaked into totals, got: $result", result.contains("1,500,000"))
    assertFalse("loan expense leaked into totals, got: $result", result.contains("1,200,000"))
  }

  @Test
  fun getOfflineForecastEmptyExclusionIncludesLoansBalance() {
    val result =
      normalize(
        BudgetAdvisor.getOfflineForecast(
          transactions = transactions(),
          loans = emptyList(),
          installments = emptyList(),
          bankLoans = emptyList(),
        )
      )

    assertTrue("expected unfiltered forecast balance, got: $result", result.contains("300,000"))
  }

  @Test
  fun getOfflineForecastPopulatedExclusionRemovesLoansBalance() {
    val result =
      normalize(
        BudgetAdvisor.getOfflineForecast(
          transactions = transactions(),
          loans = emptyList(),
          installments = emptyList(),
          bankLoans = emptyList(),
          excludedCategoryIds = listOf(loansCategory),
        )
      )

    assertTrue("expected filtered forecast balance, got: $result", result.contains("100,000"))
    assertFalse("unfiltered forecast balance leaked, got: $result", result.contains("300,000"))
  }

  @Test
  fun calculateFinancialHealthScoreEmptyExclusionIncludesLoansIncomeAndExpense() {
    val score =
      BudgetAdvisor.calculateFinancialHealthScore(
        transactions = transactions(),
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
      )

    assertEquals(85, score)
  }

  @Test
  fun calculateFinancialHealthScorePopulatedExclusionRemovesLoansIncomeAndExpense() {
    val score =
      BudgetAdvisor.calculateFinancialHealthScore(
        transactions = transactions(),
        loans = emptyList(),
        installments = emptyList(),
        categories = categories(),
        excludedCategoryIds = listOf(loansCategory),
      )

    assertEquals(75, score)
  }

  @Test
  fun getBudgetForecastOfflineSubstituteExcludesResolvedLoansCategory() =
    runTest {
      val result =
        normalize(
          BudgetAdvisor.getBudgetForecast(
            transactions = transactions(),
            loans = emptyList(),
            installments = emptyList(),
            categories = categoriesWithLoansKey(),
            config = AiProviderConfig(apiKey = ""),
          )
        )

      assertTrue("expected filtered forecast balance, got: $result", result.contains("100,000"))
      assertFalse("unfiltered forecast balance leaked, got: $result", result.contains("300,000"))
    }

  @Test
  fun getBudgetForecastOfflineSubstituteWithoutResolvableLoansKeepsFullTotals() =
    runTest {
      AppLogger.clear()
      val result =
        normalize(
          BudgetAdvisor.getBudgetForecast(
            transactions = transactions(),
            loans = emptyList(),
            installments = emptyList(),
            categories = categories(),
            config = AiProviderConfig(apiKey = ""),
          )
        )

      assertTrue("expected unfiltered forecast balance, got: $result", result.contains("300,000"))
      assertTrue(
        "expected one resolution warning for the missing Loans category",
        AppLogger.getLogsForTag("BudgetAdvisor").any {
          it.level == "W" &&
            it.message == "Loans category not found by key; defaulting to empty exclusion list"
        },
      )
    }
}
