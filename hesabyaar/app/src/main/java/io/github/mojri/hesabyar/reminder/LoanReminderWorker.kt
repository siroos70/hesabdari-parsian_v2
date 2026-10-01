package io.github.mojri.hesabyar.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mojri.hesabyar.data.AppDatabase

class LoanReminderWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val settingsManager = ReminderSettingsManager(applicationContext)
    val config = settingsManager.getConfig()

    if (!config.masterEnabled || !config.loanReminderEnabled) {
      return Result.success()
    }

    val database = AppDatabase.getDatabase(applicationContext)
    val loans = database.loanDao().getAllLoansSync()

    for (loan in loans) {
      if (loan.isSettled) continue
      if (loan.remainingAmount <= 0) continue

      NotificationHelper.showLoanReminder(
        context = applicationContext,
        loanId = loan.id,
        personName = loan.personName,
        remainingAmount = loan.remainingAmount,
        loanType = loan.type
      )
    }

    return Result.success()
  }
}
