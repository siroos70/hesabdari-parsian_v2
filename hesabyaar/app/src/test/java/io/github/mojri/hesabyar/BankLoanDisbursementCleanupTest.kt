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
import io.github.mojri.hesabyar.data.DEFAULT_ACCOUNT_ID
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests verifying disbursement transaction deletion and isolation when a
 * [io.github.mojri.hesabyar.data.BankLoan] is deleted.
 *
 * Separated from [BankLoanDelegateRollbackTest] to keep both test classes
 * under the detekt LargeClass threshold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BankLoanDisbursementCleanupTest {
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

  @Test
  fun deleteBankLoanWithInitialDisbursementDoesNotDeleteMatchingPersonalLoanTransaction() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 50_000_000L

      repo.insertLoanWithInitial(
        Loan(
          personName = "Ali",
          type = LoanType.CREDITOR,
          originalAmount = sharedAmount,
          remainingAmount = sharedAmount,
          description = "personal loan",
          date = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        ),
        recordInitial = true
      )

      val bankLoan =
        testBankLoan().copy(
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        bankLoan,
        listOf(installment()),
        recordInitial = true
      )

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(2, allTxBefore.size)

      val storedBankLoan = database.bankLoanDao().getAllBankLoansBlocking().single()
      repo.deleteBankLoan(storedBankLoan)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(1, allTxAfter.size)
      assertEquals("Ali", allTxAfter.single().personName)
      assertEquals(sharedAmount, allTxAfter.single().amount)
    }

  @Test
  fun deleteBankLoanUsesPersistedTrackedStateIgnoringStaleCallerSnapshot() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 50_000_000L

      val trackedLoan =
        testBankLoan().copy(
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        trackedLoan,
        listOf(installment()),
        recordInitial = true
      )
      assertEquals(
        "one disbursement transaction must exist initially",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      val storedLoan = database.bankLoanDao().getAllBankLoansBlocking().single()
      val staleUntrackedSnapshot = storedLoan.copy(tracked = false, accountId = null)
      repo.deleteBankLoan(staleUntrackedSnapshot)

      assertEquals(
        "disbursement transaction must be cleaned based on persisted tracked state",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      assertEquals(
        "bank loan must be deleted",
        0,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
    }

  @Test
  fun deleteBankLoanDoesNotDeleteUnrelatedTransactionWhenPersistedBankLoanIsUntracked() =
    runTest {
      val repo = createRepository()
      val loansCatId =
        repo.insertCategory(
          Category(
            name = "Loans",
            key = "Loans",
            icon = "HistoryEdu",
            color = 1,
            type = CategoryType.BOTH
          )
        )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 40_000_000L

      val untrackedLoan =
        testBankLoan().copy(
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = false,
          accountId = null
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        untrackedLoan,
        listOf(installment()),
        recordInitial = false
      )

      database.transactionDao().insertTransaction(
        Transaction(
          type = TransactionType.INCOME,
          categoryId = loansCatId,
          amount = sharedAmount,
          date = sharedDate,
          description = "Unrelated transaction",
          personName = null,
          personId = null,
          accountId = DEFAULT_ACCOUNT_ID
        )
      )
      assertEquals(
        "unrelated transaction must be present",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      val storedUntracked = database.bankLoanDao().getAllBankLoansBlocking().single()
      val staleTrackedSnapshot = storedUntracked.copy(tracked = true, accountId = DEFAULT_ACCOUNT_ID)
      repo.deleteBankLoan(staleTrackedSnapshot)

      val remainingTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals(
        "unrelated transaction must not be deleted",
        1,
        remainingTx.size
      )
      assertEquals("Unrelated transaction", remainingTx.single().description)
    }

  @Test
  fun deleteBankLoanFallbackDeletesDisbursementWhenDescriptionWasModified() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 75_000_000L

      val bankLoan =
        testBankLoan().copy(
          loanName = "وام اولیه",
          bankName = "بانک صادرات",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      repo.addBankLoanWithInstallmentsAndInitial(
        bankLoan,
        listOf(installment()),
        recordInitial = true
      )

      val initialTx = database.transactionDao().getAllTransactionsBlocking().single()
      database.transactionDao().updateTransaction(
        initialTx.copy(description = "شرح سفارشی شده توسط کاربر")
      )

      val stored = database.bankLoanDao().getAllBankLoansBlocking().single()
      repo.deleteBankLoan(stored)

      assertEquals(
        "fallback must delete modified-description disbursement",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      assertEquals(
        "bank loan must be deleted",
        0,
        database.bankLoanDao().getAllBankLoansBlocking().size
      )
    }

  @Test
  fun deleteBankLoanWithExactDescriptionDoesNotDeleteAnotherBankLoanWithSameAmountAndDate() =
    runTest {
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 60_000_000L

      val bankLoan1 =
        testBankLoan().copy(
          loanName = "وام خودرو",
          bankName = "بانک ملت",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      val bankLoan2 =
        testBankLoan().copy(
          loanName = "وام مسکن",
          bankName = "بانک سپه",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )

      repo.addBankLoanWithInstallmentsAndInitial(bankLoan1, listOf(installment()), recordInitial = true)
      repo.addBankLoanWithInstallmentsAndInitial(bankLoan2, listOf(installment()), recordInitial = true)

      val allTxBefore = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("two disbursements must exist", 2, allTxBefore.size)

      val storedLoan1 =
        database.bankLoanDao().getAllBankLoansBlocking().first { it.loanName == "وام خودرو" }
      repo.deleteBankLoan(storedLoan1)

      val allTxAfter = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("only one disbursement should survive", 1, allTxAfter.size)
      val survivingTx = allTxAfter.single()
      assertEquals(
        "surviving transaction must belong to bank loan 2",
        "دریافت وام وام مسکن از بانک سپه",
        survivingTx.description
      )
      assertEquals("surviving transaction amount matches", sharedAmount, survivingTx.amount)
    }

  @Test
  fun deleteBankLoanFallbackDoesNotDeleteAnotherBankLoanDisbursementOnDifferentAccount() =
    runTest {
      val secondAccount = AccountEntity(id = 2L, name = "حساب دوم", type = AccountType.BANK)
      database.accountDao().insertAllBlocking(listOf(secondAccount))
      val repo = createRepository()
      repo.insertCategory(
        Category(
          name = "Loans",
          key = "Loans",
          icon = "HistoryEdu",
          color = 1,
          type = CategoryType.BOTH
        )
      )
      val sharedDate = 1_700_000_000_000L
      val sharedAmount = 60_000_000L

      val bankLoan1 =
        testBankLoan().copy(
          loanName = "وام اول",
          bankName = "بانک سپه",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = DEFAULT_ACCOUNT_ID
        )
      val bankLoan2 =
        testBankLoan().copy(
          loanName = "وام دوم",
          bankName = "بانک تجارت",
          receivedAmount = sharedAmount,
          startDate = sharedDate,
          tracked = true,
          accountId = 2L
        )

      repo.addBankLoanWithInstallmentsAndInitial(bankLoan1, listOf(installment()), recordInitial = true)
      repo.addBankLoanWithInstallmentsAndInitial(bankLoan2, listOf(installment()), recordInitial = true)

      val loan1Disbursement =
        database.transactionDao().getAllTransactionsBlocking().first { it.accountId == DEFAULT_ACCOUNT_ID }
      database.transactionDao().updateTransaction(
        loan1Disbursement.copy(description = "شرح دستکاری شده")
      )

      val storedLoan1 =
        database.bankLoanDao().getAllBankLoansBlocking().first { it.accountId == DEFAULT_ACCOUNT_ID }
      repo.deleteBankLoan(storedLoan1)

      val remainingTx = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("bank loan 2 disbursement on different account must survive", 1, remainingTx.size)
      val surviving = remainingTx.single()
      assertEquals(2L, surviving.accountId)
      assertEquals(sharedAmount, surviving.amount)
    }
}
