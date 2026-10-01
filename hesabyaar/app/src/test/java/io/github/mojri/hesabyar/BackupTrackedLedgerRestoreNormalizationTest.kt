package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupTrackedLedgerRestoreNormalizationTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun createRepository(): HesabyarRepository =
    HesabyarRepository(
      database.transactionDao(),
      database.loanDao(),
      database.installmentDao(),
      database.paymentHistoryDao(),
      database.categoryDao(),
      database.bankLoanDao(),
      database.accountDao(),
      database.personDao(),
      database
    )

  @Test
  fun replaceAllFromBackupNormalizesTrackedInstallmentWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 1L,
                title = "Invalid tracked",
                amount = 1_000_000L,
                dueDate = 1000L,
                tracked = true,
                accountId = null
              ),
              Installment(
                id = 2L,
                title = "Stale untracked",
                amount = 2_000_000L,
                dueDate = 1000L,
                tracked = false,
                accountId = 99L
              ),
              Installment(
                id = 3L,
                title = "Valid tracked",
                amount = 3_000_000L,
                dueDate = 1000L,
                tracked = true,
                accountId = DEFAULT_ACCOUNT_ID
              )
            )
        )

      repo.replaceAllFromBackup(payload)

      val stored = database.installmentDao().getAllInstallmentsSync()
      val invalidTracked = stored.first { it.title == "Invalid tracked" }
      assertFalse(invalidTracked.tracked)
      assertNull(invalidTracked.accountId)

      val staleUntracked = stored.first { it.title == "Stale untracked" }
      assertFalse(staleUntracked.tracked)
      assertNull(staleUntracked.accountId)

      val validTracked = stored.first { it.title == "Valid tracked" }
      assertTrue(validTracked.tracked)
      assertEquals(DEFAULT_ACCOUNT_ID, validTracked.accountId)
    }

  @Test
  fun replaceAllFromBackupNormalizesBankLoanInstallmentToUntracked() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 5L,
                title = "Bank loan installment",
                amount = 1_500_000L,
                dueDate = 1000L,
                bankLoanId = 10L,
                tracked = true,
                accountId = DEFAULT_ACCOUNT_ID
              )
            )
        )

      repo.replaceAllFromBackup(payload)

      val stored = database.installmentDao().getAllInstallmentsSync()
      val inst = stored.first { it.title == "Bank loan installment" }
      assertFalse("bank loan installment must be normalized to untracked", inst.tracked)
      assertNull(inst.accountId)
    }

  @Test
  fun mergeFromBackupNormalizesTrackedInstallmentWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 10L,
                title = "Merge invalid tracked",
                amount = 500_000L,
                dueDate = 2000L,
                tracked = true,
                accountId = 0L
              ),
              Installment(
                id = 20L,
                title = "Merge stale untracked",
                amount = 700_000L,
                dueDate = 2000L,
                tracked = false,
                accountId = 42L
              )
            )
        )

      repo.mergeFromBackup(payload)

      val stored = database.installmentDao().getAllInstallmentsSync()
      val invalid = stored.first { it.title == "Merge invalid tracked" }
      assertFalse(invalid.tracked)
      assertNull(invalid.accountId)

      val stale = stored.first { it.title == "Merge stale untracked" }
      assertFalse(stale.tracked)
      assertNull(stale.accountId)
    }

  @Test
  fun replaceAllFromBackupNormalizesTrackedLoanWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          loans =
            listOf(
              Loan(
                id = 1L,
                personName = "Ali",
                type = LoanType.CREDITOR,
                originalAmount = 1_000_000L,
                remainingAmount = 1_000_000L,
                description = "",
                date = 1000L,
                tracked = true,
                accountId = null
              ),
              Loan(
                id = 2L,
                personName = "Reza",
                type = LoanType.DEBTOR,
                originalAmount = 2_000_000L,
                remainingAmount = 2_000_000L,
                description = "",
                date = 1000L,
                tracked = false,
                accountId = 88L
              ),
              Loan(
                id = 3L,
                personName = "Hassan",
                type = LoanType.CREDITOR,
                originalAmount = 3_000_000L,
                remainingAmount = 3_000_000L,
                description = "",
                date = 1000L,
                tracked = true,
                accountId = DEFAULT_ACCOUNT_ID
              )
            )
        )

      repo.replaceAllFromBackup(payload)

      val stored = database.loanDao().getAllLoansSync()
      val invalidLoan = stored.first { it.personName == "Ali" }
      assertFalse(invalidLoan.tracked)
      assertNull(invalidLoan.accountId)

      val staleLoan = stored.first { it.personName == "Reza" }
      assertFalse(staleLoan.tracked)
      assertNull(staleLoan.accountId)

      val validLoan = stored.first { it.personName == "Hassan" }
      assertTrue(validLoan.tracked)
      assertEquals(DEFAULT_ACCOUNT_ID, validLoan.accountId)
    }

  @Test
  fun mergeFromBackupNormalizesTrackedLoanWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          loans =
            listOf(
              Loan(
                id = 10L,
                personName = "Sara",
                type = LoanType.DEBTOR,
                originalAmount = 1_000_000L,
                remainingAmount = 1_000_000L,
                description = "",
                date = 1000L,
                tracked = true,
                accountId = 0L
              )
            )
        )

      repo.mergeFromBackup(payload)

      val stored = database.loanDao().getAllLoansSync()
      val loan = stored.first { it.personName == "Sara" }
      assertFalse(loan.tracked)
      assertNull(loan.accountId)
    }

  @Test
  fun replaceAllFromBackupNormalizesTrackedBankLoanWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          bankLoans =
            listOf(
              createTestBankLoan(1L, "Bank A", tracked = true, accountId = null),
              createTestBankLoan(2L, "Bank B", tracked = false, accountId = 77L),
              createTestBankLoan(3L, "Bank C", tracked = true, accountId = DEFAULT_ACCOUNT_ID)
            )
        )

      repo.replaceAllFromBackup(payload)

      val stored = database.bankLoanDao().getAllBankLoansBlocking()
      val invalidBankLoan = stored.first { it.bankName == "Bank A" }
      assertFalse(invalidBankLoan.tracked)
      assertNull(invalidBankLoan.accountId)

      val staleBankLoan = stored.first { it.bankName == "Bank B" }
      assertFalse(staleBankLoan.tracked)
      assertNull(staleBankLoan.accountId)

      val validBankLoan = stored.first { it.bankName == "Bank C" }
      assertTrue(validBankLoan.tracked)
      assertEquals(DEFAULT_ACCOUNT_ID, validBankLoan.accountId)
    }

  @Test
  fun mergeFromBackupNormalizesTrackedBankLoanWithInvalidAccountId() =
    runTest {
      val repo = createRepository()
      val payload =
        BackupPayload(
          bankLoans = listOf(createTestBankLoan(10L, "Bank M", tracked = true, accountId = 0L))
        )

      repo.mergeFromBackup(payload)

      val stored = database.bankLoanDao().getAllBankLoansBlocking()
      val bankLoan = stored.first { it.bankName == "Bank M" }
      assertFalse(bankLoan.tracked)
      assertNull(bankLoan.accountId)
    }

  private fun createTestBankLoan(
    id: Long,
    bankName: String,
    tracked: Boolean,
    accountId: Long?
  ) = BankLoan(
    id = id,
    bankName = bankName,
    loanName = "Test Loan",
    receivedAmount = 50_000_000L,
    totalRepayableAmount = 60_000_000L,
    totalInterest = 10_000_000L,
    monthlyInstallmentAmount = 5_000_000L,
    numberOfInstallments = 12,
    startDate = 1000L,
    description = "",
    tracked = tracked,
    accountId = accountId
  )
}
