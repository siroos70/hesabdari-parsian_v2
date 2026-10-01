package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-database coverage for [io.github.mojri.hesabyar.data.InstallmentDelegate]
 * .updateInstallment: the paid transition records exactly one linked expense,
 * toggling never double-counts money, and a missing Installments category
 * aborts the whole update (rollback leaves the row unchanged).
 *
 * Split from RepositoryLogicTest to stay under the detekt class-size threshold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class InstallmentUpdateTransactionTest {
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

  private suspend fun seedInstallmentsCategory(repo: HesabyarRepository) {
    repo.insertCategory(
      Category(
        name = "Installments",
        key = "Installments",
        icon = "CreditCard",
        color = 0xFF4CAF50L,
        type = CategoryType.EXPENSE
      )
    )
  }

  @Test
  fun updateinstallmentPaidCreatesExpenseTransaction() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 2_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = false,
            tracked = true,
            accountId = 1L
          )
        )
      val stored = database.installmentDao().getInstallmentById(installmentId)!!

      repo.updateInstallment(stored.copy(isPaid = true))

      val txs = database.transactionDao().getAllTransactionsBlocking()
      assertEquals("paid transition must record exactly one expense", 1, txs.size)
      val tx = txs.single()
      assertEquals(TransactionType.EXPENSE, tx.type)
      assertEquals(2_000_000L, tx.amount)
      assertEquals(installmentId, tx.installmentId)

      // Toggling paid → paid again must not insert a second expense.
      repo.updateInstallment(stored.copy(isPaid = true))
      assertEquals(
        "re-paying a paid installment must not double-count",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )

      // Toggling paid → unpaid reverses the expense; unpaid → paid re-records
      // exactly one fresh expense, so the cycle cannot double-count money.
      repo.updateInstallment(stored.copy(isPaid = false))
      assertEquals(
        "unpay must reverse the recorded expense",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
      repo.updateInstallment(stored.copy(isPaid = true))
      assertEquals(
        "re-pay after unpay must record exactly one expense",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun updateinstallmentWithoutCategoryThrowsAndRollsBack() =
    runTest {
      val repo = createRepository()
      // No "Installments" category seeded on purpose.
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 1_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = false,
            tracked = true,
            accountId = 1L
          )
        )
      val stored = database.installmentDao().getInstallmentById(installmentId)!!

      try {
        repo.updateInstallment(stored.copy(isPaid = true))
        fail("Missing Installments category must abort the paid transition")
      } catch (expected: IllegalStateException) {
      }

      // The whole update rolled back: the row keeps isPaid = false and no
      // expense was recorded.
      val unchanged = database.installmentDao().getInstallmentById(installmentId)!!
      assertFalse("rollback must keep the row unpaid", unchanged.isPaid)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun unpayWithoutCategoryThrowsAndRollsBackInsteadOfStrandingExpense() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 3_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = false,
            tracked = true,
            accountId = 1L
          )
        )
      val stored = database.installmentDao().getInstallmentById(installmentId)!!
      repo.updateInstallment(stored.copy(isPaid = true))
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      // A REPLACE-restore can leave the DB without an Installments category.
      database.categoryDao().deleteAllCategories()

      // The unpay must abort ATOMICALLY: before the fix, the row flipped to
      // unpaid while the expense stayed behind (double-counted money).
      try {
        repo.updateInstallment(stored.copy(isPaid = false))
        fail("unpay without Installments category must abort, not strand the expense")
      } catch (expected: IllegalStateException) {
      }

      val unchanged = database.installmentDao().getInstallmentById(installmentId)!!
      assertTrue("rollback must keep the row paid", unchanged.isPaid)
      assertEquals(
        "the expense row must survive the aborted unpay (it is still owed)",
        1,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun deletePaidInstallmentRemovesItsLinkedExpense() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 4_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = true,
            tracked = true,
            accountId = 1L
          )
        )
      // Going through updateInstallment keeps the isPaid flip and its expense
      // consistent, mirroring the real user flow.
      repo.updateInstallment(
        database.installmentDao().getInstallmentById(installmentId)!!.copy(isPaid = false)
      )
      repo.updateInstallment(
        database.installmentDao().getInstallmentById(installmentId)!!.copy(isPaid = true)
      )
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      repo.deleteInstallment(database.installmentDao().getInstallmentById(installmentId)!!)

      assertEquals("installment gone", 0, database.installmentDao().getAllInstallmentsSync().size)
      assertEquals(
        "linked expense must die with the paid installment",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun deleteUnpaidInstallmentLeavesTransactionsAlone() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 5_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = false,
            tracked = true,
            accountId = 1L
          )
        )

      repo.deleteInstallment(database.installmentDao().getInstallmentById(installmentId)!!)

      assertEquals(0, database.installmentDao().getAllInstallmentsSync().size)
      assertEquals(0, database.transactionDao().getAllTransactionsBlocking().size)
    }

  @Test
  fun deletePaidInstallmentWithoutCategoryStillRemovesExpense() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 6_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = false,
            tracked = true,
            accountId = 1L
          )
        )
      val stored = database.installmentDao().getInstallmentById(installmentId)!!
      repo.updateInstallment(stored.copy(isPaid = true))
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      // Deleting a paid installment must still clean up its expense even when
      // the Installments category is gone (delete uses installmentId alone).
      database.categoryDao().deleteAllCategories()

      repo.deleteInstallment(database.installmentDao().getInstallmentById(installmentId)!!)

      assertEquals(0, database.installmentDao().getAllInstallmentsSync().size)
      assertEquals(
        "expense cleanup must not depend on the Installments category",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun deleteInstallmentForAlreadyDeletedRowStillCleansLinkedExpense() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 8_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = true,
            bankLoanId = 55L,
            tracked = true,
            accountId = 1L
          )
        )
      repo.updateInstallment(
        database.installmentDao().getInstallmentById(installmentId)!!.copy(isPaid = false)
      )
      repo.updateInstallment(
        database.installmentDao().getInstallmentById(installmentId)!!.copy(isPaid = true)
      )
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      // The row is already gone (deleted through another path, e.g. the
      // bank-loan cascade or a REPLACE-restore); a queued or stale delete
      // must still clean the linked expense behind the dead id.
      database.installmentDao().deleteInstallment(
        database.installmentDao().getInstallmentById(installmentId)!!
      )

      repo.deleteInstallment(
        Installment(
          title = "Car",
          amount = 8_000_000L,
          dueDate = 1_700_000_000_000L,
          isPaid = true,
          id = installmentId,
          bankLoanId = 55L,
          tracked = true,
          accountId = 1L
        )
      )

      assertEquals(
        "the orphaned expense must be cleaned even though the row is already gone",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun deleteInstallmentWithStaleUnpaidSnapshotStillRemovesExpense() =
    runTest {
      val repo = createRepository()
      seedInstallmentsCategory(repo)
      val installmentId =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 7_000_000L,
            dueDate = 1_700_000_000_000L,
            isPaid = false,
            tracked = true,
            accountId = 1L
          )
        )
      val stale = database.installmentDao().getInstallmentById(installmentId)!!
      // The row becomes paid AFTER the caller captured its unpaid snapshot —
      // the delete must decide from the persisted state, not the stale object.
      repo.updateInstallment(stale.copy(isPaid = true))
      assertEquals(1, database.transactionDao().getAllTransactionsBlocking().size)

      repo.deleteInstallment(stale)

      assertEquals(
        "stale snapshot must still delete the row",
        0,
        database.installmentDao().getAllInstallmentsSync().size
      )
      assertEquals(
        "the paid row's linked expense must die with it despite the stale unpaid snapshot",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }

  @Test
  fun insertInstallmentWithTrackedTrueAndNullAccountIdThrows() =
    runTest {
      val repo = createRepository()
      try {
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 1_000_000L,
            dueDate = 1_700_000_000_000L,
            tracked = true,
            accountId = null
          )
        )
        fail("insertInstallment with tracked=true and null accountId must throw")
      } catch (expected: IllegalArgumentException) {
        // Expected
      }
    }

  @Test
  fun insertInstallmentWithTrackedFalseNormalizesAccountIdToNull() =
    runTest {
      val repo = createRepository()
      val id =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 1_000_000L,
            dueDate = 1_700_000_000_000L,
            tracked = false,
            accountId = 5L
          )
        )
      val stored = database.installmentDao().getInstallmentById(id)!!
      assertFalse(stored.tracked)
      assertEquals(null, stored.accountId)
    }

  @Test
  fun updateInstallmentWithoutPaidFlipDoesNotRequireInstallmentsCategory() =
    runTest {
      val repo = createRepository()
      val id =
        repo.insertInstallment(
          Installment(
            title = "Car",
            amount = 1_000_000L,
            dueDate = 1_700_000_000_000L,
            tracked = false
          )
        )
      val stored = database.installmentDao().getInstallmentById(id)!!
      // Ensure categories table is empty: updating title or notes without flipping isPaid
      // must succeed without querying or needing the "Installments" category.
      database.categoryDao().deleteAllCategories()
      repo.updateInstallment(stored.copy(notes = "updated note"))
      val updated = database.installmentDao().getInstallmentById(id)!!
      assertEquals("updated note", updated.notes)
    }
}
