package io.github.mojri.hesabyar.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {
  private const val WORK_INSTALLMENT = "installment_reminder_work"
  private const val WORK_LOAN = "loan_reminder_work"

  fun scheduleReminders(context: Context) {
    val settingsManager = ReminderSettingsManager(context)
    val config = settingsManager.getConfig()

    NotificationHelper.createNotificationChannels(context)

    cancelAllReminders(context)

    if (!config.masterEnabled) return

    if (config.installmentReminderEnabled) {
      scheduleInstallmentReminder(context, config)
    }

    if (config.loanReminderEnabled) {
      scheduleLoanReminder(context, config)
    }
  }

  private fun scheduleInstallmentReminder(
    context: Context,
    config: ReminderSettingsManager.ReminderConfig
  ) {
    val initialDelay = calculateInitialDelay(config.reminderHour, config.reminderMinute)

    val request =
      PeriodicWorkRequestBuilder<InstallmentReminderWorker>(
        1,
        TimeUnit.DAYS
      ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .setConstraints(
          Constraints
            .Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      WORK_INSTALLMENT,
      ExistingPeriodicWorkPolicy.UPDATE,
      request
    )
  }

  private fun scheduleLoanReminder(
    context: Context,
    config: ReminderSettingsManager.ReminderConfig
  ) {
    val initialDelay = calculateInitialDelay(config.reminderHour, config.reminderMinute)

    val request =
      PeriodicWorkRequestBuilder<LoanReminderWorker>(
        config.loanReminderDaysInterval.toLong(),
        TimeUnit.DAYS
      ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .setConstraints(
          Constraints
            .Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      WORK_LOAN,
      ExistingPeriodicWorkPolicy.UPDATE,
      request
    )
  }

  fun cancelAllReminders(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK_INSTALLMENT)
    WorkManager.getInstance(context).cancelUniqueWork(WORK_LOAN)
  }

  fun refreshReminders(context: Context) {
    cancelAllReminders(context)
    scheduleReminders(context)
  }

  private fun calculateInitialDelay(
    hour: Int,
    minute: Int
  ): Long {
    val now = Calendar.getInstance()
    val target =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }

    if (target.before(now)) {
      target.add(Calendar.DAY_OF_MONTH, 1)
    }

    return target.timeInMillis - now.timeInMillis
  }
}
