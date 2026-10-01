package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Closes the direct-coverage gaps of [BackupJsonValidator.validateBackupKotlin]
 * (the Kotlin-only fallback path). The cross-entity reference checks
 * ([BackupReferenceValidator]) and the bank-loan cross-field invariants are
 * already pinned by [BackupJsonValidatorKotlinFallbackTest], so this file only
 * adds the previously-uncovered branches: the envelope checks and the
 * per-collection field-level rules that had no direct case.
 *
 * Every rule below mirrors a pinned Rust rule in validation.rs, matching the
 * convention established in [BackupJsonValidatorKotlinFallbackTest].
 */
class BackupJsonValidatorTest {
  private val validator = BackupJsonValidator()

  private fun validate(backup: BackupPayload): BackupValidationResult = validator.validateBackupKotlin(backup)

  // ---------------------------------------------------------------------------
  // Envelope checks (BackupJsonValidator.kt:66-68)
  // ---------------------------------------------------------------------------

  @Test
  fun validatesCompleteRealisticBackupAsValid() {
    val result = validate(completeRealisticBackup())
    assertTrue("expected a complete backup to be Valid, got $result", result is BackupValidationResult.Valid)
  }

  @Test
  fun rejectsNonPositiveVersion() {
    val payload = BackupPayload(version = 0)
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for non-positive version", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsBlankAppVersion() {
    val payload = BackupPayload(appVersion = "")
    val result = validate(payload)
    assertTrue("expected $result to be invalid for a blank app version", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsNonPositiveTimestamp() {
    val payload = BackupPayload(timestamp = 0L)
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a non-positive timestamp", result is BackupValidationResult.Invalid)
  }

  // ---------------------------------------------------------------------------
  // Transactions (BackupJsonValidator.kt:86-98)
  // ---------------------------------------------------------------------------

  @Test
  fun rejectsNonPositiveTransactionAmount() {
    val payload =
      BackupPayload(
        transactions =
          listOf(Transaction(type = TransactionType.EXPENSE, categoryId = 0L, amount = 0L, description = "t"))
      )
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a zero-amount transaction", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsNonPositiveTransactionDate() {
    val payload =
      BackupPayload(
        transactions =
          listOf(
            Transaction(type = TransactionType.EXPENSE, categoryId = 0L, amount = 1_000L, description = "t", date = 0L)
          )
      )
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a non-positive date", result is BackupValidationResult.Invalid)
  }

  // ---------------------------------------------------------------------------
  // Loans (mirroring validate_loan in validation.rs)
  // ---------------------------------------------------------------------------

  @Test
  fun rejectsLoanWithBlankPersonName() {
    val payload =
      backupWithLoan(
        Loan(
          personName = "",
          type = LoanType.DEBTOR,
          originalAmount = 1_000L,
          remainingAmount = 1_000L,
          description = "l"
        )
      )
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a blank person name", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsLoanWithNonPositiveDate() {
    val payload =
      backupWithLoan(
        Loan(
          personName = "علی",
          type = LoanType.DEBTOR,
          originalAmount = 1_000L,
          remainingAmount = 1_000L,
          description = "l",
          date = 0L
        )
      )
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a non-positive loan date", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsLoanWithNonPositiveOriginalAmount() {
    val payload =
      backupWithLoan(
        Loan(
          personName = "علی",
          type = LoanType.DEBTOR,
          originalAmount = 0L,
          remainingAmount = 1_000L,
          description = "l"
        )
      )
    val result = validate(payload)
    assertTrue(
      "expected $result to be Invalid for a non-positive original amount",
      result is BackupValidationResult.Invalid
    )
  }

  @Test
  fun rejectsLoanWithNegativeRemainingAmount() {
    val payload =
      backupWithLoan(
        Loan(
          personName = "علی",
          type = LoanType.DEBTOR,
          originalAmount = 1_000L,
          remainingAmount = -1L,
          description = "l"
        )
      )
    val result = validate(payload)
    assertTrue(
      "expected $result to be Invalid for a negative remaining amount",
      result is BackupValidationResult.Invalid
    )
  }

  // ---------------------------------------------------------------------------
  // Installments (mirroring validate_installment in validation.rs)
  // ---------------------------------------------------------------------------

  @Test
  fun rejectsInstallmentWithBlankTitle() {
    val payload =
      BackupPayload(installments = listOf(Installment(title = "", amount = 1_000L, dueDate = 1_700_000_000_000L)))
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a blank installment title", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsInstallmentWithNonPositiveAmount() {
    val payload =
      BackupPayload(installments = listOf(Installment(title = "قسط", amount = 0L, dueDate = 1_700_000_000_000L)))
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a zero installment amount", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsInstallmentWithNonPositiveDueDate() {
    val payload = BackupPayload(installments = listOf(Installment(title = "قسط", amount = 1_000L, dueDate = 0L)))
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a non-positive due date", result is BackupValidationResult.Invalid)
  }

  // ---------------------------------------------------------------------------
  // Categories (mirroring validate_category in validation.rs)
  // ---------------------------------------------------------------------------

  @Test
  fun rejectsCategoryWithBlankName() {
    val payload =
      BackupPayload(
        categories =
          listOf(
            Category(id = 1L, name = "", key = "k", icon = "i", color = 0L, type = CategoryType.EXPENSE)
          )
      )
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a blank category name", result is BackupValidationResult.Invalid)
  }

  // ---------------------------------------------------------------------------
  // Payment histories (the loan-id rule is already pinned by the fallback test)
  // ---------------------------------------------------------------------------

  @Test
  fun rejectsPaymentHistoryWithNonPositiveAmount() {
    val payload =
      BackupPayload(
        paymentHistories = listOf(PaymentHistory(id = 1L, loanId = 1L, amount = 0L, date = 1_700_000_000_000L))
      )
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a zero payment amount", result is BackupValidationResult.Invalid)
  }

  @Test
  fun rejectsPaymentHistoryWithNonPositiveDate() {
    val payload =
      BackupPayload(paymentHistories = listOf(PaymentHistory(id = 1L, loanId = 1L, amount = 100_000L, date = 0L)))
    val result = validate(payload)
    assertTrue(
      "expected $result to be Invalid for a non-positive payment date",
      result is BackupValidationResult.Invalid
    )
  }

  // ---------------------------------------------------------------------------
  // Accounts (createdAt == 0 is a tolerated legacy sentinel)
  // ---------------------------------------------------------------------------

  @Test
  fun rejectsAccountWithBlankName() {
    val payload = BackupPayload(accounts = listOf(AccountEntity(id = 1L, name = "", type = AccountType.BANK)))
    val result = validate(payload)
    assertTrue("expected $result to be Invalid for a blank account name", result is BackupValidationResult.Invalid)
  }

  @Test
  fun toleratesAccountWithZeroCreatedAt() {
    // createdAt == 0 is the legacy v6→v7 migration sentinel
    // (MIGRATION_6_7 used DEFAULT 0); the validator must accept it.
    val payload =
      BackupPayload(
        accounts =
          listOf(
            AccountEntity(id = 1L, name = "اصلی", type = AccountType.BANK, createdAt = 0L, updatedAt = 0L)
          )
      )
    val result = validate(payload)
    assertTrue("expected $result to be Valid for a legacy zero createdAt", result is BackupValidationResult.Valid)
  }

  private fun backupWithLoan(loan: Loan): BackupPayload = BackupPayload(loans = listOf(loan))

  private fun completeRealisticBackup(): BackupPayload =
    BackupPayload(
      version = 7,
      timestamp = 1_700_000_000_000L,
      appVersion = "2.0.0",
      accounts = completeAccounts(),
      categories = completeCategories(),
      transactions = completeTransactions(),
      loans = completeLoans(),
      installments = listOf(Installment(title = "قسط", amount = 1_000_000L, dueDate = 1_700_000_000_000L)),
      paymentHistories = listOf(PaymentHistory(id = 1L, loanId = 1L, amount = 100_000L, date = 1_700_000_000_000L)),
      bankLoans = completeBankLoans()
    )

  private fun completeAccounts(): List<AccountEntity> =
    listOf(
      AccountEntity(id = 1L, name = "اصلی", type = AccountType.BANK),
      AccountEntity(id = 2L, name = "فروشگاه", type = AccountType.CASH_WALLET)
    )

  private fun completeCategories(): List<Category> =
    listOf(Category(id = 1L, name = "خوراک", key = "food", icon = "i", color = 0xFF0000L, type = CategoryType.EXPENSE))

  private fun completeTransactions(): List<Transaction> =
    listOf(
      Transaction(
        type = TransactionType.EXPENSE,
        categoryId = 1L,
        amount = 1_000L,
        description = "t",
        date = 1_700_000_000_000L,
        accountId = 1L
      ),
      Transaction(
        type = TransactionType.TRANSFER,
        categoryId = 0L,
        amount = 5_000L,
        description = "transfer",
        date = 1_700_000_000_000L,
        accountId = 1L,
        destinationAccountId = 2L
      )
    )

  private fun completeLoans(): List<Loan> =
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
    )

  private fun completeBankLoans(): List<BankLoan> =
    listOf(
      BankLoan(
        bankName = "بانک ملی",
        loanName = "x",
        receivedAmount = 10_000_000L,
        monthlyInstallmentAmount = 1_000_000L,
        numberOfInstallments = 12,
        totalRepayableAmount = 12_000_000L,
        totalInterest = 2_000_000L,
        startDate = 1_700_000_000_000L,
        description = ""
      )
    )
}
