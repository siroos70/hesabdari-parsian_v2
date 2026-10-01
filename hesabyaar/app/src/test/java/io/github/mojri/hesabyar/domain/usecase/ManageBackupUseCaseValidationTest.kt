package io.github.mojri.hesabyar.domain.usecase
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.RustTest
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Exercises [ManageBackupUseCase.validateBackup] on both of its paths.
 *
 * The two dispatch tests ([wellFormedPayloadIsValid] and
 * [malformedJsonStylePayloadIsSurfacedAsInvalid]) run the native (Rust) path —
 * the @Before forces the Rust availability decision on. The cross-reference
 * tests below run the Kotlin fallback ([BackupJsonValidator.validateBackupKotlin])
 * via [validateWithKotlinFallback], which forces the decision off so the Kotlin
 * rules are genuinely exercised regardless of whether the native library
 * happens to load. [BackupJsonValidatorKotlinFallbackTest] additionally covers
 * the Kotlin validator directly.
 *
 * This locks in dispatch + cross-reference-rule parity: a well-formed payload
 * is accepted, and structurally broken ones are surfaced as invalid on both
 * paths (the Kotlin cross-reference rules mirror the Rust validator in
 * rust/hesabyar-core/src/validation.rs).
 */
@org.junit.experimental.categories.Category(RustTest::class)
class ManageBackupUseCaseValidationTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  private val useCase = ManageBackupUseCase(FakeRepository())

  /** Runs [useCase.validateBackup] on the Kotlin fallback path (Rust disabled). */
  private suspend fun validateWithKotlinFallback(payload: BackupPayload): BackupValidationResult {
    val previousState = HesabyarApp.isRustInitialized()
    HesabyarApp.setRustInitializedForTesting(false)
    try {
      return useCase.validateBackup(payload)
    } finally {
      HesabyarApp.setRustInitializedForTesting(previousState)
    }
  }

  @Test
  fun wellFormedPayloadIsValid() =
    runTest {
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 1_000_000L,
                description = "t",
                date = 1_700_000_000_000L
              )
            ),
          loans =
            listOf(
              Loan(
                id = 1L,
                personName = "علی",
                type = LoanType.DEBTOR,
                originalAmount = 5_000_000L,
                remainingAmount = 3_000_000L,
                description = "l",
                date = 1_700_000_000_000L
              )
            ),
          installments =
            listOf(
              Installment(title = "قسط", amount = 1_000_000L, dueDate = 1_700_000_000_000L)
            ),
          categories =
            listOf(
              Category(
                id = 1L,
                name = "خوراک",
                key = "food",
                icon = "i",
                color = 0xFF0000L,
                type = CategoryType.EXPENSE
              )
            ),
          paymentHistories =
            listOf(PaymentHistory(id = 1L, loanId = 1L, amount = 100_000L, date = 1_700_000_000_000L))
        )

      val result = useCase.validateBackup(payload)
      assertTrue("expected $result to be Valid", result is BackupValidationResult.Valid)
    }

  @Test
  fun malformedJsonStylePayloadIsSurfacedAsInvalid() =
    runTest {
      // A transaction with a non-positive amount is invalid by every validator
      // (Kotlin fallback and the Rust core agree), so this must not pass as Valid.
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 0L,
                description = "t",
                date = 1_700_000_000_000L
              )
            )
        )

      val result = useCase.validateBackup(payload)
      assertTrue("expected $result to be Invalid for zero-amount transaction", result is BackupValidationResult.Invalid)
    }

  @Test
  fun kotlinFallbackFlagsInvalidBankLoan() =
    runTest {
      // Force the Kotlin validation path (the override beats the loadable lib).
      val previousState = HesabyarApp.isRustInitialized()
      HesabyarApp.setRustInitializedForTesting(false)
      try {
        val payload =
          BackupPayload(
            bankLoans =
              listOf(
                // Blank bank name + non-positive amounts => invalid.
                io.github.mojri.hesabyar.data.BankLoan(
                  bankName = "",
                  loanName = "x",
                  receivedAmount = 0L,
                  monthlyInstallmentAmount = 0L,
                  numberOfInstallments = 0,
                  totalRepayableAmount = 0L,
                  totalInterest = 0L,
                  startDate = 0L,
                  description = ""
                )
              )
          )

        val result = useCase.validateBackup(payload)
        assertTrue(
          "expected $result to be Invalid for malformed bank loan",
          result is BackupValidationResult.Invalid
        )
      } finally {
        HesabyarApp.setRustInitializedForTesting(previousState)
      }
    }

  // --- Kotlin-fallback cross-reference validation (mirrors the Rust validator) ---

  @Test
  fun kotlinFallbackRejectsTransactionWithNonexistentAccount() =
    runTest {
      val payload =
        BackupPayload(
          accounts =
            listOf(
              io.github.mojri.hesabyar.data.AccountEntity(
                id = 1L,
                name = "اصلی",
                type = io.github.mojri.hesabyar.data.AccountType.BANK
              )
            ),
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 1_000L,
                description = "t",
                date = 1_700_000_000_000L,
                accountId = 99L
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue("expected $result to be Invalid for non-existent account", result is BackupValidationResult.Invalid)
    }

  @Test
  fun kotlinFallbackAcceptsLegacyAccountIdWhenAccountsListEmpty() =
    runTest {
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 1_000L,
                description = "t",
                date = 1_700_000_000_000L,
                accountId = io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue("expected $result to be Valid for legacy default account", result is BackupValidationResult.Valid)
    }

  @Test
  fun kotlinFallbackRejectsTransferWithoutDestination() =
    runTest {
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.TRANSFER,
                categoryId = 1L,
                amount = 1_000L,
                description = "transfer",
                date = 1_700_000_000_000L,
                accountId = 1L,
                destinationAccountId = null
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue(
        "expected $result to be Invalid for destination-less transfer",
        result is BackupValidationResult.Invalid
      )
    }

  @Test
  fun kotlinFallbackRejectsTransferWithSameSourceAndDestination() =
    runTest {
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.TRANSFER,
                categoryId = 1L,
                amount = 1_000L,
                description = "transfer",
                date = 1_700_000_000_000L,
                accountId = 1L,
                destinationAccountId = 1L
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue("expected $result to be Invalid for same source/destination", result is BackupValidationResult.Invalid)
    }

  @Test
  fun kotlinFallbackRejectsTransactionWithNonexistentCategoryButToleratesZero() =
    runTest {
      val categories =
        listOf(
          Category(id = 1L, name = "خوراک", key = "food", icon = "i", color = 0xFF0000L, type = CategoryType.EXPENSE)
        )
      val payloadWithMissingCategory =
        BackupPayload(
          categories = categories,
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 99L,
                amount = 1_000L,
                description = "t",
                date = 1_700_000_000_000L,
                accountId = 1L
              )
            )
        )
      val result = validateWithKotlinFallback(payloadWithMissingCategory)
      assertTrue(
        "expected $result to be Invalid for non-existent category",
        result is BackupValidationResult.Invalid
      )

      // Zero is a legacy default tolerated even when categories exist.
      val payloadWithZeroCategory =
        BackupPayload(
          categories = categories,
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 0L,
                amount = 1_000L,
                description = "t",
                date = 1_700_000_000_000L,
                accountId = 1L
              )
            )
        )
      val zeroResult = validateWithKotlinFallback(payloadWithZeroCategory)
      assertTrue(
        "expected $zeroResult to be Valid for legacy category_id=0",
        zeroResult is BackupValidationResult.Valid
      )
    }

  @Test
  fun kotlinFallbackRejectsPaymentHistoryWithNonexistentLoan() =
    runTest {
      val payload =
        BackupPayload(
          paymentHistories =
            listOf(PaymentHistory(id = 1L, loanId = 99L, amount = 100_000L, date = 1_700_000_000_000L))
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue("expected $result to be Invalid for non-existent loan", result is BackupValidationResult.Invalid)
    }

  @Test
  fun kotlinFallbackRejectsTransferWithNonexistentDestinationAccount() =
    runTest {
      // Modern backup (accounts list present): a Transfer destination must
      // resolve to a declared account, mirroring Rust
      // test_backup_rejects_tx_with_nonexistent_destination_account.
      val payload =
        BackupPayload(
          accounts =
            listOf(
              io.github.mojri.hesabyar.data.AccountEntity(
                id = 1L,
                name = "اصلی",
                type = io.github.mojri.hesabyar.data.AccountType.BANK
              )
            ),
          transactions =
            listOf(
              Transaction(
                type = TransactionType.TRANSFER,
                categoryId = 1L,
                amount = 1_000L,
                description = "transfer",
                date = 1_700_000_000_000L,
                accountId = 1L,
                destinationAccountId = 99L
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue(
        "expected $result to be Invalid for non-existent destination account",
        result is BackupValidationResult.Invalid
      )
    }

  @Test
  fun kotlinFallbackRejectsLegacyTransferToNonLegacyDestination() =
    runTest {
      // Legacy backup (no accounts list): the destination check must run
      // independently of the source check. Even with the legacy default source
      // account, a non-legacy destination is an orphan — mirroring Rust
      // test_backup_rejects_non_legacy_dest_account_when_accounts_empty.
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.TRANSFER,
                categoryId = 1L,
                amount = 1_000L,
                description = "transfer",
                date = 1_700_000_000_000L,
                accountId = io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID,
                destinationAccountId = 99L
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue(
        "expected $result to be Invalid for non-legacy destination account",
        result is BackupValidationResult.Invalid
      )
    }

  @Test
  fun kotlinFallbackRejectsPaymentHistoryWithZeroLoanId() =
    runTest {
      // Field-level rule mirroring Rust validate_payment_history: loan_id must
      // be positive (test_payment_history_zero_loan_id_rejected). The zero
      // tolerance only applies to the cross-reference lookup itself.
      val payload =
        BackupPayload(
          paymentHistories =
            listOf(PaymentHistory(id = 1L, loanId = 0L, amount = 100_000L, date = 1_700_000_000_000L))
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue("expected $result to be Invalid for zero loan id", result is BackupValidationResult.Invalid)
    }

  @Test
  fun kotlinFallbackRejectsTransactionWithNegativeCategoryId() =
    runTest {
      // Field-level rule mirroring Rust validate_transaction: category_id must
      // not be negative (test_transaction_negative_category_rejected). Zero and
      // positive-but-unresolvable are handled by the cross-reference checks.
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = -1L,
                amount = 1_000L,
                description = "t",
                date = 1_700_000_000_000L
              )
            )
        )

      val result = validateWithKotlinFallback(payload)
      assertTrue("expected $result to be Invalid for negative category id", result is BackupValidationResult.Invalid)
    }
}
