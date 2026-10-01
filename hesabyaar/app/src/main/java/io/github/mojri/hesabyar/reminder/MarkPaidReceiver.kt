package io.github.mojri.hesabyar.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.HesabyarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarkPaidReceiver : BroadcastReceiver() {
  @Suppress("TooGenericExceptionCaught") // CancellationException is rethrown first for structured cancellation
  override fun onReceive(
    context: Context,
    intent: Intent
  ) {
    val installmentId = intent.getLongExtra(EXTRA_INSTALLMENT_ID, -1)
    if (installmentId == -1L) return

    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val appContext = context.applicationContext
        val database = AppDatabase.getDatabase(appContext)
        // Route the paid-mark through the repository so the ledger delegate
        // posts the tracked installment expense exactly like the in-app
        // toggle does. A raw DAO update bypassed that and left tracked rows
        // marked paid with no transaction behind them.
        val repository =
          HesabyarRepository(
            transactionDao = database.transactionDao(),
            loanDao = database.loanDao(),
            installmentDao = database.installmentDao(),
            paymentHistoryDao = database.paymentHistoryDao(),
            categoryDao = database.categoryDao(),
            bankLoanDao = database.bankLoanDao(),
            accountDao = database.accountDao(),
            personDao = database.personDao(),
            database = database
          )
        val installment =
          database
            .installmentDao()
            .getAllInstallmentsSync()
            .firstOrNull { it.id == installmentId }
        if (installment != null && !installment.isPaid) {
          repository.updateInstallment(installment.copy(isPaid = true))
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(installmentId.toInt())
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLogger.e("MarkPaidReceiver", "Failed to mark installment $installmentId as paid", e)
      } finally {
        pendingResult?.finish()
      }
    }
  }

  companion object {
    const val ACTION_MARK_PAID = "io.github.mojri.hesabyar.ACTION_MARK_PAID"
    const val EXTRA_INSTALLMENT_ID = "installment_id"

    fun createIntent(
      context: Context,
      installmentId: Long
    ): Intent =
      Intent(context, MarkPaidReceiver::class.java).apply {
        action = ACTION_MARK_PAID
        putExtra(EXTRA_INSTALLMENT_ID, installmentId)
      }
  }
}
