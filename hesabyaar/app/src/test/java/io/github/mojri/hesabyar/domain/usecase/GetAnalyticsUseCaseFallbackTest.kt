package io.github.mojri.hesabyar.domain.usecase
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Kotlin-fallback analytics coverage. The @Before forces the Rust availability
 * decision off, so these tests genuinely execute
 * [GetAnalyticsUseCase.computeFallbackAnalytics] even though the native
 * library sits on the test class path (see the `rustJvmArgs` wiring in
 * `app/build.gradle.kts`). [GetAnalyticsUseCaseRustTest] is the native-path
 * counterpart.
 */
class GetAnalyticsUseCaseFallbackTest {
  private val useCase = GetAnalyticsUseCase()

  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  @Test
  fun kotlinFallbackPopulatesBankLoanSummaries() =
    runTest {
      val bankLoans =
        listOf(
          BankLoan(
            bankName = "بانک",
            loanName = "l",
            receivedAmount = 100_000_000L,
            monthlyInstallmentAmount = 10_000_000L,
            numberOfInstallments = 10,
            totalRepayableAmount = 120_000_000L,
            totalInterest = 20_000_000L,
            startDate = 1_700_000_000_000L,
            description = "",
            isSettled = false
          )
        )
      val result =
        useCase.computeAnalytics(emptyList(), emptyList(), emptyList(), emptyList(), bankLoans)

      assertEquals(1, result.bankLoans.size)
      assertEquals(120_000_000L, result.bankLoansTotalDebt)
      assertEquals(120_000_000L, result.bankLoans[0].remainingDebt)
    }

  @Test
  fun kotlinFallbackZeroesDebtForSettledLoan() =
    runTest {
      val bankLoans =
        listOf(
          BankLoan(
            bankName = "b",
            loanName = "l",
            receivedAmount = 100_000_000L,
            monthlyInstallmentAmount = 10_000_000L,
            numberOfInstallments = 10,
            totalRepayableAmount = 120_000_000L,
            totalInterest = 20_000_000L,
            startDate = 1_700_000_000_000L,
            description = "",
            isSettled = true
          )
        )
      val result =
        useCase.computeAnalytics(emptyList(), emptyList(), emptyList(), emptyList(), bankLoans)

      assertEquals(0L, result.bankLoansTotalDebt)
      assertTrue(result.bankLoans.all { it.remainingDebt == 0L })
    }

  // --- AccountId fallback filtering (T2-8) ---

  @Test
  fun fallbackAccountIdFiltersAnalyticsToMatchingTransactions() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val txs =
        listOf(
          analyticsTx(TransactionType.INCOME, 5_000_000, accountId = 1),
          analyticsTx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          analyticsTx(TransactionType.INCOME, 3_000_000, accountId = 2),
          analyticsTx(TransactionType.EXPENSE, 500_000, accountId = 2),
        )

      // accountId=1: only account 1's transactions
      val result1 =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accountId = 1
        )
      assertEquals("account 1 monthlyIncome", 5_000_000L, result1.monthlySpending.first().income)
      assertEquals("account 1 monthlyExpense", 1_000_000L, result1.monthlySpending.first().expense)

      // accountId=2: only account 2's transactions
      val result2 =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accountId = 2
        )
      assertEquals("account 2 monthlyIncome", 3_000_000L, result2.monthlySpending.first().income)
      assertEquals("account 2 monthlyExpense", 500_000L, result2.monthlySpending.first().expense)
    }

  @Test
  fun fallbackNullAccountIdIncludesAllTransactions() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val txs =
        listOf(
          analyticsTx(TransactionType.INCOME, 5_000_000, accountId = 1),
          analyticsTx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          analyticsTx(TransactionType.INCOME, 3_000_000, accountId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accountId = null
        )
      // null → all accounts: income=8M, expense=1M
      assertEquals(8_000_000L, result.monthlySpending.first().income)
      assertEquals(1_000_000L, result.monthlySpending.first().expense)
    }

  // --- Archived-account exclusion (all-accounts analytics) ---

  private fun account(
    id: Long,
    archived: Boolean = false
  ) = AccountEntity(
    id = id,
    name = "Account $id",
    type = AccountType.BANK,
    isArchived = archived
  )

  @Test
  fun fallbackAllAccountsExcludesArchivedAccountTransactions() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val accounts = listOf(account(1), account(2, archived = true))
      val txs =
        listOf(
          analyticsTx(TransactionType.INCOME, 3_000_000, accountId = 1),
          analyticsTx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          analyticsTx(TransactionType.INCOME, 500_000, accountId = 2),
          analyticsTx(TransactionType.EXPENSE, 200_000, accountId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts
        )
      // Archived-account transactions must not leak into all-accounts totals
      assertEquals(3_000_000L, result.monthlySpending.first().income)
      assertEquals(1_000_000L, result.monthlySpending.first().expense)
      assertEquals(1, result.categoryBreakdown.size)
      assertEquals(1_000_000L, result.categoryBreakdown.first().total)

      // includeArchived = true keeps them
      val withArchived =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          includeArchived = true
        )
      assertEquals(3_500_000L, withArchived.monthlySpending.first().income)
      assertEquals(1_200_000L, withArchived.monthlySpending.first().expense)
    }

  // --- Transfer semantics: account-filtered analytics ---

  @Test
  fun fallbackTransferNeutralWhenAccountIdNull() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val accounts = listOf(account(1), account(2))
      val txs =
        listOf(
          analyticsTx(TransactionType.TRANSFER, 500_000, accountId = 1, destId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          accountId = null
        )
      // All-accounts view: transfers are neutral
      assertEquals(0L, result.monthlySpending.first().income)
      assertEquals(0L, result.monthlySpending.first().expense)
    }

  @Test
  fun fallbackTransferSourceIsSelectedAccountCountedAsExpense() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val accounts = listOf(account(1), account(2))
      val txs =
        listOf(
          analyticsTx(TransactionType.TRANSFER, 500_000, accountId = 1, destId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          accountId = 1
        )
      // Selected account is source → counted as expense
      assertEquals(500_000L, result.monthlySpending.first().expense)
      assertEquals(0L, result.monthlySpending.first().income)
    }

  @Test
  fun fallbackTransferDestIsSelectedAccountCountedAsIncome() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val accounts = listOf(account(1), account(2))
      val txs =
        listOf(
          analyticsTx(TransactionType.TRANSFER, 500_000, accountId = 1, destId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          accountId = 2
        )
      // Selected account is destination → counted as income
      assertEquals(500_000L, result.monthlySpending.first().income)
      assertEquals(0L, result.monthlySpending.first().expense)
    }

  @Test
  fun fallbackTransferUninvolvedAccountNeutral() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val accounts = listOf(account(1), account(2), account(3))
      val txs =
        listOf(
          analyticsTx(TransactionType.TRANSFER, 500_000, accountId = 1, destId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          accountId = 3
        )
      // Selected account is not involved → neutral
      assertEquals(0L, result.monthlySpending.first().income)
      assertEquals(0L, result.monthlySpending.first().expense)
    }

  // --- Account-breakdown parity: the reference the Rust path must match ---

  @Test
  fun fallbackAccountBreakdownContainsOnlySelectedAccountWhenAccountIdSet() =
    runTest {
      val categories = listOf(analyticsCat(1, "خوراک"))
      val accounts = listOf(account(1), account(2))
      val txs =
        listOf(
          analyticsTx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          analyticsTx(TransactionType.EXPENSE, 500_000, accountId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          accountId = 1
        )
      // monthlyTx is filtered by accountId first, so only account 1's expense
      // transactions reach buildAccountBreakdown → a single segment.
      assertEquals("only the selected account may appear", 1, result.accountBreakdown.size)
      assertEquals("segment carries the selected account id", 1L, result.accountBreakdown[0].categoryId)
      assertEquals(
        "segment total matches the selected account's expenses",
        1_000_000L,
        result.accountBreakdown[0].total
      )
      assertEquals("single segment owns 100%", 100f, result.accountBreakdown[0].percentage, 0.001f)
    }
}
