package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for MIGRATION_8_9 — tracked and accountId columns on
 * loans, bank_loans, and installments tables.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseMigration8to9Test {
  private fun createV8CoreTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS categories (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, key TEXT NOT NULL, " +
        "icon TEXT NOT NULL, color INTEGER NOT NULL, " +
        "type TEXT NOT NULL, isDefault INTEGER NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS accounts (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, type TEXT NOT NULL, bankName TEXT, " +
        "cardNumber TEXT, accountNumber TEXT, iban TEXT, " +
        "initialBalance INTEGER NOT NULL DEFAULT 0, " +
        "color INTEGER NOT NULL DEFAULT 4283215696, icon TEXT, " +
        "isArchived INTEGER NOT NULL DEFAULT 0, " +
        "displayOrder INTEGER NOT NULL DEFAULT 0, " +
        "createdAt INTEGER NOT NULL DEFAULT 0, " +
        "updatedAt INTEGER NOT NULL DEFAULT 0)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS persons (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "name TEXT NOT NULL, normalizedName TEXT NOT NULL, " +
        "phone TEXT, notes TEXT, createdAt INTEGER NOT NULL, " +
        "isArchived INTEGER NOT NULL DEFAULT 0)"
    )
    db.execSQL(
      "CREATE UNIQUE INDEX IF NOT EXISTS index_persons_normalizedName " +
        "ON persons (normalizedName)"
    )
  }

  private fun createV8LedgerTables(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS transactions (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "type TEXT NOT NULL, categoryId INTEGER NOT NULL, " +
        "amount INTEGER NOT NULL, description TEXT NOT NULL, " +
        "personName TEXT, date INTEGER NOT NULL, " +
        "dueDate INTEGER, installmentId INTEGER, " +
        "accountId INTEGER NOT NULL DEFAULT 1, " +
        "destinationAccountId INTEGER DEFAULT NULL, " +
        "personId INTEGER DEFAULT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS loans (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "personName TEXT NOT NULL, type TEXT NOT NULL, " +
        "originalAmount INTEGER NOT NULL, " +
        "remainingAmount INTEGER NOT NULL, " +
        "description TEXT NOT NULL, date INTEGER NOT NULL, " +
        "isSettled INTEGER NOT NULL, " +
        "personId INTEGER DEFAULT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS installments (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "title TEXT NOT NULL, amount INTEGER NOT NULL, " +
        "dueDate INTEGER NOT NULL, isPaid INTEGER NOT NULL, " +
        "reminderEnabled INTEGER NOT NULL, " +
        "notes TEXT NOT NULL, bankLoanId INTEGER)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS payment_history (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "loanId INTEGER NOT NULL, amount INTEGER NOT NULL, " +
        "date INTEGER NOT NULL, notes TEXT NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS bank_loans (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "bankName TEXT NOT NULL, loanName TEXT NOT NULL, " +
        "receivedAmount INTEGER NOT NULL, " +
        "monthlyInstallmentAmount INTEGER NOT NULL, " +
        "numberOfInstallments INTEGER NOT NULL, " +
        "totalRepayableAmount INTEGER NOT NULL, " +
        "totalInterest INTEGER NOT NULL, " +
        "startDate INTEGER NOT NULL, " +
        "description TEXT NOT NULL, " +
        "isSettled INTEGER NOT NULL)"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS room_master_table (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "identity_hash TEXT)"
    )
    db.execSQL(
      "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '')"
    )
  }

  private fun createAndSeedV8Database(
    context: Context,
    dbName: String,
    seed: (SupportSQLiteDatabase) -> Unit
  ) {
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
          .builder(context)
          .name(dbName)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                createV8CoreTables(db)
                createV8LedgerTables(db)
              }

              override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
              ) {}
            }
          ).build()
      )
    val raw = helper.writableDatabase
    seed(raw)
    raw.close()
    helper.close()
  }

  @Test
  fun migration8to9BackfillsDefaultTrackedAndNullAccountId() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_8_9_test_db"
    val dbFile = context.getDatabasePath(dbName)

    try {
      createAndSeedV8Database(context, dbName) { raw -> seedV8Rows(raw) }

      val migratedDb =
        Room
          .databaseBuilder(context, AppDatabase::class.java, dbName)
          .allowMainThreadQueries()
          .addMigrations(AppDatabase.MIGRATION_8_9)
          .build()

      assertPreExistingDefaults(migratedDb)
      assertInsertTrackedLoan(migratedDb)

      migratedDb.close()
    } finally {
      dbFile.delete()
      context.getDatabasePath("$dbName-wal").delete()
      context.getDatabasePath("$dbName-shm").delete()
    }
  }

  private fun seedV8Rows(raw: SupportSQLiteDatabase) {
    raw.execSQL(
      "INSERT INTO loans (personName, type, originalAmount, remainingAmount, " +
        "description, date, isSettled, personId) " +
        "VALUES ('علی', 'DEBTOR', 100000, 100000, 'قرض قدیمی', 1000, 0, NULL)"
    )
    raw.execSQL(
      "INSERT INTO bank_loans (bankName, loanName, receivedAmount, " +
        "monthlyInstallmentAmount, numberOfInstallments, totalRepayableAmount, " +
        "totalInterest, startDate, description, isSettled) " +
        "VALUES ('بانک ملی', 'وام مسکن', 50000000, 5000000, 12, 60000000, 10000000, 1000, 'توضیح وام', 0)"
    )
    raw.execSQL(
      "INSERT INTO installments (title, amount, dueDate, isPaid, reminderEnabled, notes, bankLoanId) " +
        "VALUES ('قسط اول', 5000000, 2000, 0, 1, 'یادداشت', NULL)"
    )
  }

  private fun assertPreExistingDefaults(migratedDb: AppDatabase) {
    val loans = migratedDb.loanDao().getAllLoansBlocking()
    assertEquals("One loan preserved", 1, loans.size)
    val loan = loans.single()
    assertFalse("Pre-existing loan tracked defaults to false", loan.tracked)
    assertNull("Pre-existing loan accountId defaults to null", loan.accountId)

    val bankLoans = migratedDb.bankLoanDao().getAllBankLoansBlocking()
    assertEquals("One bank loan preserved", 1, bankLoans.size)
    val bankLoan = bankLoans.single()
    assertFalse("Pre-existing bank loan tracked defaults to false", bankLoan.tracked)
    assertNull("Pre-existing bank loan accountId defaults to null", bankLoan.accountId)

    val installments = migratedDb.installmentDao().getAllInstallmentsBlocking()
    assertEquals("One installment preserved", 1, installments.size)
    val installment = installments.single()
    assertFalse("Pre-existing installment tracked defaults to false", installment.tracked)
    assertNull("Pre-existing installment accountId defaults to null", installment.accountId)
  }

  private fun assertInsertTrackedLoan(migratedDb: AppDatabase) {
    val newTrackedLoan =
      Loan(
        id = 2L,
        personName = "رضا",
        personId = null,
        type = LoanType.CREDITOR,
        originalAmount = 200000L,
        remainingAmount = 200000L,
        description = "وام رهگیری شده",
        date = 3000L,
        tracked = true,
        accountId = 1L
      )
    migratedDb.loanDao().insertAllBlocking(listOf(newTrackedLoan))
    val allLoans = migratedDb.loanDao().getAllLoansBlocking()
    val queriedLoan = allLoans.firstOrNull { it.id == 2L }
    assertEquals("Inserted tracked loan preserves tracked flag", true, queriedLoan?.tracked)
    assertEquals("Inserted tracked loan preserves accountId", 1L, queriedLoan?.accountId)
  }
}
