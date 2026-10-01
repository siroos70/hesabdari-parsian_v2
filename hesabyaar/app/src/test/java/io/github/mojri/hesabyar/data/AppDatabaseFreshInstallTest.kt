package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the fresh-install seeding callback registered in
 * [AppDatabase.getDatabase] ([AppDatabase.DEFAULT_ACCOUNT_SEED_CALLBACK]).
 *
 * A brand-new database (fresh install) starts directly at the latest schema
 * version and never runs migrations, so the onCreate callback is the only
 * path that seeds the default account for it. The migration-based seeding
 * path (MIGRATION_5_6 on upgrade from v5) is covered separately in
 * [AppDatabaseMigrationTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseFreshInstallTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.DEFAULT_ACCOUNT_SEED_CALLBACK)
        .build()
    // Force the first open so Room fires the onCreate callback (Room opens lazily).
    database.accountDao().getAllAccountsBlocking()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun freshInstallSeedsDefaultAccountRow() {
    val accounts = database.accountDao().getAllAccountsBlocking()
    assertEquals("Fresh install must have exactly 1 seeded account", 1, accounts.size)

    val seeded = accounts.first()
    // Assert against the constant (not literals) so a future divergence
    // between DEFAULT_ACCOUNT and the seed fails this test directly.
    val expected = AccountEntity.DEFAULT_ACCOUNT
    assertEquals("Seeded account id", expected.id, seeded.id)
    assertEquals("Seeded account name", expected.name, seeded.name)
    assertEquals("Seeded account type", expected.type, seeded.type)
    assertEquals("Seeded account initialBalance", expected.initialBalance, seeded.initialBalance)
    assertEquals("Seeded account displayOrder", expected.displayOrder, seeded.displayOrder)
    assertEquals("Seeded account createdAt", expected.createdAt, seeded.createdAt)
    assertEquals("Seeded account updatedAt", expected.updatedAt, seeded.updatedAt)
    // The seed now writes color explicitly from the constant
    assertEquals("Seeded account color matches the constant", expected.color, seeded.color)
  }
}
