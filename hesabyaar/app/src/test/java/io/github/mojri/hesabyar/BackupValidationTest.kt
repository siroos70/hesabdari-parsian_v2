package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {
  private fun validateBackup(backup: BackupPayload): BackupValidationResult {
    val errors = ArrayList<String>()

    if (backup.version < 1) {
      errors.add("نسخه پشتیبان نامعتبر است")
    }

    for (tx in backup.transactions) {
      if (tx.amount <= 0) {
        errors.add("مبلغ تراکنش نامعتبر: ${tx.description}")
      }
      if (tx.type !in listOf(TransactionType.EXPENSE, TransactionType.INCOME)) {
        errors.add("نوع تراکنش نامعتبر: ${tx.type}")
      }
    }

    for (loan in backup.loans) {
      if (loan.originalAmount <= 0) {
        errors.add("مبلغ وام نامعتبر: ${loan.personName}")
      }
      if (loan.type !in listOf(LoanType.DEBTOR, LoanType.CREDITOR)) {
        errors.add("نوع وام نامعتبر: ${loan.type}")
      }
    }

    for (inst in backup.installments) {
      if (inst.amount <= 0) {
        errors.add("مبلغ قسط نامعتبر: ${inst.title}")
      }
    }

    if (errors.isNotEmpty()) {
      return BackupValidationResult.Invalid(errors)
    }
    return BackupValidationResult.Valid
  }

  @Test
  fun `valid backup returns Valid`() {
    val backup =
      BackupPayload(
        transactions =
          listOf(
            Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = 1_000_000L, description = "test")
          ),
        loans =
          listOf(
            Loan(
              personName = "Ali",
              type = LoanType.DEBTOR,
              originalAmount = 5_000_000L,
              remainingAmount = 3_000_000L,
              description = "loan"
            )
          ),
        installments =
          listOf(
            Installment(title = "Car loan", amount = 2_000_000L, dueDate = System.currentTimeMillis())
          )
      )
    assertTrue(validateBackup(backup) is BackupValidationResult.Valid)
  }

  @Test
  fun `empty backup is valid`() {
    val backup = BackupPayload()
    assertTrue(validateBackup(backup) is BackupValidationResult.Valid)
  }

  @Test
  fun `negative transaction amount returns error`() {
    val backup =
      BackupPayload(
        transactions =
          listOf(
            Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = -500L, description = "test")
          )
      )
    val result = validateBackup(backup)
    assertTrue(result is BackupValidationResult.Invalid)
    assertEquals(1, (result as BackupValidationResult.Invalid).errors.size)
  }

  @Test
  fun `zero transaction amount returns error`() {
    val backup =
      BackupPayload(
        transactions =
          listOf(
            Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = 0L, description = "test")
          )
      )
    val result = validateBackup(backup)
    assertTrue(result is BackupValidationResult.Invalid)
  }

  @Test
  fun `negative loan amount returns error`() {
    val backup =
      BackupPayload(
        loans =
          listOf(
            Loan(
              personName = "Ali",
              type = LoanType.DEBTOR,
              originalAmount = -1000L,
              remainingAmount = -1000L,
              description = "loan"
            )
          )
      )
    val result = validateBackup(backup)
    assertTrue(result is BackupValidationResult.Invalid)
  }

  @Test
  fun `negative installment amount returns error`() {
    val backup =
      BackupPayload(
        installments =
          listOf(
            Installment(title = "Car", amount = -500L, dueDate = System.currentTimeMillis())
          )
      )
    val result = validateBackup(backup)
    assertTrue(result is BackupValidationResult.Invalid)
  }

  @Test
  fun `multiple errors collected`() {
    val backup =
      BackupPayload(
        transactions =
          listOf(
            Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = -100L, description = "t1"),
            Transaction(type = TransactionType.EXPENSE, categoryId = 1L, amount = 0L, description = "t2")
          ),
        loans =
          listOf(
            Loan(
              personName = "X",
              type = LoanType.DEBTOR,
              originalAmount = -500L,
              remainingAmount = 0L,
              description = "l1"
            )
          )
      )
    val result = validateBackup(backup)
    assertTrue(result is BackupValidationResult.Invalid)
    val errors = (result as BackupValidationResult.Invalid).errors
    assertTrue(errors.size >= 3)
  }

  @Test
  fun `valid DEBTOR and CREDITOR loan types pass`() {
    val backup =
      BackupPayload(
        loans =
          listOf(
            Loan(
              personName = "Ali",
              type = LoanType.DEBTOR,
              originalAmount = 1_000_000L,
              remainingAmount = 500_000L,
              description = "d"
            ),
            Loan(
              personName = "Reza",
              type = LoanType.CREDITOR,
              originalAmount = 2_000_000L,
              remainingAmount = 2_000_000L,
              description = "c"
            )
          )
      )
    assertTrue(validateBackup(backup) is BackupValidationResult.Valid)
  }

  @Test
  fun `BackupPayload default values`() {
    val backup = BackupPayload()
    assertEquals(BuildConfig.BACKUP_SCHEMA_VERSION, backup.version)
    assertTrue(backup.transactions.isEmpty())
    assertTrue(backup.loans.isEmpty())
    assertTrue(backup.installments.isEmpty())
    assertTrue(backup.paymentHistories.isEmpty())
    assertTrue(backup.categories.isEmpty())
    assertEquals(BuildConfig.VERSION_NAME, backup.appVersion)
  }

  @Test
  fun `BackupSettings default dark mode is true`() {
    val settings = BackupSettings()
    assertTrue(settings.darkMode)
  }
}
