package io.github.mojri.hesabyar.domain.usecase

import android.content.SharedPreferences
import io.github.mojri.hesabyar.core.AppLogger

class GetSettingsUseCase(
  private val sharedPrefs: SharedPreferences
) {
  fun isDarkMode(): Boolean = sharedPrefs.getBoolean("dark_mode", true)

  fun setDarkMode(enabled: Boolean) {
    sharedPrefs.edit().putBoolean("dark_mode", enabled).apply()
  }

  fun getCurrencyUnit(): String = sharedPrefs.getString("currency_unit", "تومان") ?: "تومان"

  fun setCurrencyUnit(unit: String) {
    sharedPrefs.edit().putString("currency_unit", unit).apply()
  }

  fun getAiLogs(): List<AppLogger.LogEntry> = AppLogger.getAiLogs()

  fun clearLogs() = AppLogger.clear()
}
