package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.TransactionType
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
class BankLoanTrackedAccountingTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database.accountDao().insertAllBlocking(
      listOf(
        AccountEntity.DEFAULT_ACCOUNT,
        AccountEntity(
          id = 2L,
          name = "Mellat Account",
          type = AccountType.BANK,
          initialBalance = 0L,
          displayOrder = 1
        )
      )
    )
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

  private fun sampleBankLoan(
    tracked: Boolean,
    accountId: Long?,
    receivedAmount: Long = 50_000_000L
  ): BankLoan =
    BankLoan(
      bankName = "بانک سپه",
      loanName = "وام مسکن",
      receivedAmount = receivedAmount,
      monthlyInstallmentAmount = 5_000_000L,
      numberOfInstallments = 12,
      totalRepayableAmount = 60_000_000L,
      totalInterest = 10_000_000L,
      startDate = 1_700_000_000_000L,
      description = "توضیحات",
      isSettled = false,
      tracked = tracked,
      accountId = accountId
    )

  private fun sampleInstallments(count: Int = 12): List<Installment> =
    (1..count).map { i ->
      Installment(
        title = "قسط $i",
        amount = 5_000_000L,
        dueDate = 1_700_000_000_000L + i * 30L * 24 * 3600 * 1000,
        reminderEnabled = true,
        notes = "",
        bankLoanId = null,
        tracked = false,
        accountId = null
      )
    }

  @Test(expected = IllegalArgumentException::class)
  fun trackedBankLoanWithNullAccountIdThrowsIllegalArgumentException() =
    runTest {
      val repo = createRepository()
      repo.addBankLoanWithInstallmentsAndInitial(
        bankLoan = sampleBankLoan(tracked = true, accountId = null),
        installments = sampleInstallments(),
        recordInitial = true
      )
    }

  @Test
  fun untrackedBankLoanProducesZeroTransactions() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )

      val bankLoan = sampleBankLoan(tracked = false, accountId = null)
      val id =
        repo.addBankLoanWithInstallmentsAndInitial(
          bankLoan = bankLoan,
          installments = sampleInstallments(),
          recordInitial = true
        )
      assertTrue("bank loan inserted successfully", id > 0)

      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("untracked bank loan must produce zero transactions", 0, allTx.size)
    }

  @Test
  fun trackedBankLoanWithRecordInitialFalseProducesZeroTransactions() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )

      val bankLoan = sampleBankLoan(tracked = true, accountId = 2L)
      val id =
        repo.addBankLoanWithInstallmentsAndInitial(
          bankLoan = bankLoan,
          installments = sampleInstallments(),
          recordInitial = false
        )
      assertTrue("bank loan inserted successfully", id > 0)

      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("tracked bank loan with recordInitial=false produces zero transactions", 0, allTx.size)
    }

  @Test
  fun trackedBankLoanWithRecordInitialTruePostsDisbursementToChosenAccount() =
    runTest {
      val repo = createRepository()
      val loansCatId =
        repo.insertCategory(
          Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
        )

      val bankLoan = sampleBankLoan(tracked = true, accountId = 2L, receivedAmount = 50_000_000L)
      val id =
        repo.addBankLoanWithInstallmentsAndInitial(
          bankLoan = bankLoan,
          installments = sampleInstallments(),
          recordInitial = true
        )
      assertTrue("bank loan inserted successfully", id > 0)

      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("tracked bank loan with recordInitial=true produces exactly 1 transaction", 1, allTx.size)
      val tx = allTx.single()
      assertEquals("transaction must post to the chosen account", 2L, tx.accountId)
      assertEquals("transaction type must be INCOME (disbursement inflow)", TransactionType.INCOME, tx.type)
      assertEquals("transaction category must be Loans", loansCatId, tx.categoryId)
      assertEquals("transaction amount matches receivedAmount", 50_000_000L, tx.amount)
    }

  @Test
  fun bankLoanInstallmentsStrictlyEnforceLedgerOnlyDefaultsEvenIfCallerPassesTracked() =
    runTest {
      val repo = createRepository()
      val bankLoan = sampleBankLoan(tracked = true, accountId = 2L)
      // Caller deliberately passes installments with tracked=true and accountId=99L
      val callerProvidedInstallments =
        listOf(
          Installment(
            title = "قسط 1",
            amount = 5_000_000L,
            dueDate = 1_700_000_000_000L,
            reminderEnabled = true,
            notes = "",
            bankLoanId = null,
            tracked = true,
            accountId = 99L
          )
        )
      val id =
        repo.addBankLoanWithInstallmentsAndInitial(
          bankLoan = bankLoan,
          installments = callerProvidedInstallments,
          recordInitial = false
        )
      val installments = repo.getInstallmentsByBankLoanId(id)
      assertEquals(1, installments.size)
      assertFalse("installments MUST be normalized to tracked=false", installments[0].tracked)
      assertNull("installments MUST be normalized to accountId=null", installments[0].accountId)
    }

  @Test
  fun trackedBankLoanWithInitialLegRollsBackWhenLoansCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Loans" category is seeded!
      var threw = false
      try {
        repo.addBankLoanWithInstallmentsAndInitial(
          bankLoan = sampleBankLoan(tracked = true, accountId = 2L),
          installments = sampleInstallments(3),
          recordInitial = true
        )
      } catch (e: IllegalStateException) {
        threw = true
        assertTrue(e.message?.contains("Loans category is missing") == true)
      }
      assertTrue("must throw IllegalStateException when Loans category is missing", threw)

      // ATOMIC ROLLBACK ASSERTIONS: zero bank loans, zero installments, zero transactions
      assertEquals("zero bank loans after rollback", 0, database.bankLoanDao().getAllBankLoansBlocking().size)
      assertEquals("zero installments after rollback", 0, database.installmentDao().getAllInstallmentsBlocking().size)
      assertEquals("zero transactions after rollback", 0, database.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun untrackedBankLoanSucceedsEvenIfLoansCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Loans" category is seeded!
      val id =
        repo.addBankLoanWithInstallmentsAndInitial(
          bankLoan = sampleBankLoan(tracked = false, accountId = null),
          installments = sampleInstallments(3),
          recordInitial = false
        )
      assertTrue("untracked bank loan succeeds without Loans category", id > 0)
      assertEquals(1, database.bankLoanDao().getAllBankLoansBlocking().size)
      assertEquals(3, database.installmentDao().getAllInstallmentsBlocking().size)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
    }
}
