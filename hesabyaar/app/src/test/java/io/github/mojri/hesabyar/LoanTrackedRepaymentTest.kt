package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LoanTrackedRepaymentTest {
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
          name = "Second Account",
          type = io.github.mojri.hesabyar.data.AccountType.BANK,
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

  @Test
  fun untrackedLoanRepaymentProducesZeroTransactions() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )

      val loanId =
        repo.insertLoan(
          Loan(
            personName = "Ali",
            type = LoanType.CREDITOR,
            originalAmount = 10_000_000L,
            remainingAmount = 10_000_000L,
            description = "untracked loan",
            tracked = false,
            accountId = null
          )
        )

      val success = repo.addPaymentToLoan(loanId, 2_000_000L, "repayment 1", null)
      assertTrue("repayment should succeed", success)

      // Payment history must be recorded
      val history = repo.getPaymentHistoryForLoan(loanId).first()
      assertEquals("1 payment history recorded", 1, history.size)
      assertEquals(2_000_000L, history[0].amount)

      // Loan remainingAmount must be reduced
      val loan = database.loanDao().getLoanById(loanId)
      assertEquals(8_000_000L, loan?.remainingAmount)

      // CRITICAL ASSERTION: untracked repayment MUST NOT produce any transaction
      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("untracked loan repayment must produce zero transactions", 0, allTx.size)
    }

  @Test
  fun trackedLoanRepaymentPostsTransactionToChosenAccount() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )

      val loanId =
        repo.insertLoan(
          Loan(
            personName = "Reza",
            type = LoanType.CREDITOR,
            originalAmount = 5_000_000L,
            remainingAmount = 5_000_000L,
            description = "tracked loan",
            tracked = true,
            accountId = 2L
          )
        )

      val success = repo.addPaymentToLoan(loanId, 1_000_000L, "first payment", null)
      assertTrue("repayment should succeed", success)

      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("tracked loan repayment must produce exactly 1 transaction", 1, allTx.size)
      val tx = allTx.single()
      assertEquals("transaction must post to the chosen account", 2L, tx.accountId)
      assertEquals("transaction amount matches repayment", 1_000_000L, tx.amount)
    }

  @Test(expected = IllegalArgumentException::class)
  fun trackedLoanWithNullAccountIdThrowsIllegalArgumentException() =
    runTest {
      val repo = createRepository()
      repo.insertLoanWithInitial(
        Loan(
          personName = "Sara",
          type = LoanType.CREDITOR,
          originalAmount = 3_000_000L,
          remainingAmount = 3_000_000L,
          description = "invalid tracked loan",
          tracked = true,
          accountId = null
        ),
        recordInitial = true
      )
    }

  @Test
  fun trackedLoanWithInitialLegRollsBackWhenLoansCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Loans" category is seeded!
      var threw = false
      try {
        repo.insertLoanWithInitial(
          Loan(
            personName = "Sara",
            type = LoanType.CREDITOR,
            originalAmount = 3_000_000L,
            remainingAmount = 3_000_000L,
            description = "loan without category",
            tracked = true,
            accountId = 2L
          ),
          recordInitial = true
        )
      } catch (e: IllegalStateException) {
        threw = true
        assertTrue(e.message?.contains("Loans category is missing") == true)
      }
      assertTrue("must throw IllegalStateException when Loans category is missing", threw)

      // ATOMIC ROLLBACK ASSERTIONS: zero loans and zero transactions must be written
      val allLoans = database.loanDao().getAllLoansBlocking()
      assertEquals("loan insert must roll back cleanly on missing category", 0, allLoans.size)
      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("zero transactions must be written", 0, allTx.size)
    }

  @Test
  fun untrackedLoanWithInitialLegFalseSucceedsEvenIfLoansCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Loans" category is seeded!
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Sara",
            type = LoanType.CREDITOR,
            originalAmount = 3_000_000L,
            remainingAmount = 3_000_000L,
            description = "untracked loan without category",
            tracked = false,
            accountId = null
          ),
          recordInitial = false
        )
      assertTrue("untracked loan succeeds without category", loanId > 0)
      val allLoans = database.loanDao().getAllLoansBlocking()
      assertEquals(1, allLoans.size)
      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("untracked loan produces zero transactions", 0, allTx.size)
    }

  @Test
  fun trackedLoanWithRecordInitialFalsePostsNoTransaction() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )
      val loanId =
        repo.insertLoanWithInitial(
          Loan(
            personName = "Nima",
            type = LoanType.CREDITOR,
            originalAmount = 4_000_000L,
            remainingAmount = 4_000_000L,
            description = "tracked, history only",
            tracked = true,
            accountId = 2L
          ),
          recordInitial = false
        )
      assertTrue(loanId > 0)
      val allTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("recordInitial=false must post zero transactions even when tracked", 0, allTx.size)
    }

  @Test
  fun creditorInitialLegPostsIncomeAndDebtorInitialLegPostsExpense() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )
      // CREDITOR = "I owe": receiving the loan money is an inflow (INCOME).
      repo.insertLoanWithInitial(
        Loan(
          personName = "Ali",
          type = LoanType.CREDITOR,
          originalAmount = 6_000_000L,
          remainingAmount = 6_000_000L,
          description = "i owe",
          tracked = true,
          accountId = 2L
        ),
        recordInitial = true
      )
      // DEBTOR = "owed to me": lending the money out is an outflow (EXPENSE).
      repo.insertLoanWithInitial(
        Loan(
          personName = "Bita",
          type = LoanType.DEBTOR,
          originalAmount = 7_000_000L,
          remainingAmount = 7_000_000L,
          description = "owed to me",
          tracked = true,
          accountId = 2L
        ),
        recordInitial = true
      )
      val creditorTx =
        database.transactionDao().getAllTransactionsBlocking().single { it.personName == "Ali" }
      assertEquals(TransactionType.INCOME, creditorTx.type)
      assertEquals(6_000_000L, creditorTx.amount)
      val debtorTx =
        database.transactionDao().getAllTransactionsBlocking().single { it.personName == "Bita" }
      assertEquals(TransactionType.EXPENSE, debtorTx.type)
      assertEquals(7_000_000L, debtorTx.amount)
    }

  @Test
  fun untrackedRepaymentSucceedsWithoutLoansCategory() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Loans" category is seeded — the category is only needed
      // when a repayment actually posts a transaction (plan 011 D2).
      val loanId =
        repo.insertLoan(
          Loan(
            personName = "Omid",
            type = LoanType.CREDITOR,
            originalAmount = 5_000_000L,
            remainingAmount = 5_000_000L,
            description = "untracked no category",
            tracked = false,
            accountId = null
          )
        )
      val success = repo.addPaymentToLoan(loanId, 1_000_000L, "partial", null)
      assertTrue("untracked repayment must not depend on the Loans category", success)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
      assertEquals(1, repo.getPaymentHistoryForLoan(loanId).first().size)
    }

  @Test
  fun deleteLoanRemovesTrackedInitialLegTransaction() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(name = "Loans", key = "Loans", icon = "HistoryEdu", color = 0xFF4CAF50L, type = CategoryType.BOTH)
      )
      val loan =
        Loan(
          personName = "Kian",
          type = LoanType.CREDITOR,
          originalAmount = 9_000_000L,
          remainingAmount = 9_000_000L,
          description = "initial leg cleanup",
          tracked = true,
          accountId = 2L
        )
      val loanId = repo.insertLoanWithInitial(loan, recordInitial = true)
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)
      repo.deleteLoan(loan.copy(id = loanId))
      assertEquals(
        "deleting a tracked loan must remove its initial-leg transaction too",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun addPaymentToTrackedLoanThrowsAndLeavesStateUnchangedWhenLoansCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Seed tracked loan directly without Loans category
      val loanId =
        database.loanDao().insertLoan(
          Loan(
            personName = "Ali",
            type = LoanType.CREDITOR,
            originalAmount = 5_000_000L,
            remainingAmount = 5_000_000L,
            description = "test repayment rejection",
            tracked = true,
            accountId = 2L
          )
        )
      var threw = false
      try {
        repo.addPaymentToLoan(loanId, 2_000_000L, "repayment without category", null)
      } catch (e: IllegalStateException) {
        threw = true
        assertTrue(e.message?.contains("Loans category is missing") == true)
      }
      assertTrue("must throw IllegalStateException when Loans category is missing", threw)

      val storedLoan = database.loanDao().getLoanById(loanId)
      assertEquals("loan remainingAmount must remain unchanged", 5_000_000L, storedLoan?.remainingAmount)
      assertEquals(
        "zero payment histories must be written",
        0,
        database.paymentHistoryDao().getAllPaymentHistoriesBlocking().size
      )
      assertEquals("zero transactions must be written", 0, database.transactionDao().getAllTransactionsBlocking().size)
    }
}
