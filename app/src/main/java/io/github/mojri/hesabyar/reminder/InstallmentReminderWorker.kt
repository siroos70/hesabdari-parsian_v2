package io.github.mojri.hesabyar.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mojri.hesabyar.data.AppDatabase
import java.util.concurrent.TimeUnit

class InstallmentReminderWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val settingsManager = ReminderSettingsManager(applicationContext)
    val config = settingsManager.getConfig()

    if (!config.masterEnabled || !config.installmentReminderEnabled) {
      return Result.success()
    }

    val database = AppDatabase.getDatabase(applicationContext)
    val installments = database.installmentDao().getAllInstallmentsSync()

    val now = System.currentTimeMillis()
    val daysBeforeDue = config.reminderDaysBeforeDue

    for (installment in installments) {
      if (installment.isPaid) continue
      if (!installment.reminderEnabled) continue

      val daysUntilDue =
        TimeUnit.MILLISECONDS
          .toDays(
            installment.dueDate - now
          ).toInt()

      if (daysUntilDue in -7..daysBeforeDue) {
        NotificationHelper.showInstallmentReminder(
          context = applicationContext,
          installmentId = installment.id,
          title = installment.title,
          amount = installment.amount,
          daysUntilDue = daysUntilDue
        )
      }
    }

    return Result.success()
  }
}
