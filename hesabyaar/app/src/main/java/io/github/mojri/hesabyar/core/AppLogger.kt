package io.github.mojri.hesabyar.core

import android.util.Log
import io.github.mojri.hesabyar.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
  private val logs = ArrayDeque<LogEntry>()
  private const val MAX_LOGS = 200

  data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: String,
    val message: String
  ) {
    fun formatted(): String {
      val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
      return "${sdf.format(Date(timestamp))} [$level/$tag] $message"
    }
  }

  fun d(
    tag: String,
    message: String
  ) {
    if (BuildConfig.DEBUG) Log.d(tag, message)
    addLog(tag, "D", message)
  }

  fun i(
    tag: String,
    message: String
  ) {
    if (BuildConfig.DEBUG) Log.i(tag, message)
    addLog(tag, "I", message)
  }

  fun w(
    tag: String,
    message: String
  ) {
    if (BuildConfig.DEBUG) Log.w(tag, message)
    addLog(tag, "W", message)
  }

  fun e(
    tag: String,
    message: String,
    throwable: Throwable? = null
  ) {
    Log.e(tag, message, throwable)
    val fullMessage = if (throwable != null) "$message: ${throwable.message}" else message
    addLog(tag, "E", fullMessage)
  }

  @Synchronized
  private fun addLog(
    tag: String,
    level: String,
    message: String
  ) {
    if (!BuildConfig.DEBUG) return
    logs.addLast(LogEntry(tag = tag, level = level, message = message))
    while (logs.size > MAX_LOGS) {
      logs.removeFirst()
    }
  }

  @Synchronized
  fun getLogs(): List<LogEntry> = logs.toList()

  @Synchronized
  fun getLogsForTag(tag: String): List<LogEntry> = logs.filter { it.tag == tag }

  @Synchronized
  fun getAiLogs(): List<LogEntry> =
    logs.filter {
      it.tag in listOf("AiConfigManager", "GeminiParser", "BudgetAdvisor", "AiProvider", "AiAssistantViewModel")
    }

  @Synchronized
  fun clear() {
    logs.clear()
  }
}
