package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Merge-restore guard coverage split from [RepositoryBackupRestoreTest] to
 * stay under the detekt class-size threshold: the null-id unlink guarantee on
 * modern payloads, the isDefault protection on category merge, and the
 * installment bankLoanId skip-with-warning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RepositoryMergeGuardsTest {
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

  private fun transaction(
    id: Long,
    amount: Long
  ) = io.github.mojri.hesabyar.data.Transaction(
    id = id,
    type = TransactionType.EXPENSE,
    categoryId = 0L,
    amount = amount,
    description = "tx",
    personName = null,
    date = System.currentTimeMillis(),
    accountId = 1L
  )

  @Test
  fun mergeFromBackupKeepsNameOnlyRowsUnlinkedOnModernPayloads() =
    runTest {
      val repo = createRepository()
      // Modern payload: persons carries Ali; the transaction is name-only with
      // personId == null — the deliberate unlinked state deletePerson leaves
      // behind. Merge must NOT link it to a same-named person by name lookup
      // (that would undo the user's unlink decision), even though the local
      // DB has a matching person.
      repo.replaceAllFromBackup(
        BackupPayload(
          persons =
            listOf(
              Person(id = 1L, name = "علی", normalizedName = "علی", createdAt = 1L)
            )
        )
      )
      val ali = requireNotNull(database.personDao().getPersonByNormalizedName("علی"))

      repo.mergeFromBackup(
        BackupPayload(
          persons =
            listOf(
              Person(id = 1L, name = "علی", normalizedName = "علی", createdAt = 1L)
            ),
          transactions =
            listOf(
              transaction(id = 10L, amount = 500L).copy(personName = "علی", personId = null)
            )
        )
      )

      val merged = database.transactionDao().getAllTransactionsBlocking().single()
      assertNull("name-only row stays unlinked on modern payloads", merged.personId)
      assertEquals("علی", merged.personName)
      // The local person row itself is untouched by the name-only row.
      assertEquals(ali.id, database.personDao().getPersonByNormalizedName("علی")?.id)
    }

  @Test
  fun mergeFromBackupKeepsPersistedIsDefaultOnProtectedCategories() =
    runTest {
      val repo = createRepository()
      // Seed Food as the live default category, then merge a backup whose
      // Food row carries isDefault = false. The raw DAO update must not flip
      // the persisted flag — otherwise the delete guard on default categories
      // becomes bypassable (flip to false, then delete).
      database.categoryDao().insertCategory(
        Category(
          name = "خوراک",
          key = "Food",
          icon = "Restaurant",
          color = 1,
          type = CategoryType.EXPENSE,
          isDefault = true
        )
      )
      val foodId = requireNotNull(database.categoryDao().getCategoryByKey("Food")).id

      repo.mergeFromBackup(
        BackupPayload(
          categories =
            listOf(
              Category(
                id = 90L,
                name = "خوراک",
                key = "Food",
                icon = "Restaurant",
                color = 2,
                type = CategoryType.EXPENSE,
                isDefault = false
              )
            )
        )
      )

      val merged = requireNotNull(database.categoryDao().getCategoryById(foodId))
      assertTrue("merge must not flip the persisted isDefault flag", merged.isDefault)
      assertEquals("non-default fields still update from the backup", 2, merged.color)
    }

  @Test
  fun mergeFromBackupSkipsInstallmentsWhoseBankLoanIdDoesNotMap() =
    runTest {
      val repo = createRepository()
      // Installments whose bankLoanId has no merged bank loan must be
      // skipped with a warning (mirroring backupMergePaymentHistories), not
      // silently inserted with a dropped FK.
      repo.mergeFromBackup(
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 1L,
                title = "قسط جاری",
                amount = 1_000L,
                dueDate = 100L,
                bankLoanId = 5L
              ),
              Installment(
                id = 2L,
                title = "قسط بدون بانک",
                amount = 2_000L,
                dueDate = 200L,
                bankLoanId = null
              )
            )
        )
      )

      val stored = database.installmentDao().getAllInstallmentsSync()
      assertEquals("unmapped bankLoanId must be skipped", 1, stored.size)
      assertEquals("null bankLoanId inserts normally", "قسط بدون بانک", stored.single().title)
      assertNull("no FK may be silently fabricated", stored.single().bankLoanId)
    }

  @Test
  fun mergeFromBackupSkipsTransactionsReferencingSkippedInstallments() =
    runTest {
      val repo = createRepository()
      // An installment skipped for an unmapped bankLoanId must NOT leave its
      // dependent transactions imported as unlinked records (silently losing
      // the payment linkage). The dependent transaction must be skipped too.
      repo.mergeFromBackup(
        BackupPayload(
          installments =
            listOf(
              Installment(
                id = 100L,
                title = "قسط با بانک ناموجود",
                amount = 1_000L,
                dueDate = 100L,
                bankLoanId = 999L // unmapped bank loan
              )
            ),
          transactions =
            listOf(
              transaction(id = 50L, amount = 1_000L).copy(installmentId = 100L)
            )
        )
      )

      assertEquals(
        "transaction referencing skipped installment must be skipped too",
        0,
        database.transactionDao().getAllTransactionsBlocking().size
      )
    }
}
