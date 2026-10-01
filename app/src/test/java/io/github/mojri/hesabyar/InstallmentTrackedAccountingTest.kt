package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.AppDatabase
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

/**
 * Phase 2 real-database coverage for tracked vs untracked installment accounting
 * across creation and payment toggles.
 *
 * Enforces:
 * - tracked=true requires non-null, valid (>0) accountId before withTransaction.
 * - untracked normalizes accountId to null and produces zero transactions on create and pay.
 * - tracked=true + isPaid=true + recordInitial=true posts EXPENSE transaction to chosen account.
 * - missing "Installments" category rolls back atomically (zero installments, zero transactions).
 * - untracked creation succeeds even if "Installments" category is missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class InstallmentTrackedAccountingTest {
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
          name = "Melli",
          type = AccountType.BANK,
          initialBalance = 50_000_000L,
          displayOrder = 1
        ),
        AccountEntity(
          id = 3L,
          name = "Pasargad",
          type = AccountType.BANK,
          initialBalance = 10_000_000L,
          displayOrder = 2
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

  private suspend fun seedInstallmentsCategory(repo: HesabyarRepository) {
    repo.insertCategory(
      Category(
        id = 10L,
        name = "Installments",
        key = "Installments",
        icon = "CreditCard",
        color = 0xFF4CAF50L,
        type = CategoryType.EXPENSE,
        isDefault = true
      )
    )
  }

  private fun sampleInstallment(
    tracked: Boolean,
    accountId: Long?,
    isPaid: Boolean = false,
    amount: Long = 2_500_000L
  ) = Installment(
    title = "Appliance loan",
    amount = amount,
    dueDate = 1_700_000_000_000L,
    isPaid = isPaid,
    notes = "Monthly installment",
    tracked = tracked,
    accountId = accountId
  )

  @Test
  fun trackedInstallmentWithNullAccountIdThrowsIllegalArgumentException() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)

      var threw = false
      try {
        repo.insertInstallmentWithInitial(
          installment = sampleInstallment(tracked = true, accountId = null, isPaid = true),
          recordInitial = true
        )
      } catch (e: IllegalArgumentException) {
        threw = true
        assertTrue(e.message?.contains("Tracked mode requires an explicit, valid non-null accountId") == true)
      }
      assertTrue("must reject null accountId when tracked=true", threw)

      // Pre-validation assertion: zero writes to the database
      assertEquals("zero installments written", 0, database.installmentDao().getAllInstallmentsBlocking().size)
      assertEquals("zero transactions written", 0, database.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun untrackedInstallmentProducesZeroTransactionsOnCreateAndPay() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)

      val id =
        repo.insertInstallmentWithInitial(
          installment = sampleInstallment(tracked = false, accountId = 2L, isPaid = false),
          recordInitial = true
        )
      assertTrue("installment created", id > 0)

      val stored = database.installmentDao().getInstallmentById(id)!!
      assertFalse("must be untracked", stored.tracked)
      assertNull("accountId must be normalized to null", stored.accountId)

      // Zero transactions on creation
      assertEquals(
        "creation produces zero transactions",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      // Mark as paid: untracked installment must produce zero transactions
      repo.updateInstallment(stored.copy(isPaid = true))

      val updated = database.installmentDao().getInstallmentById(id)!!
      assertTrue("isPaid must flip to true", updated.isPaid)
      assertEquals(
        "paying untracked installment produces zero transactions",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun trackedInstallmentWithRecordInitialFalseProducesZeroTransactions() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)

      val id =
        repo.insertInstallmentWithInitial(
          installment = sampleInstallment(tracked = true, accountId = 2L, isPaid = true),
          recordInitial = false
        )
      assertTrue("installment created", id > 0)

      val stored = database.installmentDao().getInstallmentById(id)!!
      assertTrue(stored.tracked)
      assertEquals(2L, stored.accountId)
      assertTrue(stored.isPaid)

      assertEquals(
        "already-recorded installment produces zero initial transactions",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun trackedInstallmentWithRecordInitialTruePostsExpenseToChosenAccount() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)

      val id =
        repo.insertInstallmentWithInitial(
          installment = sampleInstallment(tracked = true, accountId = 2L, isPaid = true, amount = 3_000_000L),
          recordInitial = true
        )
      assertTrue("installment created", id > 0)

      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("must record exactly one expense transaction", 1, txs.size)
      val tx = txs.single()
      assertEquals(TransactionType.EXPENSE, tx.type)
      assertEquals(3_000_000L, tx.amount)
      assertEquals(2L, tx.accountId)
      assertEquals(id, tx.installmentId)
      assertEquals(10L, tx.categoryId)
    }

  @Test
  fun trackedInstallmentWithInitialLegRollsBackWhenInstallmentsCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Installments" category is seeded!

      var threw = false
      try {
        repo.insertInstallmentWithInitial(
          installment = sampleInstallment(tracked = true, accountId = 2L, isPaid = true),
          recordInitial = true
        )
      } catch (e: IllegalStateException) {
        threw = true
        assertTrue(e.message?.contains("Installments category is missing from database") == true)
      }
      assertTrue("must throw IllegalStateException when Installments category missing", threw)

      // ATOMIC ROLLBACK ASSERTIONS: zero installments, zero transactions
      assertEquals("zero installments after rollback", 0, database.installmentDao().getAllInstallmentsBlocking().size)
      assertEquals("zero transactions after rollback", 0, database.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun untrackedInstallmentSucceedsEvenIfInstallmentsCategoryMissing() =
    runTest {
      val repo = createRepository()
      // Notice: NO "Installments" category is seeded!

      val id =
        repo.insertInstallmentWithInitial(
          installment = sampleInstallment(tracked = false, accountId = null, isPaid = false),
          recordInitial = false
        )
      assertTrue("untracked installment succeeds without Installments category", id > 0)
      assertEquals(1, database.installmentDao().getAllInstallmentsBlocking().size)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
    }
}
