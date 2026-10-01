package io.github.mojri.hesabyar.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MarkPaidReceiverTest {
  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var notificationManager: NotificationManager
  private lateinit var shadowNotificationManager: ShadowNotificationManager
  private val receiver = MarkPaidReceiver()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    AppDatabase.setDatabaseForTesting(database)
    database.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
    database.categoryDao().insertAllBlocking(
      listOf(
        Category(
          id = 1L,
          name = "Installments",
          key = "Installments",
          icon = "CreditCard",
          color = 0xFF4CAF50L,
          type = CategoryType.EXPENSE
        )
      )
    )
    notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    shadowNotificationManager = Shadows.shadowOf(notificationManager)
  }

  @After
  fun tearDown() {
    AppDatabase.setDatabaseForTesting(null)
    database.close()
  }

  private fun awaitCondition(
    timeoutMs: Long = 3000,
    condition: () -> Boolean
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (condition()) return
      Thread.sleep(20)
    }
    assertTrue("Condition not met within ${timeoutMs}ms", condition())
  }

  @Test
  fun markPaidReceiverUpdatesTrackedInstallmentAndCancelsNotification() {
    val installmentId = 1L
    database.installmentDao().insertAllBlocking(
      listOf(
        Installment(
          id = installmentId,
          title = "Insurance",
          amount = 1_500_000L,
          dueDate = System.currentTimeMillis(),
          isPaid = false,
          tracked = true,
          accountId = 1L
        )
      )
    )

    val notification = Notification.Builder(context, "test_channel").build()
    notificationManager.notify(installmentId.toInt(), notification)
    assertEquals(1, shadowNotificationManager.allNotifications.size)

    val intent = MarkPaidReceiver.createIntent(context, installmentId)
    receiver.onReceive(context, intent)

    awaitCondition {
      val isPaid =
        database
          .installmentDao()
          .getAllInstallmentsBlocking()
          .firstOrNull { it.id == installmentId }
          ?.isPaid == true
      val notificationDismissed = shadowNotificationManager.allNotifications.isEmpty()
      isPaid && notificationDismissed
    }

    val updated = database.installmentDao().getAllInstallmentsBlocking().first { it.id == installmentId }
    assertTrue(updated.isPaid)
    assertEquals(0, shadowNotificationManager.allNotifications.size)

    val transactions = database.transactionDao().getAllTransactionsBlocking()
    assertEquals("tracked installment should record one transaction", 1, transactions.size)
    val tx = transactions.first()
    assertEquals(TransactionType.EXPENSE, tx.type)
    assertEquals(1_500_000L, tx.amount)
    assertEquals(installmentId, tx.installmentId)
    assertEquals(1L, tx.accountId)
  }

  @Test
  fun markPaidReceiverIgnoresAlreadyPaidInstallment() {
    val installmentId = 2L
    database.installmentDao().insertAllBlocking(
      listOf(
        Installment(
          id = installmentId,
          title = "Gym",
          amount = 500_000L,
          dueDate = System.currentTimeMillis(),
          isPaid = true,
          tracked = true,
          accountId = 1L
        )
      )
    )

    val notification = Notification.Builder(context, "test_channel").build()
    notificationManager.notify(installmentId.toInt(), notification)
    assertEquals(1, shadowNotificationManager.allNotifications.size)

    val intent = MarkPaidReceiver.createIntent(context, installmentId)
    receiver.onReceive(context, intent)

    awaitCondition {
      shadowNotificationManager.allNotifications.isEmpty()
    }
    val transactions = database.transactionDao().getAllTransactionsBlocking()
    assertEquals("already paid installment must not generate transactions", 0, transactions.size)
  }

  @Test
  fun markPaidReceiverIgnoresInvalidInstallmentId() {
    val dummyNotificationId = 777
    val notification = Notification.Builder(context, "test_channel").build()
    notificationManager.notify(dummyNotificationId, notification)
    assertEquals(1, shadowNotificationManager.allNotifications.size)

    val intent = MarkPaidReceiver.createIntent(context, -1L)
    receiver.onReceive(context, intent)

    assertEquals(
      "invalid ID must not dismiss existing notifications",
      1,
      shadowNotificationManager.allNotifications.size
    )
    val transactions = database.transactionDao().getAllTransactionsBlocking()
    assertEquals("invalid ID must not record any transactions", 0, transactions.size)
  }

  @Test
  fun markPaidReceiverHandlesDatabaseFailureGracefully() {
    val installmentId = 999L
    val notification = Notification.Builder(context, "test_channel").build()
    notificationManager.notify(installmentId.toInt(), notification)
    database.close() // force exception on DB access

    val intent = MarkPaidReceiver.createIntent(context, installmentId)
    // Receiver must catch the exception internally without crashing the caller
    receiver.onReceive(context, intent)
    Thread.sleep(100)
    assertEquals(
      "failed DB operation must not cancel notification",
      1,
      shadowNotificationManager.allNotifications.size
    )
  }
}
