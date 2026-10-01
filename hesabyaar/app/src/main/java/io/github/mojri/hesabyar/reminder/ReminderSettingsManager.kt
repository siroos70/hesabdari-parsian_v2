package io.github.mojri.hesabyar.reminder

import android.content.Context
import android.content.SharedPreferences

class ReminderSettingsManager(
  context: Context
) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  data class ReminderConfig(
    val masterEnabled: Boolean = true,
    val installmentReminderEnabled: Boolean = true,
    val loanReminderEnabled: Boolean = true,
    val reminderDaysBeforeDue: Int = 1,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val loanReminderDaysInterval: Int = 7
  )

  fun getConfig(): ReminderConfig =
    ReminderConfig(
      masterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true),
      installmentReminderEnabled = prefs.getBoolean(KEY_INSTALLMENT_ENABLED, true),
      loanReminderEnabled = prefs.getBoolean(KEY_LOAN_ENABLED, true),
      reminderDaysBeforeDue = prefs.getInt(KEY_DAYS_BEFORE_DUE, 1),
      reminderHour = prefs.getInt(KEY_REMINDER_HOUR, 8),
      reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0),
      loanReminderDaysInterval = prefs.getInt(KEY_LOAN_INTERVAL, 7)
    )

  fun saveConfig(config: ReminderConfig) {
    prefs
      .edit()
      .putBoolean(KEY_MASTER_ENABLED, config.masterEnabled)
      .putBoolean(KEY_INSTALLMENT_ENABLED, config.installmentReminderEnabled)
      .putBoolean(KEY_LOAN_ENABLED, config.loanReminderEnabled)
      .putInt(KEY_DAYS_BEFORE_DUE, config.reminderDaysBeforeDue)
      .putInt(KEY_REMINDER_HOUR, config.reminderHour)
      .putInt(KEY_REMINDER_MINUTE, config.reminderMinute)
      .putInt(KEY_LOAN_INTERVAL, config.loanReminderDaysInterval)
      .apply()
  }

  fun setMasterEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
  }

  fun setInstallmentReminderEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_INSTALLMENT_ENABLED, enabled).apply()
  }

  fun setLoanReminderEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_LOAN_ENABLED, enabled).apply()
  }

  fun setReminderDaysBeforeDue(days: Int) {
    prefs.edit().putInt(KEY_DAYS_BEFORE_DUE, days.coerceIn(1, 30)).apply()
  }

  fun setReminderTime(
    hour: Int,
    minute: Int
  ) {
    prefs
      .edit()
      .putInt(KEY_REMINDER_HOUR, hour.coerceIn(0, 23))
      .putInt(KEY_REMINDER_MINUTE, minute.coerceIn(0, 59))
      .apply()
  }

  fun setLoanReminderInterval(days: Int) {
    prefs.edit().putInt(KEY_LOAN_INTERVAL, days.coerceIn(1, 30)).apply()
  }

  companion object {
    private const val PREFS_NAME = "hesabyar_reminder_prefs"
    private const val KEY_MASTER_ENABLED = "master_enabled"
    private const val KEY_INSTALLMENT_ENABLED = "installment_enabled"
    private const val KEY_LOAN_ENABLED = "loan_enabled"
    private const val KEY_DAYS_BEFORE_DUE = "days_before_due"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"
    private const val KEY_LOAN_INTERVAL = "loan_interval"
  }
}
