package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Kotlin-fallback regression coverage for the analytics category-breakdown
 * denominator.
 *
 * This class lives in the fast `testDebugUnitTest` suite. The native
 * `hesabyar_core` library IS on the test class path for every test task (the
 * `rustJvmArgs` wiring in `app/build.gradle.kts` adds `rust/target/release` to
 * `java.library.path`), so the @Before forces the Rust availability decision
 * OFF. Without that override `RustBridge` would load the library and these
 * assertions would silently run the native path instead of
 * [GetAnalyticsUseCase.computeFallbackAnalytics].
 *
 * [GetAnalyticsUseCaseFallbackTest] covers the same Kotlin fallback more
 * broadly (also forced), while [GetAnalyticsUseCaseRustTest] is the genuine
 * native-path counterpart.
 */
class GetAnalyticsUseCaseFallbackDenominatorTest {
  private val useCase = GetAnalyticsUseCase()

  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  @Test
  fun kotlinFallbackBreakdownDenominatorExcludesTransfers() =
    runTest {
      val categories = listOf(analyticsCat(1, "غذا"), analyticsCat(2, "حمل‌ونقل"))
      val accounts =
        listOf(
          AccountEntity(id = 1, name = "A", type = AccountType.BANK),
          AccountEntity(id = 2, name = "B", type = AccountType.BANK),
        )
      val now = System.currentTimeMillis()
      val txs =
        listOf(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = 1L,
            amount = 300_000,
            description = "food",
            date = now,
            accountId = 1L,
            destinationAccountId = null
          ),
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = 2L,
            amount = 100_000,
            description = "transport",
            date = now,
            accountId = 1L,
            destinationAccountId = null
          ),
          Transaction(
            type = TransactionType.TRANSFER,
            categoryId = 1L,
            amount = 400_000,
            description = "transfer out",
            date = now,
            accountId = 1L,
            destinationAccountId = 2L
          )
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          accountId = 1L
        )

      // The transfer-out (400k) must NOT inflate the breakdown denominator:
      // food = 300k/400k = 75%, transport = 100k/400k = 25%. The monthly
      // spending series still counts the transfer-out (expense = 800k). A
      // transfer-inclusive denominator (800k) would instead yield 37.5%/12.5%.
      val food = result.categoryBreakdown.first { it.categoryId == 1L }
      val transport = result.categoryBreakdown.first { it.categoryId == 2L }
      assertEquals("food percentage", 75f, food.percentage, 0.001f)
      assertEquals("transport percentage", 25f, transport.percentage, 0.001f)
      assertEquals("spending series counts the transfer-out", 800_000L, result.monthlySpending.first().expense)
    }
}
