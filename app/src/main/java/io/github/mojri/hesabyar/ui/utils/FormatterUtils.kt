package io.github.mojri.hesabyar.ui.utils

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import java.util.*

private const val LINE_PREVIEW_MAX_LENGTH = 60
private const val PREVIEW_MAX_LENGTH = 150

internal fun formatPersianDate(timestamp: Long): String {
  val jalali = JalaliCalendarHelper.gregorianToJalali(timestamp)
  val cal = Calendar.getInstance()
  cal.timeInMillis = timestamp
  val hour = cal.get(Calendar.HOUR_OF_DAY)
  val minute = cal.get(Calendar.MINUTE)
  return String.format(Locale.US, "%s - %02d:%02d", jalali.toString(), hour, minute)
}

internal fun extractForecastPreview(forecast: String): String {
  val lines = forecast.lines()
  val contentLines =
    lines
      .filter { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
      }.map { line ->
        line
          .trim()
          .removePrefix("-")
          .removePrefix("*")
          .trim()
      }.filter { it.isNotEmpty() }

  if (contentLines.isEmpty()) return "گزارش آماده است"

  val preview =
    contentLines.take(3).joinToString(" | ") { line ->
      truncateWithEllipsis(line, LINE_PREVIEW_MAX_LENGTH)
    }

  return truncateWithEllipsis(preview, PREVIEW_MAX_LENGTH)
}

private fun truncateWithEllipsis(
  text: String,
  maxLength: Int
): String =
  if (text.length > maxLength) {
    text.substring(0, maxLength).substringBeforeLast(" ") + "..."
  } else {
    text
  }
