package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.BankLoanDelegate
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.InstallmentDao
import io.github.mojri.hesabyar.data.TransactionLinkDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rollback coverage for [io.github.mojri.hesabyar.data.BankLoanDelegate]'s
 * transactional operations. A failure in any statement inside
 * `database.withTransaction` must leave both the bank_loans and installments
 * tables untouched — a half-applied cascade would corrupt the ledger.
 *
 * Separate from RepositoryLogicTest to stay under the detekt class-size
 * threshold; the failing-DAO decorator mirrors PersonRepositoryTest.RacePersonDao.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BankLoanDelegateRollbackTest {
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

  private fun createRepository(installmentDao: InstallmentDao = database.installmentDao()): HesabyarRepository =
    HesabyarRepository(
      database.transactionDao(),
      database.loanDao(),
      installmentDao,
      database.paymentHistoryDao(),
      database.categoryDao(),
      database.bankLoanDao(),
      database.accountDao(),
      database.personDao(),
      database
    )

  private fun testBankLoan() =
    BankLoan(
      bankName = "بانک ملت",
      loanName = "وام خودرو",
      receivedAmount = 100_000_000L,
      monthlyInstallmentAmount = 10_000_000L,
      numberOfInstallments = 2,
      totalRepayableAmount = 120_000_000L,
      totalInterest = 20_000_000L,
      startDate = 1_700_000_000_000L,
      description = "test"
    )

  private fun installment(bankLoanId: Long = 0L) =
    Installment(
      title = "قسط",
      amount = 10_000_000L,
      dueDate = 1_700_000_000_000L,
      bankLoanId = bankLoanId
    )

  /** Decorator that can force specific installment statements to throw. */
  private class FailingInstallmentDao(
    private val delegate: InstallmentDao
  ) : InstallmentDao by delegate {
    var failOnInsert = false
    var failOnDeleteByBankLoanId = false

    override suspend fun insertInstallment(installment: Installment): Long {
      if (failOnInsert) throw IllegalStateException("forced insert failure")
      return delegate.insertInstallment(installment)
    }

    override suspend fun deleteInstallmentsByBankLoanId(bankLoanId: Long) {
      if (failOnDeleteByBankLoanId) throw IllegalStateException("forced delete failure")
      delegate.deleteInstallmentsByBankLoanId(bankLoanId)
    }
  }

  /** Decorator that can force specific transaction link operations to throw. */
  private class FailingTransactionLinkDao(
    private val delegate: TransactionLinkDao
  ) : TransactionLinkDao by delegate {
    var failOnDeleteBankLoanDisbursement = false
    var failOnDeleteTransactionsForInstallment = false

    override suspend fun deleteBankLoanDisbursementTransaction(
      categoryId: Long,
      amount: Long,
      date: Long,
      accountId: Long,
      description: String?
    ): Int {
      if (failOnDeleteBankLoanDisbursement) {
        throw IllegalStateException("forced disbursement cleanup failure")
      }
      return delegate.deleteBankLoanDisbursementTransaction(
        categoryId,
        amount,
        date,
        accountId,
        description
      )
    }

    override suspend fun deleteTransactionsForInstallment(installmentId: Long) {
      if (failOnDeleteTransactionsForInstallment) {
        throw IllegalStateException("forced installment transaction cleanup failure")
      }
      delegate.deleteTransactionsForInstallment(installmentId)
    }
  }

  private fun createBankLoanDelegate(
    installmentDao: InstallmentDao = database.installmentDao(),
    transactionLinkDao: TransactionLinkDao = database.transactionLinkDao()
  ): BankLoanDelegate =
    BankLoanDelegate(
      database.bankLoanDao(),
      installmentDao,
      transactionLinkDao,
      database.transactionDao(),
      database.categoryDao(),
      database
    )

  @Test
  fun deleteBankLoanRemovesLinkedExpensesOfPaidInstallments() =
    runTest {
      // The cascade must not strand the expenses behind paid installments:
      // deleting the bank loan removes the rows, so their linked money would
      // otherwise keep showing in reports.
      val repo = createRepository()
      database.categoryDao().insertCategory(
        Category(
          name = "Installments",
          key = "Installments",
          icon = "CreditCard",
          color = 1,
          type = CategoryType.EXPENSE
        )
      )
      val loanId =
        repo.addBankLoanWithInstallments(
          testBankLoan(),
          listOf(installment(), installment())
        )
      val stored = database.installmentDao().getInstallmentsByBankLoanIdSync(loanId)
      // One paid installment carries a linked expense, the other stays unpaid.
      // Must be tracked=true with accountId to post a linked expense per Phase 2.
      repo.updateInstallment(
        stored.first().copy(isPaid = true, tracked = true, accountId = DEFAULT_ACCOUNT_ID)
      )
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      repo.deleteBankLoan(database.bankLoanDao().getAllBankLoansBlocking().single())

      assertEquals("bank loan gone", 0, database.bankLoanDao().getAllBankLoansBlocking().size)
      assertEquals("installments gone", 0, database.installmentDao().getAllInstallmentsSync().size)
      assertEquals(
        "the paid installment's linked expense must die with the cascade",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun addbankloanwithinstallmentsRollsBackWhenInstallmentInsertFails() =
    runTest {
      val failingDao = FailingInstallmentDao(database.installmentDao()).apply { failOnInsert = true }
      val repo = createRepository(failingDao)

      val threw =
        try {
          repo.addBankLoanWithInstallments(testBankLoan(), listOf(installment(), installment()))
          false
        } catch (expected: IllegalStateException) {
          true
        }

      assertTrue("forced insert failure must propagate", threw)
      assertEquals(
        "bank loan insert must roll back with the failed installment",
        0,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
      assertEquals(
        "no installment may survive the failed insert",
        0,
        database.installmentDao().getAllInstallmentsBlocking().size
      )
    }

  @Test
  fun deletebankloanRollsBackWhenInstallmentDeleteFails() =
    runTest {
      // Seed with the real DAO so the cascade has something to undo.
      val repo = createRepository()
      val loanId =
        repo.addBankLoanWithInstallments(
          testBankLoan(),
          listOf(installment(), installment())
        )
      assertEquals(2, database.installmentDao().getAllInstallmentsBlocking().size)

      val failingRepo =
        createRepository(
          FailingInstallmentDao(database.installmentDao()).apply { failOnDeleteByBankLoanId = true }
        )
      val storedLoan = database.bankLoanDao().getAllBankLoansBlocking().single()

      val threw =
        try {
          failingRepo.deleteBankLoan(storedLoan)
          false
        } catch (expected: IllegalStateException) {
          true
        }

      assertTrue("forced delete failure must propagate", threw)
      assertEquals(
        "bank loan must survive the failed cascade",
        1,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
      assertEquals(
        "installments must survive the failed cascade",
        2,
        database.installmentDao().getAllInstallmentsBlocking().size
      )
      assertTrue(loanId > 0)
    }

  @Test
  fun addBankLoanWithInstallmentsRejectsTrackedWithoutValidAccountAndPersistsNothing() =
    runTest {
      val repo = createRepository()
      val invalidTrackedLoan =
        testBankLoan().copy(
          tracked = true,
          accountId = null
        )
      try {
        repo.addBankLoanWithInstallments(invalidTrackedLoan, listOf(installment()))
        org.junit.Assert.fail("addBankLoanWithInstallments with tracked=true and accountId=null must throw")
      } catch (expected: IllegalArgumentException) {
        // Expected
      }

      assertEquals(0, database.bankLoanDao().getAllBankLoansBlocking().size)
      assertEquals(0, database.installmentDao().getAllInstallmentsBlocking().size)
    }

  @Test
  fun addBankLoanWithInstallmentsForcesInstallmentsUntrackedAndAccountIdNull() =
    runTest {
      val repo = createRepository()
      val inputInstallment =
        installment().copy(
          tracked = true,
          accountId = 5L
        )
      val loanId = repo.addBankLoanWithInstallments(testBankLoan(), listOf(inputInstallment))
      val storedInstallments = database.installmentDao().getInstallmentsByBankLoanIdSync(loanId)
      assertEquals(1, storedInstallments.size)
      val stored = storedInstallments.single()
      org.junit.Assert.assertFalse("bank loan installment must be forced to untracked", stored.tracked)
      org.junit.Assert.assertNull("bank loan installment must have null accountId", stored.accountId)
    }

  private suspend fun seedTrackedBankLoanWithDisbursement(
    repo: HesabyarRepository,
    amount: Long = 80_000_000L,
    date: Long = 1_700_000_000_000L
  ): BankLoan {
    repo.insertCategory(
      Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 1, type = CategoryType.BOTH)
    )
    val bankLoan =
      testBankLoan().copy(
        receivedAmount = amount,
        startDate = date,
        tracked = true,
        accountId = DEFAULT_ACCOUNT_ID
      )
    repo.addBankLoanWithInstallmentsAndInitial(
      bankLoan,
      listOf(installment(), installment()),
      recordInitial = true
    )
    return database.bankLoanDao().getAllBankLoansBlocking().single()
  }

  @Test
  fun deleteBankLoanRollsBackWhenDisbursementTransactionCleanupFails() =
    runTest {
      val repo = createRepository()
      val storedLoan = seedTrackedBankLoanWithDisbursement(repo)
      assertEquals(
        "disbursement transaction must be seeded",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      assertEquals(
        "installments must be seeded",
        2,
        database.installmentDao().getAllInstallmentsBlocking().size
      )

      val failingLinkDao =
        FailingTransactionLinkDao(database.transactionLinkDao()).apply {
          failOnDeleteBankLoanDisbursement = true
        }
      val failingDelegate = createBankLoanDelegate(transactionLinkDao = failingLinkDao)

      val threw =
        try {
          failingDelegate.deleteBankLoan(storedLoan)
          false
        } catch (expected: IllegalStateException) {
          true
        }

      assertTrue("forced disbursement cleanup failure must propagate", threw)
      assertEquals(
        "bank loan must survive the failed cleanup",
        1,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
      assertEquals(
        "installments must survive the failed cleanup",
        2,
        database.installmentDao().getAllInstallmentsBlocking().size
      )
      assertEquals(
        "disbursement transaction must survive the failed cleanup",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun deleteBankLoanRollsBackWhenPaidInstallmentExpenseCleanupFails() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Installments",
          key = "Installments",
          icon = "CreditCard",
          color = 1,
          type = CategoryType.EXPENSE
        )
      )
      val loanId =
        repo.addBankLoanWithInstallments(
          testBankLoan(),
          listOf(installment(), installment())
        )
      val stored = database.installmentDao().getInstallmentsByBankLoanIdSync(loanId)
      repo.updateInstallment(
        stored.first().copy(isPaid = true, tracked = true, accountId = DEFAULT_ACCOUNT_ID)
      )
      assertEquals(
        "one linked expense transaction must exist",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      val failingLinkDao =
        FailingTransactionLinkDao(database.transactionLinkDao()).apply {
          failOnDeleteTransactionsForInstallment = true
        }
      val failingDelegate = createBankLoanDelegate(transactionLinkDao = failingLinkDao)

      val storedLoan = database.bankLoanDao().getAllBankLoansBlocking().single()
      val threw =
        try {
          failingDelegate.deleteBankLoan(storedLoan)
          false
        } catch (expected: IllegalStateException) {
          true
        }

      assertTrue("forced installment expense cleanup failure must propagate", threw)
      assertEquals(
        "bank loan must survive failed installment expense cleanup",
        1,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
      assertEquals(
        "installments must survive failed installment expense cleanup",
        2,
        database.installmentDao().getAllInstallmentsBlocking().size
      )
      assertEquals(
        "linked expense transaction must survive failed installment expense cleanup",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }
}
